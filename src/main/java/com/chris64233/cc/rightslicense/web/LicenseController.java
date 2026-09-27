package com.chris64233.cc.rightslicense.web;

import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.RightsDecision;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.service.LicenseService;
import com.chris64233.cc.rightslicense.web.dto.Requests.CreateApplicationRequest;
import com.chris64233.cc.rightslicense.web.dto.Requests.SubmitDecisionRequest;
import com.chris64233.cc.rightslicense.web.dto.Responses.ApplicationResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.ConflictResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.DecisionResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.GrantResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.HolderResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.ProgressResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class LicenseController {

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @PostMapping("/api/works/{code}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse createApplication(@PathVariable String code,
                                                 @Valid @RequestBody CreateApplicationRequest request) {
        LicenseApplication application = licenseService.createApplication(
                code, request.licensee(), request.type(),
                request.startDate(), request.endDate(),
                request.territories(), request.media(),
                PolicyMapper.toDomain(request.sublicense()));
        return ApplicationResponse.of(application);
    }

    @GetMapping("/api/applications/{id}")
    public ApplicationResponse getApplication(@PathVariable Long id) {
        return ApplicationResponse.of(licenseService.getApplication(id));
    }

    @PostMapping("/api/applications/{id}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public DecisionResponse submitDecision(@PathVariable Long id,
                                           @Valid @RequestBody SubmitDecisionRequest request) {
        RightsDecision decision = licenseService.submitDecision(
                id, request.holderId(), request.decision(), request.eventNumber());
        return DecisionResponse.of(decision);
    }

    @GetMapping("/api/applications/{id}/progress")
    public ProgressResponse getProgress(@PathVariable Long id) {
        LicenseApplication application = licenseService.getApplication(id);
        List<RightsDecision> decisions = licenseService.listDecisions(id);
        List<RightsHolder> holders = licenseService.listHolders(application.getWork().getId());
        Set<Long> decidedHolderIds = decisions.stream()
                .map(d -> d.getHolder().getId())
                .collect(Collectors.toSet());
        List<HolderResponse> pending = holders.stream()
                .filter(h -> !decidedHolderIds.contains(h.getId()))
                .map(HolderResponse::of)
                .toList();
        return new ProgressResponse(
                application.getId(),
                application.getStatus(),
                licenseService.approvedShare(id),
                decisions.stream().map(DecisionResponse::of).toList(),
                pending);
    }

    @GetMapping("/api/applications/{id}/conflicts")
    public ConflictResponse getConflicts(@PathVariable Long id) {
        LicenseApplication application = licenseService.getApplication(id);
        List<GrantResponse> conflicting = licenseService.findConflictingGrants(id).stream()
                .map(GrantResponse::of)
                .toList();
        return new ConflictResponse(application.getId(), application.getStatus(),
                application.getConflictReason(), conflicting);
    }
}
