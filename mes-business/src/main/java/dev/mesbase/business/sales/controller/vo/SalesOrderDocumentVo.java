package dev.mesbase.business.sales.controller.vo;

import java.util.List;

public record SalesOrderDocumentVo(MesSalesOrderVo header, List<MesSalesOrderItemVo> items) {
}
