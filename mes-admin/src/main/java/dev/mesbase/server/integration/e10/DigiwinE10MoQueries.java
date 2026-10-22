package dev.mesbase.server.integration.e10;

import dev.mesbase.common.utils.StringUtils;
import com.lixin.capability.digiwin.e10.E10OapiClient;
import com.lixin.capability.digiwin.e10.E10Host;
import com.lixin.capability.digiwin.e10.E10Service;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import java.util.HashMap;
import com.lixin.capability.digiwin.e10.gateway.OapiMoGateway;
import com.lixin.capability.digiwin.e10.gateway.OapiCmsGateway;
import com.lixin.capability.digiwin.e10.contract.mo.MoContract;
import com.lixin.capability.digiwin.e10.contract.cms.WarehouseContract;
import com.lixin.capability.digiwin.e10.response.E10OperationBusinessResponse;
import com.lixin.capability.digiwin.e10.response.cms.WarehouseQueryRow;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;
import com.lixin.capability.digiwin.e10.request.E10QueryRequest;
import com.lixin.capability.digiwin.e10.response.E10QueryBusinessResponse;
import com.lixin.capability.digiwin.e10.response.mo.MoQueryRow;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.util.Map;

/**
 * 按当前工厂显式调用 MO/CMS Query/Read；共用 SDK 传输，不修改 MES 业务事实。
 * SDK 异常原样传播，保留 Reason、Outcome 与完整响应，不记录报文、不重试。
 */
public final class DigiwinE10MoQueries implements AutoCloseable {
    public static final String PROVIDER = "DIGIWIN_E10";

    private final FactoryOapiService configurations;
    private final Map<String, E10OapiClient> clients = new HashMap<>();
    private boolean closed;

    DigiwinE10MoQueries(FactoryOapiService configurations) {
        this.configurations = configurations;
    }

    private synchronized E10OapiClient client(FactoryIntegrationSnapshot snapshot) {
        if (closed) throw new IllegalStateException("E10 client is closed");
        if (snapshot.mode() != FactoryRuntimeMode.ERP || !PROVIDER.equals(snapshot.erpType()))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var configuration = configurations.connection(snapshot.factoryId().value());
        if (configuration == null || !snapshot.factoryId().value().equals(configuration.factoryId())
                || !configuration.reference().equals(snapshot.configurationRef())
                || !Long.valueOf(configuration.revision()).equals(snapshot.configurationRevision()))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        String key = configuration.factoryId() + ":" + configuration.reference() + ":" + configuration.revision();
        return clients.computeIfAbsent(key, ignored -> {
            var settings = configuration.settings();
            settings.validate();
            var host = settings.host();
            var service = settings.service();
            return E10OapiClient.builder().endpoint(settings.endpoint())
                    .host(new E10Host(host.ver(), host.prod(), host.timezone(), host.ip(), host.id(), host.lang(), host.acct()))
                    .service(new E10Service(service.prod(), service.ip(), service.id())).build();
        });
    }

    public E10QueryBusinessResponse<MoQueryRow> queryMo(E10QueryRequest request) {
        var snapshot = FactoryIntegrationContext.required();
        validateQuery(request);
        return new OapiMoGateway(client(snapshot)).mo().query(request);
    }

    public E10QueryBusinessResponse<WarehouseQueryRow> queryWarehouse(E10QueryRequest request) {
        var snapshot = FactoryIntegrationContext.required();
        validateQuery(request);
        return new OapiCmsGateway(client(snapshot)).warehouse().query(request);
    }

    /**
     * 定位值必须由调用者确认；DOC_NO 的定义不证明跨组织唯一。
     */
    public E10OperationBusinessResponse<MoContract.ReadResponseData> readMo(String docNo) {
        var snapshot = FactoryIntegrationContext.required();
        requiredKey(docNo);
        var key = new MoContract.ReadKey();
        key.setDocNo(JsonNodeFactory.instance.textNode(docNo));
        var request = new MoContract.ReadRequest();
        request.setKeys(List.of(key));
        request.setDataExchangeProjectId(MoContract.READ_PROJECT_ID);
        return new OapiMoGateway(client(snapshot)).mo().read(request);
    }

    /**
     * 保留三个独立定位字段，不从 warehouse_no 猜测组织或据点。
     */
    public E10OperationBusinessResponse<WarehouseContract.ReadResponseData> readWarehouse(
            String warehouseCode, String ownerOrgRtk, String plantCode) {
        var snapshot = FactoryIntegrationContext.required();
        requiredKey(warehouseCode);
        requiredKey(ownerOrgRtk);
        requiredKey(plantCode);
        var key = new WarehouseContract.ReadKey();
        key.setWarehouseCode(JsonNodeFactory.instance.textNode(warehouseCode));
        key.setOwnerOrgRtk(JsonNodeFactory.instance.textNode(ownerOrgRtk));
        key.setOwnerOrgPlantPlantCode(JsonNodeFactory.instance.textNode(plantCode));
        var request = new WarehouseContract.ReadRequest();
        request.setKeys(List.of(key));
        request.setDataExchangeProjectId(WarehouseContract.READ_PROJECT_ID);
        return new OapiCmsGateway(client(snapshot)).warehouse().read(request);
    }

    private static void requiredKey(String value) {
        if (StringUtils.isBlank(value) || value.length() > 128)
            throw new IllegalArgumentException("E10 Read requires explicit bounded locator values");
    }

    private static void validateQuery(E10QueryRequest request) {
        if (request == null || request.getPageNo() == null || request.getPageNo() < 1
                || request.getPageNo() > 10000
                || request.getPageSize() == null || request.getPageSize() < 1
                || request.getPageSize() > 100
                || request.getIsGetSchema() == null || request.getIsGetCount() == null
                || request.getConditions() == null || request.getOrders() == null) {
            throw new IllegalArgumentException("E10 MO query requires explicit pagination, schema/count flags, conditions and orders");
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        clients.values().forEach(E10OapiClient::close);
        clients.clear();
    }
}
