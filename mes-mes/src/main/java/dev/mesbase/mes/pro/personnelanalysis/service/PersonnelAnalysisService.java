package dev.mesbase.mes.pro.personnelanalysis.service;

import dev.mesbase.mes.pro.domain.PageResult;
import dev.mesbase.mes.pro.personnelanalysis.domain.*;
import java.util.List;

public interface PersonnelAnalysisService {
    com.github.pagehelper.PageInfo<dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelRecordVO> records(PersonnelAnalysisQueryDTO request, Long processId, String itemCode);

    PageResult<PersonnelAccountVO> accounts(PersonnelAnalysisQueryDTO request);
    PersonnelAccountVO overview(PersonnelAnalysisQueryDTO request);
    PageResult<PersonnelProcessVO> processes(PersonnelAnalysisQueryDTO request);
    PageResult<PersonnelProductVO> products(PersonnelAnalysisQueryDTO request);
    List<PersonnelTrendVO> trend(PersonnelAnalysisQueryDTO request);
}
