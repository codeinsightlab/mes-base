package dev.mesbase.business.production.controller.vo;

import java.util.List;

public record ProductionPlanDocumentVo(MesProductionPlanVo header, List<MesProductionPlanItemVo> items) {
}
