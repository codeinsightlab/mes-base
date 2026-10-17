package dev.mesbase.business.integration.warehouse;

import dev.mesbase.business.integration.warehouse.MaterialIssueSubmitCommand.Source;

public record MaterialIssueCancelCommand(Long issueId, String operationId, String bindingKey, String externalReference,
                                         Source source) {
    public MaterialIssueCancelCommand(Long issueId, String operationId, String bindingKey, String externalReference) {
        this(issueId, operationId, bindingKey, externalReference, null);
    }
}
