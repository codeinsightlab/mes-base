package dev.mesbase.system.domain;

import java.util.List;
import dev.mesbase.common.core.domain.BaseEntity;
/** Internal query only: authorization metadata is set by DataScope, never bound from HTTP. */

public class SysUserRecipientQuery extends BaseEntity {
    private List<Long> userIds;
    private Long userId;
    private Long deptId;
    private String deptName;
    private String userName;
    private String nickName;
    private String email;
    private String phonenumber;
    private String sex;
public List<Long> getUserIds(){return userIds;} public void setUserIds(List<Long> value){userIds=value;}public Long getUserId(){return userId;} public void setUserId(Long value){userId=value;}public Long getDeptId(){return deptId;} public void setDeptId(Long value){deptId=value;}public String getDeptName(){return deptName;} public void setDeptName(String value){deptName=value;}public String getUserName(){return userName;} public void setUserName(String value){userName=value;}public String getNickName(){return nickName;} public void setNickName(String value){nickName=value;}public String getEmail(){return email;} public void setEmail(String value){email=value;}public String getPhonenumber(){return phonenumber;} public void setPhonenumber(String value){phonenumber=value;}public String getSex(){return sex;} public void setSex(String value){sex=value;}}
