package dev.mesbase.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.mesbase.common.core.domain.entity.SysDept;
import java.util.Objects;

@JsonIgnoreProperties({"createTime", "updateTime", "createBy", "updateBy", "params", "dept", "roles", "children", "ancestors", "parentName", "remark", "delFlag", "admin", "flag", "avatar", "loginDate", "loginIp", "minioUrl", "wxOpenId"})
public record DeptForm(Long deptId, Long parentId, String deptName, Integer orderNum, String leader, String phone,
                       String email, String status) {
    public SysDept toEntity() {
        var result = new SysDept();
        result.setDeptId(deptId);
        result.setParentId(parentId);
        result.setDeptName(deptName);
        result.setOrderNum(Objects.toString(orderNum, null));
        result.setLeader(leader);
        result.setPhone(phone);
        result.setEmail(email);
        result.setStatus(status);
        return result;
    }
}
