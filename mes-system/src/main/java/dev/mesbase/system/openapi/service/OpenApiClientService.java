package dev.mesbase.system.openapi.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.openapi.OpenApiError;
import dev.mesbase.common.openapi.OpenApiException;
import dev.mesbase.common.openapi.OpenApiPrincipal;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.system.openapi.domain.OpenApiClient;
import dev.mesbase.system.openapi.domain.OpenApiClientApi;
import dev.mesbase.system.openapi.domain.OpenApiClientStatus;
import dev.mesbase.system.openapi.domain.OpenApiCredentialChange;
import dev.mesbase.system.openapi.dto.OpenApiAuthorizationQuery;
import dev.mesbase.system.openapi.dto.OpenApiClientApisRequest;
import dev.mesbase.system.openapi.dto.OpenApiClientCreateRequest;
import dev.mesbase.system.openapi.dto.OpenApiClientIdRequest;
import dev.mesbase.system.openapi.dto.OpenApiClientListRequest;
import dev.mesbase.system.openapi.dto.OpenApiClientStatusRequest;
import dev.mesbase.system.openapi.dto.OpenApiCredentialRequest;
import dev.mesbase.system.openapi.dto.OpenApiTokenCheck;
import dev.mesbase.system.openapi.mapper.OpenApiClientApiMapper;
import dev.mesbase.system.openapi.mapper.OpenApiClientExtraMapper;
import dev.mesbase.system.openapi.mapper.OpenApiClientMapper;
import dev.mesbase.system.openapi.vo.OpenApiClientPageVo;
import dev.mesbase.system.openapi.vo.OpenApiClientVo;
import dev.mesbase.system.openapi.vo.OpenApiSecretVo;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name={"mes.system.enabled","open-api.enabled"},havingValue="true")
public class OpenApiClientService {
    private final OpenApiClientMapper clients;
    private final OpenApiClientExtraMapper extra;
    private final OpenApiClientApiMapper grants;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public OpenApiClientService(OpenApiClientMapper clients, OpenApiClientExtraMapper extra,
                                OpenApiClientApiMapper grants, PasswordEncoder encoder) {
        this.clients = clients; this.extra = extra; this.grants = grants; this.encoder = encoder;
    }

    @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)

    public OpenApiSecretVo create(OpenApiClientCreateRequest request) {
        OpenApiClient entity = new OpenApiClient();
        String secret = randomValue(32);
        entity.setAppKey("mes_" + randomValue(24));
        entity.setSecretHash(encoder.encode(secret));
        entity.setClientName(request.getClientName());
        entity.setRemark(request.getRemark());
        entity.setStatus(OpenApiClientStatus.DISABLED.getCode());
        entity.setCredentialVersion(1L);
        entity.setCreateBy(SecurityUtils.getUsername());
        entity.setCreateTime(new Date());
        requireUpdated(clients.insert(entity));
        return secretVo(entity, secret);
    }

    public OpenApiClientPageVo list(OpenApiClientListRequest request) {
        OpenApiClient filter = new OpenApiClient();
        filter.setAppKey(request.getAppKey());
        if (request.getStatus() != null) filter.setStatus(OpenApiClientStatus.fromCode(request.getStatus()).getCode());
        try (Page<OpenApiClient> page = PageHelper.startPage(request.getPageNum(), request.getPageSize())) {
            List<OpenApiClient> rows = clients.selectList(filter);
            OpenApiClientPageVo result = new OpenApiClientPageVo();
            result.setList(rows.stream().map(this::toVo).collect(Collectors.toList()));
            result.setTotal(page.getTotal());
            return result;
        }
    }

    public OpenApiClientVo detail(Long clientId) { return toVo(required(clientId)); }

    @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)

    public void changeStatus(OpenApiClientStatusRequest request) {
        OpenApiClientStatus status = OpenApiClientStatus.fromCode(request.getStatus());
        lock(request.getClientId());
        OpenApiCredentialChange change = change(request.getClientId());
        change.setStatus(status.getCode());
        requireUpdated(extra.changeCredentials(change));
    }

    @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)

    public OpenApiSecretVo resetSecret(OpenApiClientIdRequest request) {
        OpenApiClient client = lock(request.getClientId());
        String secret = randomValue(32);
        OpenApiCredentialChange change = change(client.getClientId());
        change.setSecretHash(encoder.encode(secret));
        requireUpdated(extra.changeCredentials(change));
        return secretVo(client, secret);
    }

    public List<String> apiCodes(Long clientId) {
        required(clientId);
        return grants.selectList(grantFilter(clientId)).stream().map(OpenApiClientApi::getApiCode).collect(Collectors.toList());
    }

    @Transactional(transactionManager="platformTransactionManager", rollbackFor=Exception.class)

    public void replaceApis(OpenApiClientApisRequest request) {
        // 与启停/重置共用行锁，避免并发替换授权产生半份配置。
        lock(request.getClientId());
        List<OpenApiClientApi> old = grants.selectList(grantFilter(request.getClientId()));
        if (!old.isEmpty()) {
            Long[] ids = old.stream().map(OpenApiClientApi::getId).toArray(Long[]::new);
            if (grants.deleteByIds(ids) != ids.length) throw new ServiceException("接口授权更新失败");
        }
        String operator = SecurityUtils.getUsername();
        for (String apiCode : new LinkedHashSet<>(request.getApiCodes())) {
            OpenApiClientApi grant = grantFilter(request.getClientId());
            grant.setApiCode(apiCode); grant.setCreateBy(operator); grant.setCreateTime(new Date());
            requireUpdated(grants.insert(grant));
        }
    }

    public OpenApiPrincipal authenticate(OpenApiCredentialRequest request) {
        OpenApiClient client = extra.selectByAppKey(request.getAppKey());
        if (client == null || !enabled(client) || !encoder.matches(request.getAppSecret(), client.getSecretHash()))
            throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
        return principal(client);
    }

    public OpenApiPrincipal validateToken(OpenApiTokenCheck check) {
        OpenApiClient client = clients.selectById(check.getClientId());
        if (client == null || !enabled(client) || client.getCredentialVersion() != check.getCredentialVersion())
            throw new OpenApiException(OpenApiError.INVALID_CREDENTIALS);
        return principal(client);
    }

    public void authorize(OpenApiAuthorizationQuery query) {
        OpenApiClientApi filter = grantFilter(query.getPrincipal().getClientId());
        filter.setApiCode(query.getApiCode());
        if (grants.selectList(filter).isEmpty()) throw new OpenApiException(OpenApiError.ACCESS_DENIED);
    }

    private boolean enabled(OpenApiClient client) {
        return Integer.valueOf(OpenApiClientStatus.ENABLED.getCode()).equals(client.getStatus());
    }
    private OpenApiPrincipal principal(OpenApiClient client) {
        return new OpenApiPrincipal(client.getClientId(), client.getAppKey(), client.getCredentialVersion());
    }
    private OpenApiClient required(Long id) {
        OpenApiClient client = clients.selectById(id);
        if (client == null) throw new ServiceException("client不存在");
        return client;
    }
    private OpenApiClient lock(Long id) {
        OpenApiClient client = extra.lockById(id);
        if (client == null) throw new ServiceException("client不存在");
        return client;
    }
    private OpenApiCredentialChange change(Long clientId) {
        OpenApiCredentialChange change = new OpenApiCredentialChange();
        change.setClientId(clientId); change.setUpdateBy(SecurityUtils.getUsername()); return change;
    }
    private OpenApiClientApi grantFilter(Long clientId) {
        OpenApiClientApi filter = new OpenApiClientApi(); filter.setClientId(clientId); return filter;
    }
    private void requireUpdated(int rows) {
        if (rows != 1) throw new ServiceException("client操作未保存");
    }
    private String randomValue(int length) {
        byte[] bytes = new byte[length]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private OpenApiSecretVo secretVo(OpenApiClient client, String secret) {
        OpenApiSecretVo vo = new OpenApiSecretVo();
        vo.setClientId(client.getClientId()); vo.setAppKey(client.getAppKey()); vo.setAppSecret(secret); return vo;
    }
    private OpenApiClientVo toVo(OpenApiClient client) {
        OpenApiClientVo vo = new OpenApiClientVo();
        vo.setClientId(client.getClientId()); vo.setAppKey(client.getAppKey());
        vo.setClientName(client.getClientName()); vo.setStatus(client.getStatus());
        vo.setStatusName(OpenApiClientStatus.fromCode(client.getStatus()).getName());
        vo.setRemark(client.getRemark()); vo.setCreateTime(client.getCreateTime()); vo.setUpdateTime(client.getUpdateTime());
        return vo;
    }
}
