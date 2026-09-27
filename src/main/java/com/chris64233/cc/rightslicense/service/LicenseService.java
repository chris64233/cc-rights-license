package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.GrantVersion;
import com.chris64233.cc.rightslicense.domain.LicenseApplication;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.RightsDecision;
import com.chris64233.cc.rightslicense.domain.RightsHolder;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.repo.GrantVersionRepository;
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
    private final GrantVersionRepository grantVersionRepository;

    public LicenseService(WorkRepository workRepository,
                          RightsHolderRepository holderRepository,
                          LicenseApplicationRepository applicationRepository,
                          RightsDecisionRepository decisionRepository,
                          LicenseGrantRepository grantRepository,
                          GrantVersionRepository grantVersionRepository) {
        this.workRepository = workRepository;
        this.holderRepository = holderRepository;
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.grantRepository = grantRepository;
        this.grantVersionRepository = grantVersionRepository;
    }

    @Transactional
    public LicenseApplication createApplication(String workCode, String licensee, LicenseType type,
                                                LocalDate startDate, LocalDate endDate,
                                                List<String> territories, List<String> media) {
        return createApplication(workCode, licensee, type, startDate, endDate,
                territories, media, SublicensePolicy.forbidden());
    }

    @Transactional
    public LicenseApplication createApplication(String workCode, String licensee, LicenseType type,
                                                LocalDate startDate, LocalDate endDate,
                                                List<String> territories, List<String> media,
                                                SublicensePolicy policy) {
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
        SublicensePolicy cleanPolicy = normalizePolicy(policy, startDate, endDate,
                cleanTerritories, cleanMedia);
        BigDecimal totalShares = holderRepository.sumSharesByWorkId(work.getId());
        if (totalShares.compareTo(WorkService.HUNDRED) != 0) {
            throw BusinessException.unprocessable(
                    "作品权利人份额之和必须精确等于 100%，当前为 "
                            + totalShares.stripTrailingZeros().toPlainString() + "%");
        }
        return applicationRepository.save(new LicenseApplication(
                work, licensee, type, startDate, endDate, cleanTerritories, cleanMedia, cleanPolicy));
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
        // 根授权之间做冲突检测；下级转授权必然落在某条根授权范围内，由该根授权代表
        List<LicenseGrant> overlapping = grantRepository.findOverlapping(
                work.getId(), application.getStartDate(), application.getEndDate(), blockingTypes).stream()
                .filter(LicenseGrant::isRoot)
                .toList();

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
            LicenseGrant grant = new LicenseGrant(application);
            grantRepository.save(grant);
            // id 生成后补齐含自身的层级链
            grant.initRootChain();
            // 固化 v1 版本快照
            saveVersionSnapshot(grant, "初始授权");
            application.markApproved();
        }
    }

    private void saveVersionSnapshot(LicenseGrant grant, String reason) {
        grantVersionRepository.save(new GrantVersion(
                grant, grant.getCurrentVersion(), grant.getType(),
                grant.getStartDate(), grant.getEndDate(),
                grant.getTerritories(), grant.getMedia(),
                grant.isSublicensable(), grant.getMaxDepth(),
                grant.getSubTerritories(), grant.getSubMedia(),
                grant.getSubStartDate(), grant.getSubEndDate(), reason));
    }

    @Transactional(readOnly = true)
    public LicenseApplication getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("授权申请不存在: " + applicationId));
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
                application.getStartDate(), application.getEndDate(), blockingTypes).stream()
                .filter(LicenseGrant::isRoot)
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
     * 规范化根授权的转授权声明：
     * 最大层级必须 ≥ 2（至少允许一层转授权才有意义）；声明的转授范围必须落在本级授权范围内。
     * 未显式给出转授范围时，默认上限即本级范围。
     */
    private static SublicensePolicy normalizePolicy(SublicensePolicy policy,
                                                    LocalDate startDate, LocalDate endDate,
                                                    List<String> territories, List<String> media) {
        if (policy == null || !policy.sublicensable()) {
            return SublicensePolicy.forbidden();
        }
        Integer maxDepth = policy.maxDepth();
        if (maxDepth == null) {
            throw BusinessException.badRequest("允许转授权时必须声明最大层级");
        }
        if (maxDepth < 2) {
            throw BusinessException.badRequest("最大层级至少为 2（本级为第 1 层，直接下级为第 2 层）");
        }
        List<String> subTerritories = policy.subTerritories() == null
                ? territories : normalizeScope(policy.subTerritories(), "可转授地域");
        List<String> subMedia = policy.subMedia() == null
                ? media : normalizeScope(policy.subMedia(), "可转授媒介");
        LocalDate subStart = policy.subStartDate() == null ? startDate : policy.subStartDate();
        LocalDate subEnd = policy.subEndDate() == null ? endDate : policy.subEndDate();
        if (subStart.isBefore(startDate) || subEnd.isAfter(endDate) || subStart.isAfter(subEnd)) {
            throw BusinessException.badRequest("可转授期限必须落在授权期限内");
        }
        if (!new LinkedHashSet<>(territories).containsAll(new LinkedHashSet<>(subTerritories))) {
            throw BusinessException.badRequest("可转授地域不能超出授权地域");
        }
        if (!new LinkedHashSet<>(media).containsAll(new LinkedHashSet<>(subMedia))) {
            throw BusinessException.badRequest("可转授媒介不能超出授权媒介");
        }
        return new SublicensePolicy(true, maxDepth, subTerritories, subMedia, subStart, subEnd);
    }
}
