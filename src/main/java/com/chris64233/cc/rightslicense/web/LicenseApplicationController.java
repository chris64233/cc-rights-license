package com.chris64233.cc.rightslicense.web;

import com.chris64233.cc.rightslicense.service.ConflictDetail;
import com.chris64233.cc.rightslicense.service.LicenseService;
import com.chris64233.cc.rightslicense.web.dto.ApplicationResponse;
import com.chris64233.cc.rightslicense.web.dto.DecisionRequest;
import com.chris64233.cc.rightslicense.web.dto.ProgressResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class LicenseApplicationController {

    private final LicenseService licenseService;

    public LicenseApplicationController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @GetMapping("/{applicationId}")
    public ApplicationResponse getApplication(@PathVariable Long applicationId) {
        return licenseService.getApplication(applicationId);
    }

    @GetMapping("/{applicationId}/progress")
    public ProgressResponse getProgress(@PathVariable Long applicationId) {
        return licenseService.getProgress(applicationId);
    }

    @GetMapping("/{applicationId}/conflicts")
    public List<ConflictDetail> getConflicts(@PathVariable Long applicationId) {
        return licenseService.getConflicts(applicationId);
    }

    @PostMapping("/{applicationId}/decisions")
    public ProgressResponse recordDecision(@PathVariable Long applicationId,
                                           @Valid @RequestBody DecisionRequest request) {
        return licenseService.recordDecision(applicationId, request);
    }
}
