package dev.mesbase.mes.pro.personnelanalysis.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PersonnelAccountRow {
    private Long userId;
    private Long activeMonths;
    private Long processCount;
    private Long productCount;
    private Long reportRecordCount;
    private LocalDateTime firstReportTime;
    private LocalDateTime lastReportTime;
}
