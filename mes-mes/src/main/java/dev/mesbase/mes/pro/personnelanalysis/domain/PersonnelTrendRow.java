package dev.mesbase.mes.pro.personnelanalysis.domain;

import lombok.Data;

@Data
public class PersonnelTrendRow {
    private String month;
    private Long activeDayCount;
    private Long reportRecordCount;
    private Long processCount;
    private Long productCount;
}
