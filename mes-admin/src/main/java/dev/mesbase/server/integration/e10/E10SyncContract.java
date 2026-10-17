package dev.mesbase.server.integration.e10;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;

/**
 * Platform-maintained, evidence-backed deployment contract; absent means unverified, never inferred from mode.
 */
public record E10SyncContract(boolean commitEnabled, Rule workorder, Rule warehouse) {
    public record Rule(String evidence, List<String> identityPointers,
                       String sitePointer, String expectedSite,
                       String organizationPointer, String expectedOrganization,
                       String statusPointer, Map<String, String> statuses,
                       String approvalPointer, List<String> approvedValues,
                       String productKey, boolean readRequired,
                       String readCodePointer, String readOwnerPointer, String readPlantPointer,
                       String conditionsJson, String ordersJson) {
        public Rule {
            identityPointers = identityPointers == null ? List.of() : List.copyOf(identityPointers);
            statuses = statuses == null ? Map.of() : Map.copyOf(statuses);
            approvedValues = approvedValues == null ? List.of() : List.copyOf(approvedValues);
        }

        public JsonNode conditions() {
            return array(conditionsJson);
        }

        public JsonNode orders() {
            return array(ordersJson);
        }

        private static JsonNode array(String value) {
            if (value == null || value.length() > 32768) throw new IllegalArgumentException("需明确的有界 JSON 数组");
            try {
                JsonNode array = new ObjectMapper().readTree(value);
                if (array == null || !array.isArray()) throw new IllegalArgumentException("需明确的 JSON 数组");
                return array;
            } catch (JsonProcessingException invalid) {
                throw new IllegalArgumentException("同步条件或排序 JSON 无效", invalid);
            }
        }

        public void validate(boolean mo) {
            if (evidence == null || evidence.isBlank() || evidence.length() > 500
                    || identityPointers.isEmpty() || identityPointers.size() > 4
                    || expectedSite == null || expectedSite.isBlank() || statuses.isEmpty()
                    || conditions().size() > 100 || orders().isEmpty() || orders().size() > 10)
                throw new IllegalArgumentException("同步契约需要身份、据点、状态、排序及证据");
            identityPointers.forEach(Rule::pointer);
            pointer(sitePointer);
            pointer(statusPointer);
            if (organizationPointer != null) {
                pointer(organizationPointer);
                if (expectedOrganization == null || expectedOrganization.isBlank())
                    throw new IllegalArgumentException("组织映射缺失");
            }
            if (mo) {
                pointer(approvalPointer);
                if (approvedValues.isEmpty() || !("CODE".equals(productKey) || "EXTERNAL_CODE".equals(productKey)))
                    throw new IllegalArgumentException("工单需要审核状态及产品编码关系证据");
            }
            for (String target : statuses.values()) {
                if (mo ? !("DRAFT".equals(target) || "RELEASED".equals(target))
                        : !("ENABLED".equals(target) || "DISABLED".equals(target)))
                    throw new IllegalArgumentException("不支持的目标业务状态");
            }
            if (readRequired && !mo) {
                pointer(readCodePointer);
                pointer(readOwnerPointer);
                pointer(readPlantPointer);
            }
        }

        private static void pointer(String value) {
            if (value == null || !value.startsWith("/") || value.length() > 256)
                throw new IllegalArgumentException("需明确的 JSON Pointer 字段定位");
            JsonPointer.compile(value);
        }
    }

    public void validate() {
        if (workorder != null) workorder.validate(true);
        if (warehouse != null) warehouse.validate(false);
        if (commitEnabled && workorder == null && warehouse == null)
            throw new IllegalArgumentException("正式提交需要至少一个已验证契约");
    }
}
