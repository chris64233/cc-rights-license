package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionType;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightHolderDecision;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.domain.WorkRightHolder;
import com.chris64233.cc.rightslicense.repository.LicenseApplicationRepository;
import com.chris64233.cc.rightslicense.repository.LicenseGrantRepository;
import com.chris64233.cc.rightslicense.repository.RightHolderDecisionRepository;
import com.chris64233.cc.rightslicense.repository.WorkRepository;
import com.chris64233.cc.rightslicense.web.dto.ApplicationResponse;
import com.chris64233.cc.rightslicense.web.dto.CalendarEntryResponse;
import com.chris64233.cc.rightslicense.web.dto.CreateApplicationRequest;
import com.chris64233.cc.rightslicense.web.dto.DecisionInfo;
import com.chris64233.cc.rightslicense.web.dto.DecisionRequest;
import com.chris64233.cc.rightslicense.web.dto.ProgressResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LicenseService {

    private final WorkRepository workRepository;
    private final LicenseApplicationRepository applicationRepository;
    private final LicenseGrantRepository grantRepository;
    private final RightHolderDecisionRepository decisionRepository;

    public LicenseService(WorkRepository workRepository,
                          LicenseApplicationRepository applicationRepository,
                          LicenseGrantRepository grantRepository,
                          RightHolderDecisionRepository decisionRepository) {
        this.workRepository = workRepository;
        this.applicationRepository = applicationRepository;
        this.grantRepository = grantRepository;
        this.decisionRepository = decisionRepository;
    }

    @Transactional
    public ApplicationResponse createApplication(String workCode, CreateApplicationRequest request) {
        Work work = workRepository.findByWorkCode(workCode)
                .orElseThrow(() -> BusinessException.notFound("作品不存在: " + workCode));
        if (request.endDate().isBefore(request.startDate())) {
            throw BusinessException.badRequest("结束日期不能早于开始日期");
        }
        LicenseApplication application = new LicenseApplication(
                work, request.licensee(), request.licenseType(),
                request.startDate(), request.endDate(),
                request.territories(), request.media());
        application = applicationRepository.save(application);
        return toApplicationResponse(application);
    }

    @Transactional
    public ProgressResponse recordDecision(Long applicationId, DecisionRequest request) {
        var existing = decisionRepository.findByEventId(request.eventId());
        if (existing.isPresent()) {
            RightHolderDecision prior = existing.get();
            if (!prior.getApplication().getId().equals(applicationId)) {
                throw BusinessException.conflict("事件号已被其他申请使用: " + request.eventId());
            }
            return buildProgress(prior.getApplication());
        }

        LicenseApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> BusinessException.notFound("申请不存在: " + applicationId));
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw BusinessException.conflict("申请已结束，当前状态: " + application.getStatus());
        }

        Work work = application.getWork();
        Map<String, WorkRightHolder> holdersByName = work.getRightHolders().stream()
                .collect(Collectors.toMap(WorkRightHolder::getHolderName, Function.identity()));
        WorkRightHolder holder = holdersByName.get(request.holderName());
        if (holder == null) {
            throw BusinessException.badRequest("不是该作品的权利人: " + request.holderName());
        }
        if (decisionRepository.existsByApplicationIdAndHolderName(applicationId, request.holderName())) {
            throw BusinessException.conflict("权利人已作出决定，不可重复或修改: " + request.holderName());
        }

        RightHolderDecision decision = new RightHolderDecision(
                application, request.holderName(), request.decision(), request.eventId());
        try {
            decisionRepository.saveAndFlush(decision);
        } catch (DataIntegrityViolationException e) {
            throw BusinessException.conflict("决定记录冲突：权利人已决定或事件号已使用");
        }

        if (request.decision() == DecisionType.REJECT) {
            application.setStatus(ApplicationStatus.REJECTED);
        } else {
            BigDecimal approvedShare = approvedShare(application, holdersByName);
            if (approvedShare.compareTo(WorkService.HUNDRED) >= 0) {
                finalizeApproval(application, work);
            }
        }
        return buildProgress(application);
    }

    private void finalizeApproval(LicenseApplication application, Work work) {
        // 对作品行加悲观写锁，串行化同一作品的并发批准，保证冲突申请最多一个成功
        workRepository.findByIdForUpdate(work.getId());
        List<LicenseGrant> grants = grantRepository.findByWorkIdOrderByStartDateAscIdAsc(work.getId());
        List<ConflictDetail> conflicts = findConflicts(
                application.getLicenseType(), application.getTerritories(), application.getMedia(),
                application.getStartDate(), application.getEndDate(), grants, null);
        if (!conflicts.isEmpty()) {
            application.setStatus(ApplicationStatus.CONFLICT);
            application.setConflictReason(buildConflictReason(conflicts));
        } else {
            grantRepository.save(new LicenseGrant(application));
            application.setStatus(ApplicationStatus.APPROVED);
        }
    }

    @Transactional(readOnly = true)
    public ProgressResponse getProgress(Long applicationId) {
        LicenseApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("申请不存在: " + applicationId));
        return buildProgress(application);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getApplication(Long applicationId) {
        LicenseApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("申请不存在: " + applicationId));
        return toApplicationResponse(application);
    }

    @Transactional(readOnly = true)
    public List<ConflictDetail> getConflicts(Long applicationId) {
        LicenseApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("申请不存在: " + applicationId));
        List<LicenseGrant> grants = grantRepository.findByWorkIdOrderByStartDateAscIdAsc(
                application.getWork().getId());
        return findConflicts(
                application.getLicenseType(), application.getTerritories(), application.getMedia(),
                application.getStartDate(), application.getEndDate(), grants, applicationId);
    }

    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> getCalendar(String workCode) {
        Work work = workRepository.findByWorkCode(workCode)
                .orElseThrow(() -> BusinessException.notFound("作品不存在: " + workCode));
        return grantRepository.findByWorkIdOrderByStartDateAscIdAsc(work.getId()).stream()
                .map(grant -> new CalendarEntryResponse(
                        grant.getId(), grant.getApplication().getId(), grant.getLicensee(),
                        grant.getLicenseType(), grant.getTerritories(), grant.getMedia(),
                        grant.getStartDate(), grant.getEndDate()))
                .toList();
    }

    static List<ConflictDetail> findConflicts(LicenseType candidateType,
                                              Set<String> candidateTerritories,
                                              Set<String> candidateMedia,
                                              LocalDate startDate, LocalDate endDate,
                                              List<LicenseGrant> grants,
                                              Long excludeApplicationId) {
        List<ConflictDetail> conflicts = new ArrayList<>();
        for (LicenseGrant grant : grants) {
            if (excludeApplicationId != null && excludeApplicationId.equals(grant.getApplication().getId())) {
                continue;
            }
            if (!datesOverlap(startDate, endDate, grant.getStartDate(), grant.getEndDate())) {
                continue;
            }
            Set<String> territoryOverlap = intersect(candidateTerritories, grant.getTerritories());
            Set<String> mediaOverlap = intersect(candidateMedia, grant.getMedia());
            if (territoryOverlap.isEmpty() || mediaOverlap.isEmpty()) {
                continue;
            }
            if (candidateType == LicenseType.EXCLUSIVE || grant.getLicenseType() == LicenseType.EXCLUSIVE) {
                conflicts.add(new ConflictDetail(
                        grant.getId(), grant.getLicensee(), grant.getLicenseType(),
                        territoryOverlap, mediaOverlap, grant.getStartDate(), grant.getEndDate()));
            }
        }
        return conflicts;
    }

    static boolean datesOverlap(LocalDate startA, LocalDate endA, LocalDate startB, LocalDate endB) {
        return !startA.isAfter(endB) && !startB.isAfter(endA);
    }

    private static Set<String> intersect(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private static String buildConflictReason(List<ConflictDetail> conflicts) {
        return conflicts.stream()
                .map(c -> "与已批准授权 #%d（被授权方=%s，类型=%s，地域=%s，媒介=%s，期限=%s~%s）冲突".formatted(
                        c.grantId(), c.licensee(), c.licenseType(),
                        c.overlappingTerritories(), c.overlappingMedia(), c.startDate(), c.endDate()))
                .collect(Collectors.joining("；"));
    }

    private BigDecimal approvedShare(LicenseApplication application, Map<String, WorkRightHolder> holdersByName) {
        BigDecimal approved = BigDecimal.ZERO;
        for (RightHolderDecision decision : decisionRepository.findByApplicationIdOrderByIdAsc(application.getId())) {
            if (decision.getDecision() == DecisionType.APPROVE) {
                approved = approved.add(holdersByName.get(decision.getHolderName()).getSharePercent());
            }
        }
        return approved;
    }

    private ProgressResponse buildProgress(LicenseApplication application) {
        List<RightHolderDecision> decisions = decisionRepository.findByApplicationIdOrderByIdAsc(application.getId());
        Map<String, BigDecimal> shares = application.getWork().getRightHolders().stream()
                .collect(Collectors.toMap(WorkRightHolder::getHolderName, WorkRightHolder::getSharePercent));
        BigDecimal approved = BigDecimal.ZERO;
        List<DecisionInfo> infos = new ArrayList<>();
        for (RightHolderDecision decision : decisions) {
            if (decision.getDecision() == DecisionType.APPROVE) {
                approved = approved.add(shares.get(decision.getHolderName()));
            }
            infos.add(new DecisionInfo(decision.getHolderName(), decision.getDecision(),
                    decision.getEventId(), decision.getDecidedAt()));
        }
        BigDecimal remaining = WorkService.HUNDRED.subtract(approved).max(BigDecimal.ZERO);
        return new ProgressResponse(application.getId(), application.getStatus(),
                approved, remaining, infos, application.getConflictReason());
    }

    private ApplicationResponse toApplicationResponse(LicenseApplication application) {
        return new ApplicationResponse(
                application.getId(), application.getWork().getWorkCode(), application.getLicensee(),
                application.getLicenseType(), application.getTerritories(), application.getMedia(),
                application.getStartDate(), application.getEndDate(),
                application.getStatus(), application.getConflictReason());
    }
}
