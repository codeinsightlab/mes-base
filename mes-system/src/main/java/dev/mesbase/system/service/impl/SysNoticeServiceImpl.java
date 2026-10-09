package dev.mesbase.system.service.impl;

import dev.mesbase.common.utils.bean.BeanValidators;
import dev.mesbase.common.utils.html.RichTextSanitizer;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.domain.SysNotice;
import dev.mesbase.system.mapper.SysNoticeMapper;
import dev.mesbase.system.service.ISysNoticeService;
import jakarta.validation.Validator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 公告 服务层实现
 *
 * @author ruoyi
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysNoticeServiceImpl implements ISysNoticeService {
    @Autowired
    private Validator contractValidator;
    @Autowired
    private SysNoticeMapper noticeMapper;
    @Autowired
    private SysAttachmentServiceImpl attachments;

    /**
     * 查询公告信息
     *
     * @param noticeId 公告ID
     * @return 公告信息
     */
    @Override
    public SysNotice selectNoticeById(Long noticeId) {
        return noticeMapper.selectNoticeById(noticeId);
    }

    /**
     * 查询公告列表
     *
     * @param notice 公告信息
     * @return 公告集合
     */
    @Override
    public List<SysNotice> selectNoticeList(SysNotice notice) {
        return noticeMapper.selectNoticeList(notice);
    }

    /**
     * 新增公告
     *
     * @param notice 公告信息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int insertNotice(SysNotice notice) {
        BeanValidators.validateWithException(contractValidator, notice);
        notice.setNoticeContent(RichTextSanitizer.clean(notice.getNoticeContent()));
        int rows = noticeMapper.insertNotice(notice);
        attachments.bindNotice(notice.getNoticeId(), notice.getNoticeContent());
        return rows;
    }

    /**
     * 修改公告
     *
     * @param notice 公告信息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int updateNotice(SysNotice notice) {
        BeanValidators.validateWithException(contractValidator, notice);
        if (noticeMapper.selectNoticeById(notice.getNoticeId()) == null)
            throw new FoundationException(ErrorCode.NOT_FOUND);
        notice.setNoticeContent(RichTextSanitizer.clean(notice.getNoticeContent()));
        int rows = noticeMapper.updateNotice(notice);
        attachments.bindNotice(notice.getNoticeId(), notice.getNoticeContent());
        return rows;
    }

    /**
     * 删除公告对象
     *
     * @param noticeId 公告ID
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int deleteNoticeById(Long noticeId) {
        return noticeMapper.deleteNoticeById(noticeId);
    }

    /**
     * 批量删除公告信息
     *
     * @param noticeIds 需要删除的公告ID
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int deleteNoticeByIds(Long[] noticeIds) {
        return noticeMapper.deleteNoticeByIds(noticeIds);
    }
}
