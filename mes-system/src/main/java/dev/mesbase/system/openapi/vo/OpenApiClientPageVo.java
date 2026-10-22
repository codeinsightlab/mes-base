package dev.mesbase.system.openapi.vo;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAlias;

public class OpenApiClientPageVo {
    @JsonAlias("items")
    private List<OpenApiClientVo> list;
    private long total;

    public List<OpenApiClientVo> getList() {
        return list;
    }

    public void setList(List<OpenApiClientVo> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }
}
