package dev.mesbase.business.integration.warehouse;

import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand.Source;

public record QueryMaterialIssueCommand(Long issueId, String operationId, String bindingKey, String externalId,
                                        String externalCode, Source source) {
    public QueryMaterialIssueCommand(Long issueId, String operationId, String bindingKey, String externalId,
                                     String externalCode) {
        this(issueId, operationId, bindingKey, externalId, externalCode, null);
    }
}
