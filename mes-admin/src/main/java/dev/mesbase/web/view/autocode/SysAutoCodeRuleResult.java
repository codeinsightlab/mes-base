package dev.mesbase.web.view.autocode;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysAutoCodeRuleResult(String ruleId, String ruleCode, String ruleType, String ruleName, String ruleDesc,
                                      Integer maxLength, String isPadded, String paddedChar, String paddedMethod,
                                      String enableFlag, String remark, String createBy,
                                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                      String updateBy,
                                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime) {
}
