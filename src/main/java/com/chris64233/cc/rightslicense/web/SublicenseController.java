package com.chris64233.cc.rightslicense.web;

import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicenseDecision;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import com.chris64233.cc.rightslicense.web.dto.Requests.CreateSublicenseApplicationRequest;
import com.chris64233.cc.rightslicense.web.dto.Requests.SubmitSublicenseDecisionRequest;
import com.chris64233.cc.rightslicense.web.dto.Responses.GrantResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.SublicenseApplicationResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.SublicenseConflictResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.SublicenseDecisionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SublicenseController {

    private final SublicenseService sublicenseService;

    public SublicenseController(SublicenseService sublicenseService) {
        this.sublicenseService = sublicenseService;
    }

    /** 创建转授权申请（转授权号 sublicenseNumber 保证幂等）。 */
    @PostMapping("/api/grants/{parentGrantId}/sublicenses")
    @ResponseStatus(HttpStatus.CREATED)
    public SublicenseApplicationResponse create(
            @PathVariable Long parentGrantId,
            @Valid @RequestBody CreateSublicenseApplicationRequest request) {
        SublicenseApplication application = sublicenseService.createApplication(
                request.sublicenseNumber(), parentGrantId,
                request.applicant(), request.sublicensee(),
                request.type(), request.startDate(), request.endDate(),
                request.territories(), request.media(),
                request.sublicensePolicy() != null
                        ? request.sublicensePolicy().toPolicy() : null);
        return SublicenseApplicationResponse.of(application);
    }

    @GetMapping("/api/sublicenses/{id}")
    public SublicenseApplicationResponse get(@PathVariable Long id) {
        return SublicenseApplicationResponse.of(sublicenseService.getApplication(id));
    }

    @PostMapping("/api/sublicenses/{id}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public SublicenseDecisionResponse decide(
            @PathVariable Long id,
            @Valid @RequestBody SubmitSublicenseDecisionRequest request) {
        SublicenseDecision decision = sublicenseService.submitDecision(
                id, request.decision(), request.eventNumber());
        return SublicenseDecisionResponse.of(decision);
    }

    @GetMapping("/api/sublicenses/{id}/decisions")
    public java.util.List<SublicenseDecisionResponse> listDecisions(@PathVariable Long id) {
        return sublicenseService.listDecisions(id).stream()
                .map(SublicenseDecisionResponse::of)
                .toList();
    }

    @GetMapping("/api/sublicenses/{id}/conflicts")
    public SublicenseConflictResponse conflicts(@PathVariable Long id) {
        SublicenseApplication application = sublicenseService.getApplication(id);
        return new SublicenseConflictResponse(
                application.getId(), application.getStatus(), application.getConflictReason(),
                sublicenseService.findConflictingGrants(id).stream()
                        .map(GrantResponse::of).toList());
    }
}
