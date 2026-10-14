package dev.mesbase.business.integration.warehouse;

public record MaterialIssueCancelCommand(Long issueId, String operationId, String bindingKey,
                                         String externalReference) {
}
