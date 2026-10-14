package dev.mesbase.business.integration.warehouse;

/**
 * Current local administrative creation; connected issue submission requires a reviewed item payload/use case.
 */
public record MaterialIssueCreateCommand(Long issueId, String operationId, String issueNo,
                                         Long workorderId, Long executionId) {
}
