package dev.mesbase.system.service.impl;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.core.domain.MessageEvents;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.utils.DateUtils;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.persistence.CommitActions;
import dev.mesbase.system.domain.SysMessage;
import dev.mesbase.system.mapper.SysMessageMapper;
import dev.mesbase.system.service.ISysMessageService;
import dev.mesbase.system.service.ISysUserService;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息Service业务层处理
 *
 * @author yinjinlu
 * @date 2023-03-06
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SysMessageServiceImpl implements ISysMessageService {
    @Autowired
    private SysMessageMapper sysMessageMapper;
    @Autowired
    private ISysUserService users;
    @Autowired
    private MessageEvents events;
    @Autowired
    private CommitActions effects;

    private SysMessage require(Long id) {
        var row = sysMessageMapper.selectSysMessageByMessageId(id);
        if (row == null || !"N".equals(row.getDeletedFlag())) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private void validate(SysMessage row) {
        if (StringUtils.isBlank(row.getMessageTitle()) || row.getMessageTitle()
                .length() > 100 || row.getMessageContent() == null || row.getMessageContent()
                .isBlank() || row.getMessageContent().length() > 20000 || row.getRecipientId() == null)
            throw new ServiceException("标题、内容和接收人不能为空");
        if (StringUtils.isNotBlank(row.getCallBack()) && !row.getCallBack()
                .matches("/(?!/)[A-Za-z0-9/_?=&.-]{0,250}")) throw new ServiceException("消息回调只能使用系统内路由");
        var recipient = users.selectUserById(row.getRecipientId());
        if (recipient == null || !"0".equals(recipient.getStatus()) || !"0".equals(recipient.getDelFlag()))
            throw new ServiceException("接收人不存在或已停用");
        row.setRecipientName(recipient.getUserName());
        row.setRecipientNick(recipient.getNickName());
    }

    public List<SysMessage> myMessages(SysMessage query) {
        query.setRecipientId(SecurityUtils.getUserId());
        return sysMessageMapper.selectSysMessageList(query);
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int markRead(Long id) {
        var row = require(id);
        if (!row.getRecipientId().equals(SecurityUtils.getUserId())) throw new FoundationException(ErrorCode.NOT_FOUND);
        row.setStatus("READ");
        row.setProcessTime(new Date());
        int updated = sysMessageMapper.updateSysMessage(row);
        effects.afterCommit(() -> events.changed(row.getRecipientId()));
        return updated;
    }

    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int archive(Long[] ids) {
        for (Long id : ids) {
            var row = require(id);
            row.setDeletedFlag("Y");
            sysMessageMapper.updateSysMessage(row);
            effects.afterCommit(() -> events.changed(row.getRecipientId()));
        }
        return ids.length;
    }

    /**
     * 查询消息
     *
     * @param messageId 消息主键
     * @return 消息
     */
    @Override
    public SysMessage selectSysMessageByMessageId(Long messageId) {
        return require(messageId);
    }

    /**
     * 查询消息列表
     *
     * @param sysMessage 消息
     * @return 消息
     */
    @Override
    public List<SysMessage> selectSysMessageList(SysMessage sysMessage) {
        return sysMessageMapper.selectSysMessageList(sysMessage);
    }

    /**
     * 新增消息
     *
     * @param sysMessage 消息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int insertSysMessage(SysMessage sysMessage) {
        sysMessage.setMessageId(null);
        validate(sysMessage);
        var sender = SecurityUtils.getLoginUser().getUser();
        sysMessage.setSenderId(sender.getUserId());
        sysMessage.setSenderName(sender.getUserName());
        sysMessage.setSenderNick(sender.getNickName());
        sysMessage.setStatus("UNREAD");
        sysMessage.setDeletedFlag("N");
        sysMessage.setCreateBy(sender.getUserName());
        sysMessage.setCreateTime(DateUtils.getNowDate());
        int inserted = sysMessageMapper.insertSysMessage(sysMessage);
        long recipientId = sysMessage.getRecipientId();
        effects.afterCommit(() -> events.changed(recipientId));
        return inserted;
    }

    /**
     * 修改消息
     *
     * @param sysMessage 消息
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int updateSysMessage(SysMessage sysMessage) {
        var stored = require(sysMessage.getMessageId());
        validate(sysMessage);
        sysMessage.setSenderId(stored.getSenderId());
        sysMessage.setSenderName(stored.getSenderName());
        sysMessage.setSenderNick(stored.getSenderNick());
        sysMessage.setDeletedFlag("N");
        sysMessage.setStatus(stored.getStatus());
        sysMessage.setUpdateBy(SecurityUtils.getUsername());
        sysMessage.setUpdateTime(DateUtils.getNowDate());
        int updated = sysMessageMapper.updateSysMessage(sysMessage);
        long recipientId = sysMessage.getRecipientId();
        effects.afterCommit(() -> events.changed(recipientId));
        if (!stored.getRecipientId().equals(recipientId))
            effects.afterCommit(() -> events.changed(stored.getRecipientId()));
        return updated;
    }

    /**
     * 批量删除消息
     *
     * @param messageIds 需要删除的消息主键
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int deleteSysMessageByMessageIds(Long[] messageIds) {
        return sysMessageMapper.deleteSysMessageByMessageIds(messageIds);
    }

    /**
     * 删除消息信息
     *
     * @param messageId 消息主键
     * @return 结果
     */
    @Override
    @Transactional(transactionManager = "masterTransactionManager", rollbackFor = Exception.class)
    public int deleteSysMessageByMessageId(Long messageId) {
        return sysMessageMapper.deleteSysMessageByMessageId(messageId);
    }
}
