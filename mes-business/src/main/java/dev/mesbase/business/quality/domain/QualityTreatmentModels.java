package dev.mesbase.business.quality.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class QualityTreatmentModels {
    private QualityTreatmentModels() {
    }

    public enum Action {
        ASSESS, RETURN_NODE, TRANSFER_WAREHOUSE;
        private static final Map<String, Action> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

        @JsonValue
        public String getValue() {
            return name();
        }

        public static boolean recognizes(String value) {
            return value != null && BY_VALUE.containsKey(value);
        }

        public static Action from(String value) {
            var action = BY_VALUE.get(value);
            if (action == null) throw new FoundationException(ErrorCode.QUALITY_DATA_INVALID);
            return action;
        }
    }

    public enum State {
        UNVERIFIED, OVERLAP, PENDING, VOIDED, RETURNED, TRANSFERRED, DISPOSED, UNRESOLVED;

        private static final Map<String, State> BY_VALUE = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(State::getValue, Function.identity()));

        @JsonValue
        public String getValue() {
            return name();
        }

        public static State from(String value) {
            var state = BY_VALUE.get(value);
            if (state == null) throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            return state;
        }
    }

    public enum Attribution {
        INDEPENDENT, OVERLAP, UNVERIFIED;

        public static Attribution find(String value) {
            for (var attribution : values())
                if (attribution.getValue().equals(value)) return attribution;
            return null;
        }

        @JsonValue
        public String getValue() {
            return name();
        }
    }

    public enum Destination {RETURN_NODE, WAREHOUSE}

    public record Disposition(String requestId, int expectedRevision, Destination destination,
                              BigDecimal quantity, Long warehouseId, String reason) {
    }

    public record Assessment(Attribution type, String relatedDeclarationId, String basis) {
    }

    public record AssessmentCommand(String requestId, int expectedRevision, String reason, Assessment assessment) {
    }

    public record Summary(String declarationId, int revision, Long executionId, Long routingNodeId,
                          BigDecimal defectQty, Attribution attribution, String relatedDeclarationId, String basis,
                          BigDecimal pendingDispositionQty, BigDecimal netDefectQty, State state,
                          String recordAction, List<MesQualityTreatmentEntity> events,
                          BigDecimal returnedQty, BigDecimal transferredQty) {
    }
}
