package dev.mesbase.framework.openapi;


public class OpenApiMappingVo {
    private String httpMethod;
    private String path;
    private String groupId;
    private String groupName;
    private String description;
    public OpenApiMappingVo(String httpMethod, String path) {
        this.httpMethod = httpMethod; this.path = path;
    }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
