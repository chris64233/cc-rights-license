package com.chris64233.cc.rightslicense.web;

import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicenseDecision;
import com.chris64233.cc.rightslicense.service.GrantLifecycleService;
import com.chris64233.cc.rightslicense.service.RightsTreeQueryService;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import com.chris64233.cc.rightslicense.web.dto.Requests.CreateSublicenseRequest;
import com.chris64233.cc.rightslicense.web.dto.Requests.LifecycleEventRequest;
import com.chris64233.cc.rightslicense.web.dto.Requests.ReduceScopeRequest;
import com.chris64233.cc.rightslicense.web.dto.Requests.SublicenseDecisionRequest;
import com.chris64233.cc.rightslicense.web.dto.Responses.GrantResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.SublicenseApplicationResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.SublicenseConflictResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.SublicenseDecisionResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.TreeNodeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 受控转授权、授权生命周期与权利树查询接口。
 */
@RestController
public class GrantController {

    private final SublicenseService sublicenseService;
    private final GrantLifecycleService lifecycleService;
    private final RightsTreeQueryService treeQueryService;

    public GrantController(SublicenseService sublicenseService,
                           GrantLifecycleService lifecycleService,
                           RightsTreeQueryService treeQueryService) {
        this.sublicenseService = sublicenseService;
        this.lifecycleService = lifecycleService;
        this.treeQueryService = treeQueryService;
    }

    // ---- 转授权申请与审批 ----

    @PostMapping("/api/sublicenses")
    @ResponseStatus(HttpStatus.CREATED)
    public SublicenseApplicationResponse createSublicense(
            @Valid @RequestBody CreateSublicenseRequest request) {
        SublicenseApplication application = sublicenseService.createApplication(
                request.parentGrantNo(), request.sublicenseNo(),
                request.licensee(), request.type(),
                request.startDate(), request.endDate(),
                request.territories(), request.media(),
                PolicyMapper.toDomain(request.sublicense()));
        return SublicenseApplicationResponse.of(application);
    }

    @GetMapping("/api/sublicenses/{id}")
    public SublicenseApplicationResponse getSublicense(@PathVariable Long id) {
        return SublicenseApplicationResponse.of(sublicenseService.getApplication(id));
    }

    @GetMapping("/api/sublicenses/by-no/{sublicenseNo}")
    public SublicenseApplicationResponse getSublicenseByNo(@PathVariable String sublicenseNo) {
        return SublicenseApplicationResponse.of(sublicenseService.getApplicationByNo(sublicenseNo));
    }

    @PostMapping("/api/sublicenses/{id}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public SublicenseDecisionResponse submitSublicenseDecision(
            @PathVariable Long id, @Valid @RequestBody SublicenseDecisionRequest request) {
        SublicenseDecision decision = sublicenseService.submitDecision(
                id, request.decider(), request.decision(), request.eventNumber());
        return SublicenseDecisionResponse.of(decision);
    }

    @GetMapping("/api/sublicenses/{id}/conflicts")
    public SublicenseConflictResponse getSublicenseConflicts(@PathVariable Long id) {
        SublicenseApplication application = sublicenseService.getApplication(id);
        List<GrantResponse> conflicts = sublicenseService.findConflictingChildren(id).stream()
                .map(GrantResponse::of).toList();
        return new SublicenseConflictResponse(application.getId(), application.getStatus(),
                application.getConflictReason(), conflicts);
    }

    // ---- 授权生命周期 ----

    @PostMapping("/api/grants/{grantNo}/revoke")
    public GrantResponse revoke(@PathVariable String grantNo,
                                @Valid @RequestBody LifecycleEventRequest request) {
        return GrantResponse.of(lifecycleService.revoke(grantNo, request.eventNo()));
    }

    @PostMapping("/api/grants/{grantNo}/suspend")
    public GrantResponse suspend(@PathVariable String grantNo,
                                 @Valid @RequestBody LifecycleEventRequest request) {
        return GrantResponse.of(lifecycleService.suspend(grantNo, request.eventNo()));
    }

    @PostMapping("/api/grants/{grantNo}/resume")
    public GrantResponse resume(@PathVariable String grantNo,
                                @Valid @RequestBody LifecycleEventRequest request) {
        return GrantResponse.of(lifecycleService.resume(grantNo, request.eventNo()));
    }

    @PostMapping("/api/grants/{grantNo}/reduce-scope")
    public GrantResponse reduceScope(@PathVariable String grantNo,
                                     @Valid @RequestBody ReduceScopeRequest request) {
        return GrantResponse.of(lifecycleService.reduceScope(grantNo, request.eventNo(),
                request.startDate(), request.endDate(),
                request.territories(), request.media()));
    }

    @PostMapping("/api/grants/expire-scan")
    public Map<String, Integer> expireScan(@RequestParam(required = false) LocalDate today) {
        LocalDate effective = today != null ? today : LocalDate.now();
        return Map.of("expired", lifecycleService.expireDueGrants(effective, "EXPIRE-" + effective));
    }

    @PostMapping("/api/grants/propagations/retry")
    public Map<String, Integer> retryPropagations() {
        return Map.of("applied", lifecycleService.retryPendingPropagations());
    }

    @GetMapping("/api/grants/propagations/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("pending", lifecycleService.pendingPropagationCount());
    }

    // ---- 权利树与有效范围查询 ----

    @GetMapping("/api/grants/{grantNo}")
    public GrantResponse getGrant(@PathVariable String grantNo) {
        return GrantResponse.of(treeQueryService.getGrant(grantNo));
    }

    @GetMapping("/api/grants/{grantNo}/children")
    public List<GrantResponse> children(@PathVariable String grantNo) {
        return treeQueryService.getChildren(grantNo).stream().map(GrantResponse::of).toList();
    }

    @GetMapping("/api/grants/{grantNo}/subtree")
    public List<GrantResponse> subtree(@PathVariable String grantNo) {
        return treeQueryService.getSubtree(grantNo).stream().map(GrantResponse::of).toList();
    }

    @GetMapping("/api/grants/{grantNo}/tree")
    public TreeNodeResponse tree(@PathVariable String grantNo) {
        List<LicenseGrant> nodes = treeQueryService.getSubtree(grantNo);
        Map<Long, List<LicenseGrant>> byParent = new LinkedHashMap<>();
        Map<Long, LicenseGrant> byId = new LinkedHashMap<>();
        for (LicenseGrant node : nodes) {
            byId.put(node.getId(), node);
            byParent.computeIfAbsent(node.getParent() == null ? null : node.getParent().getId(),
                    k -> new ArrayList<>()).add(node);
        }
        return buildTree(nodes.get(0), byParent);
    }

    @GetMapping("/api/grants/{grantNo}/chain")
    public List<GrantResponse> chain(@PathVariable String grantNo) {
        return treeQueryService.getChain(grantNo).stream().map(GrantResponse::of).toList();
    }

    @GetMapping("/api/grants/{grantNo}/descendants")
    public List<GrantResponse> descendantsByStatus(@PathVariable String grantNo,
                                                   @RequestParam(defaultValue = "ACTIVE") GrantStatus status) {
        return treeQueryService.getDescendantsByStatus(grantNo, status).stream()
                .map(GrantResponse::of).toList();
    }

    private TreeNodeResponse buildTree(LicenseGrant node, Map<Long, List<LicenseGrant>> byParent) {
        List<TreeNodeResponse> children = byParent
                .getOrDefault(node.getId(), List.of()).stream()
                .map(child -> buildTree(child, byParent))
                .toList();
        return new TreeNodeResponse(GrantResponse.of(node), children);
    }
}
