package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.DecisionType;

import java.time.Instant;

public record DecisionInfo(
        String holderName,
        DecisionType decision,
        String eventId,
        Instant decidedAt) {
}
