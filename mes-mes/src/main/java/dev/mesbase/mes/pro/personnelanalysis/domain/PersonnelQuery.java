package dev.mesbase.mes.pro.personnelanalysis.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PersonnelQuery {
    // 仅保留筛选条件；PageHelper 会按 Mapper 入参中的 pageNum/pageSize 自动分页，连 count 查询也会受影响。
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String accountKeyword;
    private List<Long> matchingUserIds;
    private Long userId;
    private Long processId;
    private String itemCode;
}
