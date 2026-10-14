package dev.mesbase.business.issue.controller;

import dev.mesbase.business.issue.controller.vo.MesMaterialIssueVo;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@Validated
@ConditionalOnProperty(name="mes.business.enabled",havingValue="true")
public class MaterialIssueSubmissionController {
    private final MaterialIssueSubmissionService service;
    public MaterialIssueSubmissionController(MaterialIssueSubmissionService service) {this.service=service;}
    @PostMapping("/api/issue/mes-material-issue/{id}/actions/submit")
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="business:mes:material:issue:submit")
    public MesMaterialIssueVo submit(@PathVariable @NotNull Long id) {return MesMaterialIssueController.toView(service.submit(id));}
    @PostMapping("/api/issue/mes-material-issue/{id}/actions/reopen-rejected")
    @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="business:mes:material:issue:submit")
    public MesMaterialIssueVo reopen(@PathVariable @NotNull Long id) {return MesMaterialIssueController.toView(service.reopenRejected(id));}
}
