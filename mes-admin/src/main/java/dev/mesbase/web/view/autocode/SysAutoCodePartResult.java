package dev.mesbase.web.view.autocode;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysAutoCodePartResult(String partId, String ruleId, Integer partIndex, String partType, String partCode,
                                      String partName, Integer partLength, String dateFormat, String inputCharacter,
                                      String fixCharacter, Integer seriaStartNo, Integer seriaStep, String cycleFlag,
                                      String cycleMethod, String remark, String createBy,
                                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                      String updateBy,
                                      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime) {
}
