package dev.mesbase.business.quality.integration;

import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;

/**
 * Only a declaration acknowledgement. No physical scrap, rework or inventory command.
 */
public interface ProductionDefectErpGateway {
    ErpActionResult acknowledge(MesProductionDefectEntity version);
}
