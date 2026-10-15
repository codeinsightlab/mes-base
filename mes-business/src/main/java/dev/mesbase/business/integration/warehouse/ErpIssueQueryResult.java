package dev.mesbase.business.integration.warehouse;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;

import java.util.LinkedHashMap;
import java.util.Map;

public record ErpIssueQueryResult(Outcome outcome, DocumentStatus documentStatus, Mode executionMode,
                                  String externalId, String externalCode, String message) {
    public enum Outcome {
        FOUND("已找到", "FOUND"),
        NOT_FOUND("未找到", "NOT_FOUND"),
        UNKNOWN("待核实", "UNKNOWN");

        private final String description;
        private final String value;

        Outcome(String description, String value) {
            this.description = description;
            this.value = value;
        }

        /**
         * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
         */
        public static Outcome from(String value) {
            for (Outcome item : values()) {
                if (item.value.equals(value)) return item;
            }
            return null;
        }

        public static Map<String, String> toMap() {
            Map<String, String> result = new LinkedHashMap<>();
            for (Outcome item : values()) {
                result.put(item.value, item.description);
            }
            return result;
        }

        public String getDescription() {
            return description;
        }

        public String getValue() {
            return value;
        }
    }

    public enum DocumentStatus {
        DRAFT("草稿", "DRAFT"),
        PENDING_APPROVAL("待审核", "PENDING_APPROVAL"),
        EFFECTIVE("已生效", "EFFECTIVE"),
        CANCELLED("已作废", "CANCELLED"),
        REJECTED("已拒绝", "REJECTED"),
        UNKNOWN("待核实", "UNKNOWN");

        private final String description;
        private final String value;

        DocumentStatus(String description, String value) {
            this.description = description;
            this.value = value;
        }

        /**
         * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
         */
        public static DocumentStatus from(String value) {
            for (DocumentStatus item : values()) {
                if (item.value.equals(value)) return item;
            }
            return null;
        }

        public static Map<String, String> toMap() {
            Map<String, String> result = new LinkedHashMap<>();
            for (DocumentStatus item : values()) {
                result.put(item.value, item.description);
            }
            return result;
        }

        public String getDescription() {
            return description;
        }

        public String getValue() {
            return value;
        }
    }

    public static ErpIssueQueryResult local() {
        return new ErpIssueQueryResult(Outcome.NOT_FOUND, DocumentStatus.UNKNOWN, Mode.LOCAL_ONLY, null, null, "本地模式，没有ERP单据或审核事实");
    }

    public static ErpIssueQueryResult unknown(Mode mode) {
        return new ErpIssueQueryResult(Outcome.UNKNOWN, DocumentStatus.UNKNOWN, mode, null, null, "ERP查询结果不确定，保持本地状态，请人工核实");
    }
}
