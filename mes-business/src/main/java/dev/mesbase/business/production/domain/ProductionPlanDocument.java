package dev.mesbase.business.production.domain;

import java.util.List;

public record ProductionPlanDocument(MesProductionPlanEntity header, List<MesProductionPlanItemEntity> items) {
}
