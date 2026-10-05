package dev.mesbase.system.dto;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties({"createTime","updateTime","createBy","updateBy","params","dept","roles","children","ancestors","delFlag","admin","flag","avatar","loginDate","loginIp","minioUrl","wxOpenId"})
public record NoticeForm(Long noticeId, String noticeTitle, String noticeType, String noticeContent, String status, String remark) {
 public dev.mesbase.system.domain.SysNotice toEntity(){var result=new dev.mesbase.system.domain.SysNotice();
result.setNoticeId(noticeId);
result.setNoticeTitle(noticeTitle);
result.setNoticeType(noticeType);
result.setNoticeContent(noticeContent);
result.setStatus(status);
result.setRemark(remark);
return result;}
}
