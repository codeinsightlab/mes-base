package dev.mesbase.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.mesbase.system.domain.SysPost;
import java.util.Objects;

@JsonIgnoreProperties({"createTime", "updateTime", "createBy", "updateBy", "params", "dept", "roles", "children", "ancestors", "delFlag", "admin", "flag", "avatar", "loginDate", "loginIp", "minioUrl", "wxOpenId"})
public record PostForm(Long postId, String postCode, String postName, Integer postSort, String status, String remark) {
    public SysPost toEntity() {
        var result = new SysPost();
        result.setPostId(postId);
        result.setPostCode(postCode);
        result.setPostName(postName);
        result.setPostSort(Objects.toString(postSort, null));
        result.setStatus(status);
        result.setRemark(remark);
        return result;
    }
}
