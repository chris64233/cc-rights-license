package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Requests {

    private Requests() {
    }

    public record CreateWorkRequest(
            @NotBlank String code,
            @NotBlank String title) {
    }

    public record AddHolderRequest(
            @NotBlank String name,
            @NotNull BigDecimal sharePercent) {
    }

    public record CreateApplicationRequest(
            @NotBlank String licensee,
            @NotNull LicenseType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty List<String> territories,
            @NotEmpty List<String> media) {
    }

    public record SubmitDecisionRequest(
            @NotNull Long holderId,
            @NotNull DecisionValue decision,
            @NotBlank String eventNumber) {
    }
}
