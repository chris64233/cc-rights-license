package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.DecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DecisionRequest(
        @NotBlank String holderName,
        @NotNull DecisionType decision,
        @NotBlank String eventId) {
}
