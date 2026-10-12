package dev.mesbase.system.openapi.vo;

import java.util.List;

public class OpenApiClientPageVo {
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
