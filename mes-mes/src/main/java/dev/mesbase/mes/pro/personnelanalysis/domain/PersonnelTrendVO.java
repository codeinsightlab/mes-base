package dev.mesbase.mes.pro.personnelanalysis.domain;

import lombok.Data;

@Data
public class PersonnelTrendVO {
    private String month;
    private Long activeDayCount;
    private Long reportRecordCount;
    private Long processCount;
    private Long productCount;
    private Boolean partialMonth;
}
