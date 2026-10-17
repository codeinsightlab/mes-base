package dev.mesbase.business.integration.warehouse;

import java.util.LinkedHashMap;
import java.util.Map;

public record ErpActionResult(Status status, Mode executionMode, String externalReference, String externalCode,
                              String message, boolean stockEffective) {
    /**
     * Creation success alone is not evidence of an effective ERP stock issue.
     */
    public ErpActionResult(Status status, Mode mode, String reference, String code, String message) {
        this(status, mode, reference, code, message, false);
    }

    public ErpActionResult(Status status, Mode executionMode, String externalReference, String message) {
        this(status, executionMode, externalReference, null, message);
    }

    public enum Status {
        SUCCEEDED("成功", "SUCCEEDED"),
        REJECTED("已拒绝", "REJECTED"),
        UNKNOWN("待核实", "UNKNOWN");

        private final String description;
        private final String value;

        Status(String description, String value) {
            this.description = description;
            this.value = value;
        }

        /**
         * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
         */
        public static Status from(String value) {
            for (Status item : values()) {
                if (item.value.equals(value)) return item;
            }
            return null;
        }

        public static Map<String, String> toMap() {
            Map<String, String> result = new LinkedHashMap<>();
            for (Status item : values()) {
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

    public enum Mode {
        LOCAL_ONLY("仅本地", "LOCAL_ONLY"),
        CONNECTED("已连接ERP", "CONNECTED");

        private final String description;
        private final String value;

        Mode(String description, String value) {
            this.description = description;
            this.value = value;
        }

        /**
         * 未知值（包括 null）返回 null，由调用方决定拒绝或保留未知。
         */
        public static Mode from(String value) {
            for (Mode item : values()) {
                if (item.value.equals(value)) return item;
            }
            return null;
        }

        public static Map<String, String> toMap() {
            Map<String, String> result = new LinkedHashMap<>();
            for (Mode item : values()) {
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

    public static ErpActionResult localSuccess() {
        return new ErpActionResult(Status.SUCCEEDED, Mode.LOCAL_ONLY, null, "仅完成MES本地业务，无ERP外部副作用");
    }
}
