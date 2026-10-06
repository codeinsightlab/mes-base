package dev.mesbase.mes.pro.personnelanalysis.domain;

import lombok.Data;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
public class PersonnelAnalysisQueryDTO {
    private String range = "12M";
    private String startTime;
    private String endTime;
    private String accountKeyword;
    private Long userId;
    private Long processId;
    private String itemCode;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(100) private Integer pageSize = 30;
}
