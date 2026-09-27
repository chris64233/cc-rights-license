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
            @NotEmpty List<String> media,
            SublicensePolicyRequest sublicense) {
    }

    public record SubmitDecisionRequest(
            @NotNull Long holderId,
            @NotNull DecisionValue decision,
            @NotBlank String eventNumber) {
    }

    /** 根授权或下级授权中"是否允许继续转授权"的声明 */
    public record SublicensePolicyRequest(
            @NotNull Boolean allowed,
            Integer maxDepth,
            List<String> territories,
            List<String> media,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record CreateSublicenseRequest(
            @NotBlank String sublicenseNo,
            @NotBlank String parentGrantNo,
            @NotBlank String licensee,
            @NotNull LicenseType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty List<String> territories,
            @NotEmpty List<String> media,
            SublicensePolicyRequest sublicense) {
    }

    public record SublicenseDecisionRequest(
            @NotBlank String decider,
            @NotNull DecisionValue decision,
            @NotBlank String eventNumber) {
    }

    public record LifecycleEventRequest(@NotBlank String eventNo) {
    }

    public record ReduceScopeRequest(
            @NotBlank String eventNo,
            LocalDate startDate,
            LocalDate endDate,
            List<String> territories,
            List<String> media) {
    }
}
