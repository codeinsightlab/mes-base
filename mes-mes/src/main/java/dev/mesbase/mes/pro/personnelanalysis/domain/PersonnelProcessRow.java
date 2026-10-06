package dev.mesbase.mes.pro.personnelanalysis.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PersonnelProcessRow {
    private Long processId;
    private String processCode;
    private String processName;
    private Long activeMonths;
    private Long productCount;
    private Long workorderCount;
    private Long reportRecordCount;
    private LocalDateTime lastReportTime;
}
