package dev.mesbase.web.view.dict;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysDictDataResult(String dictCode, Long dictSort, String dictLabel, String dictValue, String dictType,
                                  String cssClass, String listClass, String isDefault, String status, String createBy,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                  String updateBy,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                  String remark) {
}
