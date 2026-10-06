package dev.mesbase.mes.pro.domain;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> rows;    // 当前页的数据列表
    private Long total;       // 总记录数
    private Integer pageNum;  // 当前页码
    private Integer pageSize; // 每页条数
}
