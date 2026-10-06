package dev.mesbase.mes.pro.personnelanalysis.mapper;

import dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelProcessRow;
import dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelTrendRow;
import dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelQuery;
import dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelAccountRow;
import dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelProductRow;
import dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelRecordVO;
import java.util.List;

public interface ProReportingWorkPersonnelExtraMapper {
    java.util.List<dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelRecordVO> selectRecords(dev.mesbase.mes.pro.personnelanalysis.domain.PersonnelQuery query);

    long countAccounts(PersonnelQuery query);
    List<PersonnelAccountRow> selectAccounts(PersonnelQuery query);
    PersonnelAccountRow selectAccount(PersonnelQuery query);
    List<PersonnelProcessRow> selectProcesses(PersonnelQuery query);
    List<PersonnelProductRow> selectProducts(PersonnelQuery query);
    List<PersonnelTrendRow> selectTrend(PersonnelQuery query);
    java.time.LocalDateTime selectFirstRecordTime();
}
