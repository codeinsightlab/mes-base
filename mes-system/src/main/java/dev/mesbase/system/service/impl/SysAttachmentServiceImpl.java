package dev.mesbase.system.service.impl;

import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.persistence.CommitActions;
import dev.mesbase.platform.management.FileStorage;
import dev.mesbase.system.domain.SysAttachment;
import dev.mesbase.system.mapper.SysAttachmentMapper;
import dev.mesbase.system.mapper.SysNoticeMapper;
import dev.mesbase.system.service.SystemPermissionService;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOURCE_PRODUCT_PORT: attachment metadata, now private owner + scope + resource.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysAttachmentServiceImpl {
    @Autowired
    private SystemPermissionService permissions;
    @Autowired
    private SysNoticeMapper notices;
    private final CommitActions effects;
    private final SysAttachmentMapper mapper;
    private final FileStorage storage;
    private final long maxBytes;

    public SysAttachmentServiceImpl(SysAttachmentMapper mapper, FileStorage storage, Environment env,
                                    CommitActions effects) {
        this.effects = effects;
        this.mapper = mapper;
        this.storage = storage;
        maxBytes = env.getProperty("mes.platform.max-upload-bytes", Long.class, 10000000L);
    }

    private String factory(FactoryScope scope) {
        return scope == null ? null : scope.factoryId().value();
    }

    private void policy(String resource) {
        if (!Set.of("personal", "avatar", "notice-media").contains(resource))
            throw new FoundationException(ErrorCode.UNPROCESSABLE);
    }

    public List<SysAttachment> list(String resource, FactoryScope scope) {
        policy(resource);
        var query = new SysAttachment();
        query.setResource(resource);
        query.setOwnerId(SecurityUtils.getUserId());
        query.setFactoryId(factory(scope));
        return mapper.selectSysAttachmentList(query);
    }

    public SysAttachment metadata(String id, String resource, FactoryScope scope) {
        policy(resource);
        var row = mapper.findByFileId(id);
        if (row == null || !Objects.equals(row.getFactoryId(), factory(scope)) || !resource.equals(row.getResource()))
            throw new FoundationException(ErrorCode.NOT_FOUND);
        boolean owned = row.getOwnerId().equals(SecurityUtils.getUserId());
        boolean notice = "notice-media".equals(resource) && scope == null && row.getSourceDocId() != null && notices.selectNoticeById(row.getSourceDocId()) != null && permissions.granted(SecurityUtils.getUsername(), null, "system:notice:query");
        if (!owned && !notice) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public SysAttachment upload(String resource, String name, String contentType, InputStream input,
                                FactoryScope scope) throws IOException {
        policy(resource);
        if ("notice-media".equals(resource) && (scope != null || !canEditNotice()))
            throw new FoundationException(ErrorCode.FORBIDDEN);
        var checked = new PushbackInputStream(input, 24);
        byte[] header = checked.readNBytes(24);
        checked.unread(header);
        input = checked;
        contentType = sniff(header);
        if (("avatar".equals(resource) || "notice-media".equals(resource)) && !(contentType.startsWith("image/") || "notice-media".equals(resource) && contentType.equals("video/mp4")))
            throw new FoundationException(ErrorCode.UNPROCESSABLE);
        String clean = name == null ? "attachment" : name.replace('\\', '/');
        clean = clean.substring(clean.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\p{Cntrl}]", "");
        if (clean.isBlank() || clean.length() > 160) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var row = new SysAttachment();
        String id = UUID.randomUUID().toString();
        row.setFileId(id);
        row.setFileName(clean);
        row.setOrignalName(clean);
        row.setOwnerId(SecurityUtils.getUserId());
        row.setFactoryId(factory(scope));
        row.setResource(resource);
        row.setSourceDocType(resource);
        row.setFileUrl("/api/platform/files/" + id + "/content?resource=" + resource);
        row.setFileType(contentType == null ? "application/octet-stream" : contentType);
        row.setCreateBy(SecurityUtils.getUsername());
        row.setCreateTime(new Date());
        long size;
        try (var stream = input) {
            size = storage.write(id, stream, maxBytes);
        }
        row.setFileSize(BigDecimal.valueOf(size));
        effects.afterRollback(() -> {
            try {
                storage.delete(id);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            mapper.insertSysAttachment(row);
        } catch (RuntimeException failure) {
            try {
                storage.delete(id);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        return row;
    }

    public byte[] download(String id, String resource, FactoryScope scope) throws IOException {
        var row = metadata(id, resource, scope);
        return storage.read(id, row.getFileSize().longValueExact());
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int delete(String id, String resource, FactoryScope scope) throws IOException {
        var row = metadata(id, resource, scope);
        if (!row.getOwnerId()
                .equals(SecurityUtils.getUserId()) || "notice-media".equals(resource) && row.getSourceDocId() != null)
            throw new FoundationException(ErrorCode.FORBIDDEN);
        int deleted = mapper.deleteSysAttachmentByAttachmentId(row.getAttachmentId());
        effects.afterCommit(() -> {
            try {
                storage.delete(id);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return deleted;
    }

    private boolean canEditNotice() {
        String user = SecurityUtils.getUsername();
        return permissions.granted(user, null, "system:notice:add") || permissions.granted(user, null, "system:notice:edit");
    }

    private static String sniff(byte[] b) {
        if (b.length >= 8 && b[0] == (byte) 137 && b[1] == 80 && b[2] == 78 && b[3] == 71) return "image/png";
        if (b.length >= 3 && b[0] == (byte) 255 && b[1] == (byte) 216 && b[2] == (byte) 255) return "image/jpeg";
        String s = new String(b, StandardCharsets.ISO_8859_1);
        if (s.startsWith("GIF87a") || s.startsWith("GIF89a")) return "image/gif";
        if (s.startsWith("RIFF") && s.length() >= 12 && s.substring(8, 12).equals("WEBP")) return "image/webp";
        if (s.length() >= 12 && s.substring(4, 8).equals("ftyp")) return "video/mp4";
        return "application/octet-stream";
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public void bindNotice(Long notice, String html) {
        if (!canEditNotice()) throw new FoundationException(ErrorCode.FORBIDDEN);
        var doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        for (var media : doc.select("img[data-file-id],video[data-file-id]")) {
            String id = media.attr("data-file-id");
            var row = mapper.findByFileId(id);
            if (row == null || !"notice-media".equals(row.getResource()) || row.getFactoryId() != null || row.getSourceDocId() != null && !row.getSourceDocId()
                    .equals(notice) || row.getSourceDocId() == null && !row.getOwnerId()
                    .equals(SecurityUtils.getUserId())) throw new FoundationException(ErrorCode.NOT_FOUND);
            mapper.bindNotice(notice, id);
        }
    }
}
