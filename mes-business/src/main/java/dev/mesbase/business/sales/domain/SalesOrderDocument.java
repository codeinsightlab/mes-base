package dev.mesbase.business.sales.domain;

import java.util.List;

public record SalesOrderDocument(MesSalesOrderEntity header, List<MesSalesOrderItemEntity> items) {
}
