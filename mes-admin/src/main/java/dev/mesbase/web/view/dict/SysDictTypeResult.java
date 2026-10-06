package dev.mesbase.web.view.dict;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysDictTypeResult(String dictId, String dictName, String dictType, String status, String createBy,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                                  String updateBy,
                                  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                                  String remark) {
}
