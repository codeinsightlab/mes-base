package dev.mesbase.business.integration.warehouse;

public record QueryMaterialIssueCommand(Long issueId, String operationId, String bindingKey,
                                        String externalId, String externalCode) {
}
