package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.databind.JsonNode;
import dev.mesbase.business.production.service.WorkOrderService;
import dev.mesbase.business.wm.service.IWmWarehouseService;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.server.integration.e10.DigiwinE10SyncMapping.Item;
import dev.mesbase.server.integration.e10.DigiwinE10SyncPreview.Kind;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * One candidate per transaction; remote HTTP is completed before this boundary.
 */
public class DigiwinE10SyncWriter {
    private final DigiwinE10SyncMapping mapping;
    private final WorkOrderService workorders;
    private final IWmWarehouseService warehouses;

    public DigiwinE10SyncWriter(DigiwinE10SyncMapping mapping, WorkOrderService workorders,
                                IWmWarehouseService warehouses) {
        this.mapping = mapping;
        this.workorders = workorders;
        this.warehouses = warehouses;
    }

    @Transactional(transactionManager = "businessTransactionManager")
    public Item write(Kind kind, JsonNode raw, E10SyncContract.Rule rule, SyncCoordinator.Run run) {
        var prepared = mapping.prepare(kind, raw, rule);
        if (prepared.plan() != null) workorders.syncExternal(prepared.plan(), run);
        else if (prepared.warehouse() != null)
            warehouses.upsert(FactoryContext.required(), List.of(prepared.warehouse()), run);
        return prepared.item();
    }
}
