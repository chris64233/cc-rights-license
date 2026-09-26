package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;

import java.math.BigDecimal;
import java.util.List;

public record ProgressResponse(
        Long applicationId,
        ApplicationStatus status,
        BigDecimal approvedSharePercent,
        BigDecimal remainingSharePercent,
        List<DecisionInfo> decisions,
        String conflictReason) {
}
