package dev.mesbase.framework.openapi;

import java.util.ArrayList;
import java.util.List;

public class OpenApiCatalogVo {
    private String apiCode;
    private List<OpenApiMappingVo> mappings = new ArrayList<>();

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public List<OpenApiMappingVo> getMappings() {
        return mappings;
    }

    public void setMappings(List<OpenApiMappingVo> mappings) {
        this.mappings = mappings;
    }
}
