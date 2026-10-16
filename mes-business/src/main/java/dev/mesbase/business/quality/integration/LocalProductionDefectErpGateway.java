package dev.mesbase.business.quality.integration;

import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import org.springframework.stereotype.Component;

@Component
public class LocalProductionDefectErpGateway implements ProductionDefectErpGateway {
    public ErpActionResult acknowledge(MesProductionDefectEntity version) {
        return ErpActionResult.localSuccess();
    }
}
