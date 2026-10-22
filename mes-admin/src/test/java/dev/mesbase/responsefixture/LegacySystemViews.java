package dev.mesbase.responsefixture;

import java.util.Optional;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.mesbase.common.core.domain.TreeSelect;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.domain.entity.SysAutoCodeRule;
import dev.mesbase.common.core.domain.entity.SysDept;
import dev.mesbase.common.core.domain.entity.SysDictData;
import dev.mesbase.common.core.domain.entity.SysDictType;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.system.domain.SysAttachment;
import dev.mesbase.system.domain.SysConfig;
import dev.mesbase.system.domain.SysLogininfor;
import dev.mesbase.system.domain.SysMessage;
import dev.mesbase.system.domain.SysNotice;
import dev.mesbase.system.domain.SysOperLog;
import dev.mesbase.system.domain.SysPost;
import dev.mesbase.system.domain.SysRoleDept;
import dev.mesbase.system.domain.SysRoleMenu;
import dev.mesbase.system.domain.SysUserPost;
import dev.mesbase.system.domain.SysUserRecipientQuery;
import dev.mesbase.system.domain.SysUserRole;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TEST ONLY: frozen pre-migration response oracle, never registered in production. */
final class LegacySystemViews {
    public static Object project(Object value) {
        if (value == null) return null;
        if (value instanceof Long id) return id.toString();
        if (value instanceof TreeSelect tree)
            return new TreeView(Objects.toString(tree.getId(), null), tree.getLabel(), project(tree.getChildren()));
        if (value instanceof List<?> list) return list.stream().map(LegacySystemViews::project).toList();
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<Object, Object>();
            map.forEach((k, v) -> result.put(k, project(v)));
            return result;
        }
        if (value instanceof SysAutoCodeRule row)
            return new SysAutoCodeRuleView(Objects.toString(row.getRuleId(), null), row.getRuleCode(), row.getRuleName(), row.getRuleDesc(), row.getMaxLength(), row.getIsPadded(), row.getPaddedChar(), row.getPaddedMethod(), row.getEnableFlag(), row.getRemark(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime());
        if (value instanceof SysAutoCodePart row)
            return new SysAutoCodePartView(Objects.toString(row.getPartId(), null), Objects.toString(row.getRuleId(), null), row.getPartIndex(), row.getPartType(), row.getPartCode(), row.getPartName(), row.getPartLength(), row.getDateFormat(), row.getInputCharacter(), row.getFixCharacter(), row.getSeriaStartNo(), row.getSeriaStep(), row.getCycleFlag(), row.getCycleMethod(), row.getRemark(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime());
        if (value instanceof SysRole row)
            return new SysRoleView(row.getKind(), row.getFactoryId(), Objects.toString(row.getRoleId(), null), row.getRoleName(), row.getRoleKey(), Optional.ofNullable(row.getRoleSort()).map(nullableValue -> Integer.valueOf(nullableValue)).orElse(null), row.getDataScope(), row.isMenuCheckStrictly(), row.isDeptCheckStrictly(), row.getStatus(), row.getDelFlag(), row.isFlag(), Optional.ofNullable(row.getMenuIds()).map(nullableValue -> Arrays.stream(nullableValue).map(String::valueOf).toArray(String[]::new)).orElse(null), Optional.ofNullable(row.getDeptIds()).map(nullableValue -> Arrays.stream(nullableValue).map(String::valueOf).toArray(String[]::new)).orElse(null), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysDictData row)
            return new SysDictDataView(Objects.toString(row.getDictCode(), null), row.getDictSort(), row.getDictLabel(), row.getDictValue(), row.getDictType(), row.getCssClass(), row.getListClass(), row.getIsDefault(), row.getStatus(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysUser row)
            return new SysUserView(Objects.toString(row.getUserId(), null), Objects.toString(row.getDeptId(), null), row.getUserName(), row.getNickName(), row.getUserType(), row.getEmail(), row.getPhonenumber(), row.getSex(), row.getAvatar(), row.getStatus(), row.getDelFlag(), row.getLoginIp(), row.getLoginDate(), project(row.getDept()), project(row.getRoles()), Optional.ofNullable(row.getRoleIds()).map(nullableValue -> Arrays.stream(nullableValue).map(String::valueOf).toArray(String[]::new)).orElse(null), Optional.ofNullable(row.getPostIds()).map(nullableValue -> Arrays.stream(nullableValue).map(String::valueOf).toArray(String[]::new)).orElse(null), Objects.toString(row.getRoleId(), null), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysMenu row)
            return new SysMenuView(Objects.toString(row.getMenuId(), null), row.getMenuName(), row.getParentName(), Objects.toString(row.getParentId(), null), row.getOrderNum(), row.getPath(), row.getComponent(), row.getQuery(), row.getIsFrame(), row.getIsCache(), row.getMenuType(), row.getVisible(), row.getStatus(), row.getPerms(), row.getIcon(), project(row.getChildren()), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysDictType row)
            return new SysDictTypeView(Objects.toString(row.getDictId(), null), row.getDictName(), row.getDictType(), row.getStatus(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysDept row)
            return new SysDeptView(Objects.toString(row.getDeptId(), null), Objects.toString(row.getParentId(), null), row.getAncestors(), row.getDeptName(), Optional.ofNullable(row.getOrderNum()).map(nullableValue -> Integer.valueOf(nullableValue)).orElse(null), row.getLeader(), row.getPhone(), row.getEmail(), row.getStatus(), row.getDelFlag(), row.getParentName(), project(row.getChildren()), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysOperLog row)
            return new SysOperLogView(row.getRequestId(), row.getActorId(), row.getFactoryId(), row.getExecutionScope(), row.getCostTime(), Objects.toString(row.getOperId(), null), row.getTitle(), row.getBusinessType(), row.getBusinessTypes(), row.getMethod(), row.getRequestMethod(), row.getOperatorType(), row.getOperName(), row.getDeptName(), row.getOperUrl(), row.getOperIp(), row.getOperLocation(), row.getOperParam(), row.getJsonResult(), row.getStatus(), row.getErrorMsg(), row.getOperTime(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysUserRole row)
            return new SysUserRoleView(Objects.toString(row.getUserId(), null), Objects.toString(row.getRoleId(), null));
        if (value instanceof SysRoleMenu row)
            return new SysRoleMenuView(Objects.toString(row.getRoleId(), null), Objects.toString(row.getMenuId(), null));
        if (value instanceof SysConfig row)
            return new SysConfigView(Objects.toString(row.getConfigId(), null), row.getConfigName(), row.getConfigKey(), row.getConfigValue(), row.getConfigType(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysNotice row)
            return new SysNoticeView(Objects.toString(row.getNoticeId(), null), row.getNoticeTitle(), row.getNoticeType(), row.getNoticeContent(), row.getStatus(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysRoleDept row)
            return new SysRoleDeptView(Objects.toString(row.getRoleId(), null), Objects.toString(row.getDeptId(), null));
        if (value instanceof SysUserRecipientQuery row)
            return new SysUserRecipientQueryView(project(row.getUserIds()), Objects.toString(row.getUserId(), null), Objects.toString(row.getDeptId(), null), row.getDeptName(), row.getUserName(), row.getNickName(), row.getEmail(), row.getPhonenumber(), row.getSex(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysLogininfor row)
            return new SysLogininforView(row.getRequestId(), row.getActorId(), Objects.toString(row.getInfoId(), null), row.getUserName(), row.getStatus(), row.getIpaddr(), row.getLoginLocation(), row.getBrowser(), row.getOs(), row.getMsg(), row.getLoginTime(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysUserPost row)
            return new SysUserPostView(Objects.toString(row.getUserId(), null), Objects.toString(row.getPostId(), null));
        if (value instanceof SysPost row)
            return new SysPostView(Objects.toString(row.getPostId(), null), row.getPostCode(), row.getPostName(), Optional.ofNullable(row.getPostSort()).map(nullableValue -> Integer.valueOf(nullableValue)).orElse(null), row.getStatus(), row.isFlag(), row.getCreateBy(), row.getCreateTime(), row.getUpdateBy(), row.getUpdateTime(), row.getRemark());
        if (value instanceof SysMessage r)
            return new MessageView(String.valueOf(r.getMessageId()), r.getMessageType(), r.getMessageLevel(), r.getMessageTitle(), r.getMessageContent(), String.valueOf(r.getSenderId()), r.getSenderName(), r.getSenderNick(), String.valueOf(r.getRecipientId()), r.getRecipientName(), r.getRecipientNick(), r.getProcessTime(), r.getCallBack(), r.getStatus(), r.getRemark(), r.getCreateTime());
        if (value instanceof SysAttachment r)
            return new AttachmentView(String.valueOf(r.getAttachmentId()), Objects.toString(r.getSourceDocId(), null), r.getSourceDocType(), r.getFileId(), r.getFileUrl(), r.getFileName(), r.getOrignalName(), r.getFileType(), r.getFileSize(), String.valueOf(r.getOwnerId()), r.getFactoryId(), r.getResource(), r.getCreateTime());
        return value;
    }

    public record TreeView(String id, String label, Object children) {
    }

    public record SysRoleView(String kind, String factoryId, String roleId, String roleName, String roleKey,
                              Integer roleSort, String dataScope, boolean menuCheckStrictly, boolean deptCheckStrictly,
                              String status, String delFlag, boolean flag, String[] menuIds, String[] deptIds,
                              String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
    }

    public record SysDictDataView(String dictCode, Long dictSort, String dictLabel, String dictValue, String dictType,
                                  String cssClass, String listClass, String isDefault, String status, String createBy,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                  String updateBy,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                  String remark) {
    }

    public record SysUserView(String userId, String deptId, String userName, String nickName, String userType,
                              String email, String phonenumber, String sex, String avatar, String status,
                              String delFlag, String loginIp,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date loginDate,
                              Object dept, Object roles, String[] roleIds, String[] postIds, String roleId,
                              String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
    }

    public record SysMenuView(String menuId, String menuName, String parentName, String parentId, Integer orderNum,
                              String path, String component, String query, String isFrame, String isCache,
                              String menuType, String visible, String status, String perms, String icon,
                              Object children, String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
    }

    public record SysDictTypeView(String dictId, String dictName, String dictType, String status, String createBy,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                  String updateBy,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                  String remark) {
    }

    public record SysDeptView(String deptId, String parentId, String ancestors, String deptName, Integer orderNum,
                              String leader, String phone, String email, String status, String delFlag,
                              String parentName, Object children, String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
    }

    public record SysOperLogView(String requestId, String actorId, String factoryId, String executionScope,
                                 Long costTime, String operId, String title, Integer businessType,
                                 Integer[] businessTypes, String method, String requestMethod, Integer operatorType,
                                 String operName, String deptName, String operUrl, String operIp, String operLocation,
                                 String operParam, String jsonResult, Integer status, String errorMsg,
                                 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date operTime,
                                 String createBy,
                                 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                 String updateBy,
                                 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                 String remark) {
    }

    public record SysUserRoleView(String userId, String roleId) {
    }

    public record SysRoleMenuView(String roleId, String menuId) {
    }

    public record SysConfigView(String configId, String configName, String configKey, String configValue,
                                String configType, String createBy,
                                @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                String updateBy,
                                @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                String remark) {
    }

    public record SysNoticeView(String noticeId, String noticeTitle, String noticeType, String noticeContent,
                                String status, String createBy,
                                @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                String updateBy,
                                @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                String remark) {
    }

    public record SysRoleDeptView(String roleId, String deptId) {
    }

    public record SysUserRecipientQueryView(Object userIds, String userId, String deptId, String deptName,
                                            String userName, String nickName, String email, String phonenumber,
                                            String sex, String createBy,
                                            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                            String updateBy,
                                            @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                            String remark) {
    }

    public record SysLogininforView(String requestId, String actorId, String infoId, String userName, String status,
                                    String ipaddr, String loginLocation, String browser, String os, String msg,
                                    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date loginTime,
                                    String createBy,
                                    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                    String updateBy,
                                    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                    String remark) {
    }

    public record SysUserPostView(String userId, String postId) {
    }

    public record SysPostView(String postId, String postCode, String postName, Integer postSort, String status,
                              boolean flag, String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
    }

    public record MessageView(String messageId, String messageType, String messageLevel, String messageTitle,
                              String messageContent, String senderId, String senderName, String senderNick,
                              String recipientId, String recipientName, String recipientNick,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date processTime,
                              String callBack, String status, String remark,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime) {
    }

    public record AttachmentView(String attachmentId, String sourceDocId, String sourceDocType, String fileId,
                                 String fileUrl, String fileName, String orignalName, String fileType,
                                 BigDecimal fileSize, String ownerId, String factoryId, String resource,
                                 @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime) {
    }

    public record SysAutoCodeRuleView(String ruleId, String ruleCode, String ruleName, String ruleDesc,
                                      Integer maxLength, String isPadded, String paddedChar, String paddedMethod,
                                      String enableFlag, String remark, String createBy,
                                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                      String updateBy,
                                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime) {
    }

    public record SysAutoCodePartView(String partId, String ruleId, Integer partIndex, String partType, String partCode,
                                      String partName, Integer partLength, String dateFormat, String inputCharacter,
                                      String fixCharacter, Integer seriaStartNo, Integer seriaStep, String cycleFlag,
                                      String cycleMethod, String remark, String createBy,
                                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                      String updateBy,
                                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime) {
    }
}
