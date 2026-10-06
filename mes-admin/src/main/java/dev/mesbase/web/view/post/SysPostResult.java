package dev.mesbase.web.view.post;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysPostResult(String postId, String postCode, String postName, Integer postSort, String status,
                              boolean flag, String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
}
