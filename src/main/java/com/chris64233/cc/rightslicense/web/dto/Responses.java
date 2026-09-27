package com.chris64233.cc.rightslicense.web.dto;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.HaltReason;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsDecision;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicenseDecision;
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

    public record PolicyResponse(boolean allowed, Integer maxDepth,
                                 List<String> territories, List<String> media,
                                 LocalDate startDate, LocalDate endDate) {
    }

    public record ApplicationResponse(Long id, String workCode, String licensee, LicenseType type,
                                      LocalDate startDate, LocalDate endDate,
                                      List<String> territories, List<String> media,
                                      ApplicationStatus status, String conflictReason,
                                      PolicyResponse sublicense) {
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
                    application.getConflictReason(),
                    new PolicyResponse(application.isSublicensable(), application.getMaxDepth(),
                            List.copyOf(application.getSubTerritories()),
                            List.copyOf(application.getSubMedia()),
                            application.getSubStartDate(), application.getSubEndDate()));
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

    public record GrantResponse(Long id, String grantNo, Long applicationId,
                                String parentGrantNo, int depth, List<Long> chain,
                                String licensee, LicenseType type,
                                LocalDate startDate, LocalDate endDate,
                                List<String> territories, List<String> media,
                                Instant grantedAt, Integer currentVersion,
                                GrantStatus status, HaltReason haltReason,
                                PolicyResponse sublicense) {
        public static GrantResponse of(LicenseGrant grant) {
            String parentNo = grant.getParent() == null ? null : grant.getParent().getGrantNo();
            return new GrantResponse(
                    grant.getId(),
                    grant.getGrantNo(),
                    grant.getApplication() == null ? null : grant.getApplication().getId(),
                    parentNo,
                    grant.getDepth(),
                    List.copyOf(grant.getChain()),
                    grant.getLicensee(),
                    grant.getType(),
                    grant.getStartDate(),
                    grant.getEndDate(),
                    List.copyOf(grant.getTerritories()),
                    List.copyOf(grant.getMedia()),
                    grant.getCreatedAt(),
                    grant.getCurrentVersion(),
                    grant.getStatus(),
                    grant.getHaltReason(),
                    new PolicyResponse(grant.isSublicensable(), grant.getMaxDepth(),
                            List.copyOf(grant.getSubTerritories()),
                            List.copyOf(grant.getSubMedia()),
                            grant.getSubStartDate(), grant.getSubEndDate()));
        }
    }

    public record SublicenseApplicationResponse(Long id, String sublicenseNo,
                                                String parentGrantNo, int parentVersion,
                                                String applicant, String licensee, LicenseType type,
                                                LocalDate startDate, LocalDate endDate,
                                                List<String> territories, List<String> media,
                                                ApplicationStatus status, String conflictReason,
                                                String childGrantNo,
                                                PolicyResponse sublicense) {
        public static SublicenseApplicationResponse of(SublicenseApplication a) {
            return new SublicenseApplicationResponse(
                    a.getId(), a.getSublicenseNo(),
                    a.getParentGrant().getGrantNo(), a.getParentVersion(),
                    a.getApplicant(), a.getLicensee(), a.getType(),
                    a.getStartDate(), a.getEndDate(),
                    List.copyOf(a.getTerritories()), List.copyOf(a.getMedia()),
                    a.getStatus(), a.getConflictReason(),
                    a.getChildGrant() == null ? null : a.getChildGrant().getGrantNo(),
                    new PolicyResponse(a.isSublicensable(), a.getMaxDepth(),
                            List.copyOf(a.getSubTerritories()), List.copyOf(a.getSubMedia()),
                            a.getSubStartDate(), a.getSubEndDate()));
        }
    }

    public record SublicenseDecisionResponse(Long id, Long applicationId, int parentVersion,
                                             String decider, DecisionValue decision,
                                             String eventNumber, Instant decidedAt,
                                             ApplicationStatus applicationStatus) {
        public static SublicenseDecisionResponse of(SublicenseDecision d) {
            return new SublicenseDecisionResponse(
                    d.getId(), d.getApplication().getId(), d.getParentVersion(),
                    d.getApplicant(), d.getDecision(), d.getEventNumber(), d.getDecidedAt(),
                    d.getApplication().getStatus());
        }
    }

    public record TreeNodeResponse(GrantResponse grant, List<TreeNodeResponse> children) {
    }

    public record SublicenseConflictResponse(Long applicationId, ApplicationStatus status,
                                             String conflictReason,
                                             List<GrantResponse> conflictingGrants) {
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
