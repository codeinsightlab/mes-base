package dev.mesbase.web.view.dept;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;

/** HTTP response whitelist; persistence and credential fields are intentionally absent. */
public record SysDeptResult(String deptId, String parentId, String ancestors, String deptName, Integer orderNum,
                              String leader, String phone, String email, String status, String delFlag,
                              String parentName, List<SysDeptResult> children, String createBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date createTime,
                              String updateBy,
                              @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date updateTime,
                              String remark) {
}
