package dev.mesbase.business.quality.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Processing;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class QualityTreatmentModels {
    private QualityTreatmentModels() {
    }

    public enum RequestKind {
        ASSESS("assess", "核实原请求"), DISPOSE("dispose", "入仓原请求");
        private final String value;
        private final String description;

        RequestKind(String value, String description) {
            this.value = value;
            this.description = description;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static RequestKind from(String value) {
            for (var kind : values()) if (kind.value.equals(value)) return kind;
            return null;
        }

        @JsonCreator
        public static RequestKind parse(String value) {
            var kind = from(value);
            if (kind == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
            return kind;
        }

        public static Map<String, String> toMap() {
            var result = new LinkedHashMap<String, String>();
            for (var kind : values()) result.put(kind.value, kind.description);
            return result;
        }
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

    public enum ReceiptState {
        PENDING("待入仓", "PENDING"), PARTIAL("部分入仓", "PARTIAL"), RECEIVED("已入仓", "RECEIVED");

        private final String description;
        private final String value;

        ReceiptState(String description, String value) {
            this.description = description;
            this.value = value;
        }

        public String getDescription() {
            return description;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        public static ReceiptState from(String value) {
            for (var state : values()) if (state.getValue().equals(value)) return state;
            return null;
        }

        public static Map<String, String> toMap() {
            var result = new LinkedHashMap<String, String>();
            for (var state : values()) result.put(state.getValue(), state.getDescription());
            return result;
        }
    }

    public record ReceiptProof(String eventId, long validCount) {
    }

    /**
     * Historical returns reduce the pending balance but never count as warehouse receipts.
     */
    public static ReceiptState receiptState(State state, BigDecimal declared, BigDecimal transferred) {
        if (state == null || state == State.UNRESOLVED || state == State.VOIDED
                || state == State.UNVERIFIED || state == State.OVERLAP || transferred == null) return null;
        if (transferred.signum() == 0) return ReceiptState.PENDING;
        if (transferred.compareTo(declared) == 0) return ReceiptState.RECEIVED;
        return ReceiptState.PARTIAL;
    }

    public record Declaration(Long id, String declarationId, Integer revision, String executionCode,
                              Long routingNodeId, BigDecimal defectQty, String recordAction) {
    }

    public record Event(String id, String declarationId, Integer revision, String executionCode,
                        Long routingNodeId, String action, String attribution, String relatedDeclarationId,
                        String basis, BigDecimal returnedQty, BigDecimal transferredQty, String ownerDeclarationId) {
    }

    /**
     * Incremental interpretation of raw facts; the reader can page arbitrarily long event histories.
     */
    public static final class FactBuilder {
        private final Declaration declaration;
        private long assessments, invalid, unknown;
        private String attribution, related, basis;
        private BigDecimal returned = BigDecimal.ZERO, transferred = BigDecimal.ZERO;

        public FactBuilder(Declaration declaration) {
            this.declaration = declaration;
        }

        public void accept(Event event) {
            if (!Objects.equals(declaration.declarationId(), event.declarationId())
                    || !Objects.equals(declaration.revision(), event.revision())
                    || declaration.executionCode() == null || !Objects.equals(declaration.executionCode(), event.executionCode())
                    || declaration.routingNodeId() == null || !Objects.equals(declaration.routingNodeId(), event.routingNodeId()))
                invalid++;
            if (!Action.recognizes(event.action())) {
                unknown++;
                invalid++;
                return;
            }
            if (event.returnedQty() == null || event.transferredQty() == null) {
                invalid++;
                return;
            }
            if (event.returnedQty().signum() < 0 || event.transferredQty().signum() < 0) invalid++;
            switch (Action.from(event.action())) {
                case ASSESS -> {
                    assessments++;
                    attribution = event.attribution();
                    related = event.relatedDeclarationId();
                    basis = event.basis();
                    var type = Attribution.find(attribution);
                    if (type == null || type == Attribution.UNVERIFIED || basis == null || basis.isBlank()
                            || type == Attribution.INDEPENDENT && related != null
                            || type == Attribution.OVERLAP && (related == null || related.equals(declaration.declarationId()))
                            || event.returnedQty().signum() != 0 || event.transferredQty().signum() != 0) invalid++;
                }
                case RETURN_NODE -> {
                    returned = returned.add(event.returnedQty());
                    if (event.returnedQty().signum() <= 0 || event.transferredQty().signum() != 0) invalid++;
                }
                case TRANSFER_WAREHOUSE -> {
                    transferred = transferred.add(event.transferredQty());
                    if (event.transferredQty().signum() <= 0 || event.returnedQty().signum() != 0) invalid++;
                }
            }
        }

        public CurrentFact build(Map<String, CurrentFact> relatedFacts) {
            long errors = invalid;
            if (assessments == 1 && Attribution.OVERLAP.getValue().equals(attribution)) {
                var target = relatedFacts.get(related);
                if (target == null || target.summary(false).state() == State.UNRESOLVED
                        || target.summary(false).state() == State.VOIDED
                        || target.summary(false).attribution() != Attribution.INDEPENDENT
                        || !Objects.equals(target.executionCode(), declaration.executionCode())
                        || declaration.defectQty() == null || target.defectQty().compareTo(declaration.defectQty()) < 0)
                    errors++;
            }
            int revision = 0;
            if (declaration.revision() != null) revision = declaration.revision();
            if (declaration.executionCode() == null || declaration.routingNodeId() == null) errors++;
            return new CurrentFact(declaration.declarationId(), revision, declaration.executionCode(), declaration.routingNodeId(),
                    declaration.defectQty(), declaration.recordAction(), assessments, attribution, related, basis,
                    returned, transferred, errors, unknown);
        }

        public String relatedDeclarationId() {
            return related;
        }
    }

    /**
     * One latest declaration and its corresponding version facts, never display history.
     */
    public record CurrentFact(String declarationId, int revision, String executionCode, Long routingNodeId,
                              BigDecimal defectQty, String recordAction, long assessmentCount,
                              String attribution, String relatedDeclarationId, String basis,
                              BigDecimal returnedQty, BigDecimal transferredQty, long invalidFacts,
                              long unknownActions) {
        public Summary summary(boolean strict) {
            Attribution type = null;
            String related = null;
            String assessmentBasis = null;
            if (assessmentCount == 0) type = Attribution.UNVERIFIED;
            if (assessmentCount == 1) {
                type = Attribution.find(attribution);
                related = relatedDeclarationId;
                assessmentBasis = basis;
            }
            long invalid = invalidFacts + unknownActions;
            if (revision < 1) invalid++;
            if (assessmentCount > 1 || assessmentCount == 1 && type == Attribution.UNVERIFIED) invalid++;
            var processing = Processing.read(recordAction, type, defectQty,
                    returnedQty, transferredQty, invalid);
            if (strict && unknownActions > 0) throw new FoundationException(ErrorCode.QUALITY_DATA_INVALID);
            if (strict && processing.state() == State.UNRESOLVED)
                throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT);
            BigDecimal net = null;
            if (processing.state() != State.UNRESOLVED)
                net = processing.state() != State.VOIDED && type == Attribution.INDEPENDENT ? defectQty.subtract(returnedQty) : BigDecimal.ZERO;
            return new Summary(declarationId, revision, executionCode, routingNodeId, defectQty, type,
                    related, assessmentBasis, processing.pendingDispositionQty(), net, processing.state(),
                    recordAction, processing.returnedQty(), processing.transferredQty(),
                    receiptState(processing.state(), defectQty, processing.transferredQty()));
        }
    }

    public record Disposition(String requestId, int expectedRevision, Destination destination,
                              BigDecimal quantity, Long warehouseId, String reason) {
    }

    public record Assessment(Attribution type, String relatedDeclarationId, String basis) {
    }

    public record AssessmentCommand(String requestId, int expectedRevision, String reason, Assessment assessment) {
    }

    public record Summary(String declarationId, int revision, String executionCode, Long routingNodeId,
                          BigDecimal defectQty, Attribution attribution, String relatedDeclarationId, String basis,
                          BigDecimal pendingDispositionQty, BigDecimal netDefectQty, State state,
                          String recordAction,
                          BigDecimal returnedQty, BigDecimal transferredQty, ReceiptState receiptState) {
    }
}
