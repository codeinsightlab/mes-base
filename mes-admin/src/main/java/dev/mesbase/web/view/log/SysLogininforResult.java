package dev.mesbase.web.view.log;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysLogininforResult(String requestId, String actorId, String infoId, String userName, String status,
                                    String ipaddr, String loginLocation, String browser, String os, String msg,
                                    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date loginTime,
                                    String createBy,
                                    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                    String updateBy,
                                    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                    String remark) {
}
