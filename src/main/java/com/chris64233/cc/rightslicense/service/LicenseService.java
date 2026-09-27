package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsDecision;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.repo.LicenseApplicationRepository;
import com.chris64233.cc.rightslicense.repo.LicenseGrantRepository;
import com.chris64233.cc.rightslicense.repo.RightsDecisionRepository;
import com.chris64233.cc.rightslicense.repo.RightsHolderRepository;
import com.chris64233.cc.rightslicense.repo.WorkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class LicenseService {

    private final WorkRepository workRepository;
    private final RightsHolderRepository holderRepository;
    private final LicenseApplicationRepository applicationRepository;
    private final RightsDecisionRepository decisionRepository;
    private final LicenseGrantRepository grantRepository;

    public LicenseService(WorkRepository workRepository,
                          RightsHolderRepository holderRepository,
                          LicenseApplicationRepository applicationRepository,
                          RightsDecisionRepository decisionRepository,
                          LicenseGrantRepository grantRepository) {
        this.workRepository = workRepository;
        this.holderRepository = holderRepository;
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.grantRepository = grantRepository;
    }

    @Transactional
    public LicenseApplication createApplication(String workCode, String licensee, LicenseType type,
                                                LocalDate startDate, LocalDate endDate,
                                                List<String> territories, List<String> media) {
        return createApplication(workCode, licensee, type, startDate, endDate,
                territories, media, null);
    }

    @Transactional
    public LicenseApplication createApplication(String workCode, String licensee, LicenseType type,
                                                LocalDate startDate, LocalDate endDate,
                                                List<String> territories, List<String> media,
                                                SublicensePolicy sublicensePolicy) {
        Work work = workRepository.findByCode(workCode)
                .orElseThrow(() -> BusinessException.notFound("作品不存在: " + workCode));
        if (licensee == null || licensee.isBlank()) {
            throw BusinessException.badRequest("被授权方不能为空");
        }
        if (type == null) {
            throw BusinessException.badRequest("授权类型不能为空");
        }
        validateDates(startDate, endDate);
        List<String> cleanTerritories = normalizeScope(territories, "地域");
        List<String> cleanMedia = normalizeScope(media, "媒介");
        SublicensePolicy cleanPolicy = normalizePolicy(sublicensePolicy,
                startDate, endDate, cleanTerritories, cleanMedia);
        BigDecimal totalShares = holderRepository.sumSharesByWorkId(work.getId());
        if (totalShares.compareTo(WorkService.HUNDRED) != 0) {
            throw BusinessException.unprocessable(
                    "作品权利人份额之和必须精确等于 100%，当前为 "
                            + totalShares.stripTrailingZeros().toPlainString() + "%");
        }
        LicenseApplication application = new LicenseApplication(
                work, licensee, type, startDate, endDate, cleanTerritories, cleanMedia);
        application.applySublicensePolicy(cleanPolicy);
        return applicationRepository.save(application);
    }

    @Transactional
    public RightsDecision submitDecision(Long applicationId, Long holderId,
                                         DecisionValue decisionValue, String eventNumber) {
        if (decisionValue == null) {
            throw BusinessException.badRequest("决定不能为空");
        }
        if (eventNumber == null || eventNumber.isBlank()) {
            throw BusinessException.badRequest("事件号不能为空");
        }
        LicenseApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> BusinessException.notFound("授权申请不存在: " + applicationId));

        var existingByEvent = decisionRepository
                .findByApplicationIdAndEventNumber(application.getId(), eventNumber);
        if (existingByEvent.isPresent()) {
            return existingByEvent.get();
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw BusinessException.conflict(
                    "申请已终结（" + application.getStatus() + "），无法再提交决定");
        }
        if (decisionRepository.existsByApplicationIdAndHolderId(application.getId(), holderId)) {
            throw BusinessException.conflict("权利人已对该申请作出决定，决定不可修改");
        }
        RightsHolder holder = holderRepository.findById(holderId)
                .orElseThrow(() -> BusinessException.notFound("权利人不存在: " + holderId));
        if (!holder.getWork().getId().equals(application.getWork().getId())) {
            throw BusinessException.badRequest("该权利人不是本作品的权利人");
        }

        RightsDecision decision = decisionRepository.save(
                new RightsDecision(application, holder, decisionValue, eventNumber));

        if (decisionValue == DecisionValue.REJECT) {
            application.markRejected();
        } else {
            BigDecimal approved = decisionRepository.sumSharesByApplicationIdAndDecision(
                    application.getId(), DecisionValue.APPROVE);
            if (approved.compareTo(WorkService.HUNDRED) == 0) {
                finalizeApproval(application);
            }
        }
        return decision;
    }

    private void finalizeApproval(LicenseApplication application) {
        Work work = workRepository.findByIdForUpdate(application.getWork().getId())
                .orElseThrow(() -> BusinessException.notFound("作品不存在"));

        Set<LicenseType> blockingTypes = application.getType() == LicenseType.EXCLUSIVE
                ? Set.of(LicenseType.EXCLUSIVE, LicenseType.NON_EXCLUSIVE)
                : Set.of(LicenseType.EXCLUSIVE);
        List<LicenseGrant> overlapping = grantRepository.findOverlapping(
                work.getId(), application.getStartDate(), application.getEndDate(), blockingTypes,
                GrantStatus.ACTIVE);

        List<String> conflicts = new ArrayList<>();
        for (LicenseGrant grant : overlapping) {
            Set<String> territories = intersect(application.getTerritories(), grant.getTerritories());
            Set<String> media = intersect(application.getMedia(), grant.getMedia());
            if (!territories.isEmpty() && !media.isEmpty()) {
                conflicts.add("与已批准授权#" + grant.getId()
                        + "（" + grant.getType() + "，" + grant.getStartDate() + " ~ " + grant.getEndDate()
                        + "，被授权方 " + grant.getLicensee() + "）在地域 " + territories
                        + " × 媒介 " + media + " 上重叠");
            }
        }

        if (!conflicts.isEmpty()) {
            application.markConflict(String.join("；", conflicts));
        } else {
            LicenseGrant grant = grantRepository.save(
                    new LicenseGrant(application, application.toSublicensePolicy()));
            grant.initHierarchy();
            application.markApproved();
        }
    }

    @Transactional(readOnly = true)
    public LicenseApplication getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("授权申请不存在: " + applicationId));
    }

    @Transactional(readOnly = true)
    public LicenseGrant getGrantByApplicationId(Long applicationId) {
        return grantRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> BusinessException.notFound(
                        "申请尚未生成授权（申请状态非 APPROVED）: " + applicationId));
    }

    @Transactional(readOnly = true)
    public List<LicenseGrant> getCalendar(String workCode, LocalDate from, LocalDate to) {
        Work work = workRepository.findByCode(workCode)
                .orElseThrow(() -> BusinessException.notFound("作品不存在: " + workCode));
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(9999, 12, 31);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw BusinessException.badRequest("查询起始日期不能晚于结束日期");
        }
        return grantRepository.findCalendarEntries(work.getId(), effectiveFrom, effectiveTo);
    }

    @Transactional(readOnly = true)
    public List<LicenseGrant> findConflictingGrants(Long applicationId) {
        LicenseApplication application = getApplication(applicationId);
        Set<LicenseType> blockingTypes = application.getType() == LicenseType.EXCLUSIVE
                ? Set.of(LicenseType.EXCLUSIVE, LicenseType.NON_EXCLUSIVE)
                : Set.of(LicenseType.EXCLUSIVE);
        return grantRepository.findOverlapping(application.getWork().getId(),
                application.getStartDate(), application.getEndDate(), blockingTypes,
                GrantStatus.ACTIVE).stream()
                .filter(grant -> !grant.getApplication().getId().equals(application.getId()))
                .filter(grant -> !intersect(application.getTerritories(), grant.getTerritories()).isEmpty()
                        && !intersect(application.getMedia(), grant.getMedia()).isEmpty())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RightsDecision> listDecisions(Long applicationId) {
        getApplication(applicationId);
        return decisionRepository.findByApplicationIdOrderById(applicationId);
    }

    @Transactional(readOnly = true)
    public List<RightsHolder> listHolders(Long workId) {
        return holderRepository.findByWorkIdOrderById(workId);
    }

    @Transactional(readOnly = true)
    public BigDecimal approvedShare(Long applicationId) {
        return decisionRepository.sumSharesByApplicationIdAndDecision(applicationId, DecisionValue.APPROVE);
    }

    private static Set<String> intersect(List<String> left, List<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.retainAll(new LinkedHashSet<>(right));
        return result;
    }

    private static void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw BusinessException.badRequest("起止日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw BusinessException.badRequest("起始日期不能晚于结束日期");
        }
    }

    private static List<String> normalizeScope(List<String> values, String label) {
        if (values == null || values.isEmpty()) {
            throw BusinessException.badRequest(label + "至少需要一个");
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw BusinessException.badRequest(label + "不能为空");
            }
            cleaned.add(value.trim());
        }
        return List.copyOf(cleaned);
    }

    /**
     * 校验并规整转授权策略：
     * 策略声明的地域/媒介必须是授权范围的子集，期限边界必须落在授权期限内；
     * 允许转授权时层级数必须为正整数。
     */
    static SublicensePolicy normalizePolicy(SublicensePolicy policy,
                                            LocalDate grantStart, LocalDate grantEnd,
                                            List<String> grantTerritories,
                                            List<String> grantMedia) {
        if (policy == null || !policy.sublicensable()) {
            return SublicensePolicy.disabled();
        }
        if (policy.maxLevels() != null && policy.maxLevels() <= 0) {
            throw BusinessException.badRequest("允许转授权层级必须为正整数");
        }
        List<String> territories = policy.territories();
        List<String> media = policy.media();
        List<String> missingTerritories = ScopeRules.notContained(territories, grantTerritories);
        if (!missingTerritories.isEmpty()) {
            throw BusinessException.badRequest("转授权地域超出授权范围: " + missingTerritories);
        }
        List<String> missingMedia = ScopeRules.notContained(media, grantMedia);
        if (!missingMedia.isEmpty()) {
            throw BusinessException.badRequest("转授权媒介超出授权范围: " + missingMedia);
        }
        LocalDate startBound = policy.startBound() != null ? policy.startBound() : grantStart;
        LocalDate endBound = policy.endBound() != null ? policy.endBound() : grantEnd;
        if (startBound.isBefore(grantStart) || endBound.isAfter(grantEnd)
                || startBound.isAfter(endBound)) {
            throw BusinessException.badRequest("转授权期限范围必须落在授权期限内");
        }
        return new SublicensePolicy(true, policy.maxLevels(),
                List.copyOf(territories), List.copyOf(media), startBound, endBound);
    }
}
