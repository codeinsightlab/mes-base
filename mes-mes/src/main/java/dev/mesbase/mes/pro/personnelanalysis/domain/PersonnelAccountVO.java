package dev.mesbase.mes.pro.personnelanalysis.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PersonnelAccountVO {
    private Long userId;
    private String userName;
    private String nickName;
    private String accountStatus;
    private String accountStatusName;
    private Long activeMonths;
    private Long processCount;
    private Long productCount;
    private Long reportRecordCount;
    private LocalDateTime firstReportTime;
    private LocalDateTime lastReportTime;
    private LocalDateTime dataThrough;
    private LocalDateTime generatedAt;
    private LocalDateTime rangeStart;
    private LocalDateTime rangeEndExclusive;
}
