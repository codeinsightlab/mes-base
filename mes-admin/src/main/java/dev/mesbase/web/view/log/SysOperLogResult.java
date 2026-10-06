package dev.mesbase.web.view.log;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysOperLogResult(String requestId, String actorId, String factoryId, String executionScope,
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
