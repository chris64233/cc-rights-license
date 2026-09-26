package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsDecision;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.Work;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class Responses {

    private Responses() {
    }

    public record WorkResponse(Long id, String code, String title) {
        public static WorkResponse of(Work work) {
            return new WorkResponse(work.getId(), work.getCode(), work.getTitle());
        }
    }

    public record HolderResponse(Long id, String name, BigDecimal sharePercent) {
        public static HolderResponse of(RightsHolder holder) {
            return new HolderResponse(holder.getId(), holder.getName(), holder.getSharePercent());
        }
    }

    public record ApplicationResponse(Long id, String workCode, String licensee, LicenseType type,
                                      LocalDate startDate, LocalDate endDate,
                                      List<String> territories, List<String> media,
                                      ApplicationStatus status, String conflictReason) {
        public static ApplicationResponse of(LicenseApplication application) {
            return new ApplicationResponse(
                    application.getId(),
                    application.getWork().getCode(),
                    application.getLicensee(),
                    application.getType(),
                    application.getStartDate(),
                    application.getEndDate(),
                    List.copyOf(application.getTerritories()),
                    List.copyOf(application.getMedia()),
                    application.getStatus(),
                    application.getConflictReason());
        }
    }

    public record DecisionResponse(Long id, Long applicationId, Long holderId, String holderName,
                                   DecisionValue decision, String eventNumber, Instant decidedAt,
                                   ApplicationStatus applicationStatus) {
        public static DecisionResponse of(RightsDecision decision) {
            return new DecisionResponse(
                    decision.getId(),
                    decision.getApplication().getId(),
                    decision.getHolder().getId(),
                    decision.getHolder().getName(),
                    decision.getDecision(),
                    decision.getEventNumber(),
                    decision.getDecidedAt(),
                    decision.getApplication().getStatus());
        }
    }

    public record GrantResponse(Long id, Long applicationId, String licensee, LicenseType type,
                                LocalDate startDate, LocalDate endDate,
                                List<String> territories, List<String> media, Instant grantedAt) {
        public static GrantResponse of(LicenseGrant grant) {
            return new GrantResponse(
                    grant.getId(),
                    grant.getApplication().getId(),
                    grant.getLicensee(),
                    grant.getType(),
                    grant.getStartDate(),
                    grant.getEndDate(),
                    List.copyOf(grant.getTerritories()),
                    List.copyOf(grant.getMedia()),
                    grant.getGrantedAt());
        }
    }

    public record ProgressResponse(Long applicationId, ApplicationStatus status,
                                   BigDecimal approvedSharePercent,
                                   List<DecisionResponse> decisions,
                                   List<HolderResponse> pendingHolders) {
    }

    public record ConflictResponse(Long applicationId, ApplicationStatus status,
                                   String conflictReason,
                                   List<GrantResponse> conflictingGrants) {
    }

    public record ErrorResponse(int status, String message) {
    }
}
