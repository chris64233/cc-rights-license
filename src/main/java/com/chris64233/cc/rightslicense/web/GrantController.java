package com.chris64233.cc.rightslicense.web;

import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.service.CascadeService;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import com.chris64233.cc.rightslicense.web.dto.Requests.ReduceScopeRequest;
import com.chris64233.cc.rightslicense.web.dto.Responses.CascadeTaskResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.EffectiveScopeResponse;
import com.chris64233.cc.rightslicense.web.dto.Responses.GrantResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class GrantController {

    private final SublicenseService sublicenseService;
    private final CascadeService cascadeService;

    public GrantController(SublicenseService sublicenseService,
                           CascadeService cascadeService) {
        this.sublicenseService = sublicenseService;
        this.cascadeService = cascadeService;
    }

    @GetMapping("/api/grants/{id}")
    public GrantResponse get(@PathVariable Long id) {
        return GrantResponse.of(sublicenseService.getGrant(id));
    }

    /** 权利树：以该授权为根的全部下级（含自身，按层级排序）。 */
    @GetMapping("/api/grants/{id}/tree")
    public List<GrantResponse> tree(@PathVariable Long id) {
        return sublicenseService.getRightsTree(id).stream()
                .map(GrantResponse::of).toList();
    }

    /** 直接下级授权。 */
    @GetMapping("/api/grants/{id}/children")
    public List<GrantResponse> children(@PathVariable Long id) {
        return sublicenseService.listChildren(id).stream()
                .map(GrantResponse::of).toList();
    }

    /** 有效范围：沿层级链逐层求交后的实际有效地域、媒介、期限。 */
    @GetMapping("/api/grants/{id}/effective-scope")
    public EffectiveScopeResponse effectiveScope(@PathVariable Long id) {
        return EffectiveScopeResponse.of(sublicenseService.getEffectiveScope(id));
    }

    /** 撤销授权：自身终止，全部下级级联终止。 */
    @PostMapping("/api/grants/{id}/revoke")
    public GrantResponse revoke(@PathVariable Long id) {
        LicenseGrant grant = sublicenseService.revokeGrant(id);
        return GrantResponse.of(grant);
    }

    /** 缩减授权范围：越界下级级联暂停。 */
    @PostMapping("/api/grants/{id}/reduce-scope")
    public GrantResponse reduceScope(@PathVariable Long id,
                                     @Valid @RequestBody ReduceScopeRequest request) {
        LicenseGrant grant = sublicenseService.reduceScope(
                id, request.startDate(), request.endDate(),
                request.territories(), request.media());
        return GrantResponse.of(grant);
    }

    /** 立即到期（管理入口）：下级级联暂停或到期。 */
    @PostMapping("/api/grants/{id}/expire")
    public GrantResponse expire(@PathVariable Long id) {
        return GrantResponse.of(sublicenseService.expireGrant(id));
    }

    /** 级联传播任务（可重试、不遗漏的处理凭证）。 */
    @GetMapping("/api/cascade/tasks")
    public List<CascadeTaskResponse> cascadeTasks() {
        return cascadeService.listTasks().stream()
                .map(CascadeTaskResponse::of).toList();
    }

    /** 手动触发一轮级联任务处理（重试入口）。 */
    @PostMapping("/api/cascade/process")
    public List<CascadeTaskResponse> processCascade() {
        cascadeService.processPending();
        return cascadeService.listTasks().stream()
                .map(CascadeTaskResponse::of).toList();
    }
}
