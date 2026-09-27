package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.CascadeTask;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsDecision;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicenseDecision;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.SublicenseService;

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

    public record PolicyResponse(boolean sublicensable, Integer maxLevels,
                                 List<String> territories, List<String> media,
                                 LocalDate startBound, LocalDate endBound) {
    }

    public record ApplicationResponse(Long id, String workCode, String licensee, LicenseType type,
                                      LocalDate startDate, LocalDate endDate,
                                      List<String> territories, List<String> media,
                                      ApplicationStatus status, String conflictReason,
                                      PolicyResponse sublicensePolicy) {
        public static ApplicationResponse of(LicenseApplication application) {
            var policy = application.toSublicensePolicy();
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
                    application.getConflictReason(),
                    new PolicyResponse(policy.sublicensable(), policy.maxLevels(),
                            List.copyOf(policy.territories()), List.copyOf(policy.media()),
                            policy.startBound(), policy.endBound()));
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

    public record GrantResponse(Long id, Long applicationId, Long sublicenseApplicationId,
                                Long parentGrantId, Long rootGrantId, Integer depth, String chainPath,
                                String licensee, LicenseType type,
                                LocalDate startDate, LocalDate endDate,
                                List<String> territories, List<String> media,
                                GrantStatus status, String statusReason, long version,
                                PolicyResponse sublicensePolicy,
                                Instant grantedAt) {
        public static GrantResponse of(LicenseGrant grant) {
            return new GrantResponse(
                    grant.getId(),
                    grant.getApplication() != null ? grant.getApplication().getId() : null,
                    grant.getSublicenseApplication() != null
                            ? grant.getSublicenseApplication().getId() : null,
                    grant.getParentGrant() != null ? grant.getParentGrant().getId() : null,
                    grant.getRootGrantId(),
                    grant.getDepth(),
                    grant.getChainPath(),
                    grant.getLicensee(),
                    grant.getType(),
                    grant.getStartDate(),
                    grant.getEndDate(),
                    List.copyOf(grant.getTerritories()),
                    List.copyOf(grant.getMedia()),
                    grant.getStatus(),
                    grant.getStatusReason(),
                    grant.getVersion(),
                    new PolicyResponse(
                            grant.isSublicensable(),
                            grant.getMaxSublicenseDepth() != null
                                    ? grant.getMaxSublicenseDepth() - grant.getDepth() : null,
                            List.copyOf(grant.getSublicensableTerritories()),
                            List.copyOf(grant.getSublicensableMedia()),
                            grant.getSublicenseStartBound(),
                            grant.getSublicenseEndBound()),
                    grant.getGrantedAt());
        }
    }

    public record SublicenseApplicationResponse(Long id, String sublicenseNumber,
                                                Long parentGrantId, String parentLicensee,
                                                String applicant, String sublicensee,
                                                LicenseType type,
                                                LocalDate startDate, LocalDate endDate,
                                                List<String> territories, List<String> media,
                                                com.chris64233.cc.rightslicense.domain.SublicenseStatus status,
                                                String conflictReason,
                                                long parentVersionAtCreation,
                                                PolicyResponse sublicensePolicy,
                                                Instant createdAt) {
        public static SublicenseApplicationResponse of(SublicenseApplication application) {
            var policy = application.toSublicensePolicy();
            return new SublicenseApplicationResponse(
                    application.getId(),
                    application.getSublicenseNumber(),
                    application.getParentGrant().getId(),
                    application.getParentGrant().getLicensee(),
                    application.getApplicant(),
                    application.getSublicensee(),
                    application.getType(),
                    application.getStartDate(),
                    application.getEndDate(),
                    List.copyOf(application.getTerritories()),
                    List.copyOf(application.getMedia()),
                    application.getStatus(),
                    application.getConflictReason(),
                    application.getParentVersion(),
                    new PolicyResponse(policy.sublicensable(), policy.maxLevels(),
                            List.copyOf(policy.territories()), List.copyOf(policy.media()),
                            policy.startBound(), policy.endBound()),
                    application.getCreatedAt());
        }
    }

    public record SublicenseDecisionResponse(Long id, Long applicationId,
                                             DecisionValue decision, String eventNumber,
                                             Instant decidedAt,
                                             com.chris64233.cc.rightslicense.domain.SublicenseStatus applicationStatus) {
        public static SublicenseDecisionResponse of(SublicenseDecision decision) {
            return new SublicenseDecisionResponse(
                    decision.getId(),
                    decision.getApplication().getId(),
                    decision.getDecision(),
                    decision.getEventNumber(),
                    decision.getDecidedAt(),
                    decision.getApplication().getStatus());
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

    public record SublicenseConflictResponse(Long applicationId,
                                             com.chris64233.cc.rightslicense.domain.SublicenseStatus status,
                                             String conflictReason,
                                             List<GrantResponse> conflictingGrants) {
    }

    public record EffectiveScopeResponse(Long grantId, boolean effective, String reason,
                                         LocalDate startDate, LocalDate endDate,
                                         List<String> territories, List<String> media) {
        public static EffectiveScopeResponse of(SublicenseService.EffectiveScope scope) {
            return new EffectiveScopeResponse(scope.grantId(), scope.effective(), scope.reason(),
                    scope.startDate(), scope.endDate(),
                    scope.territories(), scope.media());
        }
    }

    public record CascadeTaskResponse(Long id, Long targetGrantId, Long sourceGrantId,
                                      String eventType, String status,
                                      int attempts, String lastError,
                                      String detail, Instant createdAt, Instant processedAt) {
        public static CascadeTaskResponse of(CascadeTask task) {
            return new CascadeTaskResponse(
                    task.getId(),
                    task.getTargetGrant().getId(),
                    task.getSourceGrant().getId(),
                    task.getEventType().name(),
                    task.getStatus().name(),
                    task.getAttempts(),
                    task.getLastError(),
                    task.getDetail(),
                    task.getCreatedAt(),
                    task.getProcessedAt());
        }
    }

    public record ErrorResponse(int status, String message) {
    }
}
