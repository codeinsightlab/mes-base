package dev.mesbase.server.integration.e10;

import com.lixin.capability.digiwin.e10.E10OapiClient;
import com.lixin.capability.digiwin.e10.gateway.OapiMoGateway;
import com.lixin.capability.digiwin.e10.request.E10QueryRequest;
import com.lixin.capability.digiwin.e10.response.E10QueryBusinessResponse;
import com.lixin.capability.digiwin.e10.response.mo.MoQueryRow;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import java.util.Map;

/**
 * 显式调用的 E10 工单查询示例；不注册 HTTP、调度、同步或健康探测入口，不修改 MES 业务事实。
 * SDK 异常原样传播，保留 Reason、Outcome 与完整响应，不记录报文、不重试。
 */
public final class DigiwinE10MoQueries implements AutoCloseable {
    public static final String PROVIDER = "DIGIWIN_E10";

    record Binding(String factoryId, long revision, E10OapiClient client) {
        @Override
        public String toString() {
            return "E10Binding[REDACTED]";
        }
    }

    private final Map<String, Binding> bindings;

    DigiwinE10MoQueries(Map<String, Binding> bindings) {
        this.bindings = Map.copyOf(bindings);
    }

    public E10QueryBusinessResponse<MoQueryRow> queryMo(E10QueryRequest request) {
        var snapshot = FactoryIntegrationContext.required();
        var binding = snapshot.configurationRef() == null ? null : bindings.get(snapshot.configurationRef());
        if (snapshot.mode() != FactoryRuntimeMode.ERP || !PROVIDER.equals(snapshot.erpType())
                || binding == null || !binding.factoryId().equals(snapshot.factoryId().value())
                || !Long.valueOf(binding.revision()).equals(snapshot.configurationRevision())) {
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        }
        if (request == null || request.getPageNo() == null || request.getPageNo() < 1
                || request.getPageSize() == null || request.getPageSize() < 1
                || request.getIsGetSchema() == null || request.getIsGetCount() == null
                || request.getConditions() == null || request.getOrders() == null) {
            throw new IllegalArgumentException("E10 MO query requires explicit pagination, schema/count flags, conditions and orders");
        }
        return new OapiMoGateway(binding.client()).mo().query(request);
    }

    @Override
    public void close() {
        bindings.values().forEach(binding -> binding.client().close());
    }
}
