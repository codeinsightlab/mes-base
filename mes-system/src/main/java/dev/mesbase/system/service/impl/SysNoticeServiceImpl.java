package dev.mesbase.system.service.impl;

import dev.mesbase.system.domain.SysNotice;
import dev.mesbase.system.mapper.SysNoticeMapper;
import dev.mesbase.system.service.ISysNoticeService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 公告 服务层实现
 * 
 * @author ruoyi
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysNoticeServiceImpl implements ISysNoticeService
{
    @org.springframework.beans.factory.annotation.Autowired private jakarta.validation.Validator contractValidator;
    @Autowired
    private SysNoticeMapper noticeMapper;
    @Autowired private SysAttachmentServiceImpl attachments;

    /**
     * 查询公告信息
     * 
     * @param noticeId 公告ID
     * @return 公告信息
     */
    @Override
    public SysNotice selectNoticeById(Long noticeId)
    {
        return noticeMapper.selectNoticeById(noticeId);
    }

    /**
     * 查询公告列表
     * 
     * @param notice 公告信息
     * @return 公告集合
     */
    @Override
    public List<SysNotice> selectNoticeList(SysNotice notice)
    {
        return noticeMapper.selectNoticeList(notice);
    }

    /**
     * 新增公告
     * 
     * @param notice 公告信息
     * @return 结果
     */
    @Override
    public int insertNotice(SysNotice notice)
    {
        dev.mesbase.common.utils.bean.BeanValidators.validateWithException(contractValidator,notice);
        notice.setNoticeContent(dev.mesbase.common.utils.html.RichTextSanitizer.clean(notice.getNoticeContent()));int rows=noticeMapper.insertNotice(notice);attachments.bindNotice(notice.getNoticeId(),notice.getNoticeContent());return rows;
    }

    /**
     * 修改公告
     * 
     * @param notice 公告信息
     * @return 结果
     */
    @Override
    public int updateNotice(SysNotice notice)
    {
        dev.mesbase.common.utils.bean.BeanValidators.validateWithException(contractValidator,notice);
        if(noticeMapper.selectNoticeById(notice.getNoticeId())==null)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.NOT_FOUND);notice.setNoticeContent(dev.mesbase.common.utils.html.RichTextSanitizer.clean(notice.getNoticeContent()));int rows=noticeMapper.updateNotice(notice);attachments.bindNotice(notice.getNoticeId(),notice.getNoticeContent());return rows;
    }

    /**
     * 删除公告对象
     * 
     * @param noticeId 公告ID
     * @return 结果
     */
    @Override
    public int deleteNoticeById(Long noticeId)
    {
        return noticeMapper.deleteNoticeById(noticeId);
    }

    /**
     * 批量删除公告信息
     * 
     * @param noticeIds 需要删除的公告ID
     * @return 结果
     */
    @Override
    public int deleteNoticeByIds(Long[] noticeIds)
    {
        return noticeMapper.deleteNoticeByIds(noticeIds);
    }
}
