package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
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

    /** 转授权策略声明；为空或 sublicensable=false 表示不允许转授权。 */
    public record SublicensePolicyRequest(
            Boolean sublicensable,
            Integer maxLevels,
            List<String> territories,
            List<String> media,
            LocalDate startBound,
            LocalDate endBound) {

        public SublicensePolicy toPolicy() {
            return new SublicensePolicy(
                    Boolean.TRUE.equals(sublicensable),
                    maxLevels,
                    territories != null ? territories : List.of(),
                    media != null ? media : List.of(),
                    startBound,
                    endBound);
        }
    }

    public record CreateApplicationRequest(
            @NotBlank String licensee,
            @NotNull LicenseType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty List<String> territories,
            @NotEmpty List<String> media,
            SublicensePolicyRequest sublicensePolicy) {
    }

    public record SubmitDecisionRequest(
            @NotNull Long holderId,
            @NotNull DecisionValue decision,
            @NotBlank String eventNumber) {
    }

    public record CreateSublicenseApplicationRequest(
            @NotBlank String sublicenseNumber,
            @NotBlank String applicant,
            @NotBlank String sublicensee,
            @NotNull LicenseType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty List<String> territories,
            @NotEmpty List<String> media,
            SublicensePolicyRequest sublicensePolicy) {
    }

    public record SubmitSublicenseDecisionRequest(
            @NotNull DecisionValue decision,
            @NotBlank String eventNumber) {
    }

    public record ReduceScopeRequest(
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotEmpty List<String> territories,
            @NotEmpty List<String> media) {
    }
}
