package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.GrantVersion;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicenseDecision;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.repo.GrantVersionRepository;
import com.chris64233.cc.rightslicense.repo.LicenseGrantRepository;
import com.chris64233.cc.rightslicense.repo.SublicenseApplicationRepository;
import com.chris64233.cc.rightslicense.repo.SublicenseDecisionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 受控转授权：申请创建、幂等审批决定、范围/层级/独占冲突的原子校验、下级授权生成。
 *
 * <p>并发安全：审批与上级状态变更都通过对上级授权行加悲观写锁（{@code findByIdForUpdate}）
 * 串行化，确保"新的下级申请"无法逃逸上级暂停/终止/范围缩减的限制。
 */
@Service
public class SublicenseService {

    private final LicenseGrantRepository grantRepository;
    private final SublicenseApplicationRepository applicationRepository;
    private final SublicenseDecisionRepository decisionRepository;
    private final GrantVersionRepository versionRepository;

    public SublicenseService(LicenseGrantRepository grantRepository,
                             SublicenseApplicationRepository applicationRepository,
                             SublicenseDecisionRepository decisionRepository,
                             GrantVersionRepository versionRepository) {
        this.grantRepository = grantRepository;
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.versionRepository = versionRepository;
    }

    // ------------------------------------------------------------------
    // 申请创建（幂等：同一转授权号重复提交返回首次申请）
    // ------------------------------------------------------------------

    @Transactional
    public SublicenseApplication createApplication(String parentGrantNo, String sublicenseNo,
                                                   String licensee, LicenseType type,
                                                   LocalDate startDate, LocalDate endDate,
                                                   List<String> territories, List<String> media,
                                                   SublicensePolicy requestedPolicy) {
        if (sublicenseNo == null || sublicenseNo.isBlank()) {
            throw BusinessException.badRequest("转授权号不能为空");
        }
        if (sublicenseNo.startsWith("G-")) {
            // G- 前缀保留给根授权号，避免与根授权号空间冲突
            throw BusinessException.badRequest("转授权号不能使用保留前缀 G-");
        }
        if (licensee == null || licensee.isBlank()) {
            throw BusinessException.badRequest("下级被授权方不能为空");
        }
        if (type == null) {
            throw BusinessException.badRequest("授权类型不能为空");
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw BusinessException.badRequest("起止日期不合法");
        }
        List<String> cleanTerritories = normalize(territories, "地域");
        List<String> cleanMedia = normalize(media, "媒介");
        SublicensePolicy cleanPolicy = normalizeRequestedPolicy(requestedPolicy);

        // 幂等：先查；并发时唯一约束兜底
        SublicenseApplication existing = applicationRepository.findBySublicenseNo(sublicenseNo).orElse(null);
        if (existing != null) {
            return existing;
        }

        LicenseGrant parent = grantRepository.findByGrantNoForUpdate(parentGrantNo)
                .orElseThrow(() -> BusinessException.notFound("上级授权不存在: " + parentGrantNo));

        List<String> violations = validateAgainstParent(parent, licensee, type,
                startDate, endDate, cleanTerritories, cleanMedia, cleanPolicy, new ArrayList<>());
        if (!violations.isEmpty()) {
            throw BusinessException.unprocessable(String.join("；", violations));
        }

        try {
            return applicationRepository.saveAndFlush(new SublicenseApplication(
                    sublicenseNo, parent.getWork(), parent, parent.getLicensee(),
                    licensee, type, startDate, endDate, cleanTerritories, cleanMedia, cleanPolicy));
        } catch (DataIntegrityViolationException dup) {
            // 并发下另一个事务已用相同转授权号落库：幂等返回
            return applicationRepository.findBySublicenseNo(sublicenseNo)
                    .orElseThrow(() -> dup);
        }
    }

    // ------------------------------------------------------------------
    // 审批决定（上级持权方；事件号幂等；批准时整份申请原子校验并生成下级授权）
    // ------------------------------------------------------------------

    @Transactional
    public SublicenseDecision submitDecision(Long applicationId, String decider,
                                             DecisionValue decisionValue, String eventNumber) {
        if (decisionValue == null) {
            throw BusinessException.badRequest("决定不能为空");
        }
        if (decider == null || decider.isBlank()) {
            throw BusinessException.badRequest("决定人不能为空");
        }
        if (eventNumber == null || eventNumber.isBlank()) {
            throw BusinessException.badRequest("事件号不能为空");
        }

        SublicenseApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> BusinessException.notFound("转授权申请不存在: " + applicationId));

        var byEvent = decisionRepository.findByApplicationIdAndEventNumber(applicationId, eventNumber);
        if (byEvent.isPresent()) {
            return byEvent.get();
        }
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw BusinessException.conflict(
                    "转授权申请已终结（" + application.getStatus() + "），无法再提交决定");
        }

        // 关键：锁定上级授权行，与上级的暂停/终止/范围缩减串行化
        LicenseGrant parent = grantRepository.findByIdForUpdate(application.getParentGrant().getId())
                .orElseThrow(() -> BusinessException.notFound("上级授权不存在"));

        if (decisionRepository.existsByApplicationIdAndApplicant(applicationId, decider)) {
            throw BusinessException.conflict("该决定人已对本申请作出决定，决定不可修改");
        }
        if (!parent.getLicensee().equals(decider)) {
            throw BusinessException.badRequest("只有上级授权当前被授权方才能审批转授权");
        }

        int decidedAtVersion = parent.getCurrentVersion();
        SublicenseDecision decision = new SublicenseDecision(
                application, decidedAtVersion, decider, decisionValue, eventNumber);
        decisionRepository.save(decision);

        if (decisionValue == DecisionValue.REJECT) {
            application.markRejected("被上级被授权方拒绝");
        } else {
            finalizeApproval(application, parent);
        }
        return decision;
    }

    /** 批准时对全部 地域 × 媒介 × 日期 组合做一次性原子校验，整体通过才生成下级授权 */
    private void finalizeApproval(SublicenseApplication application, LicenseGrant parent) {
        List<String> violations = new ArrayList<>();

        // 1) 申请方必须仍是当前有效被授权方，且上级仍有效
        if (parent.getStatus() != GrantStatus.ACTIVE) {
            violations.add("上级授权当前状态为 " + parent.getStatus() + "，不得转授权");
        }
        if (!parent.getLicensee().equals(application.getApplicant())) {
            violations.add("申请方已不是上级授权的当前被授权方");
        }
        if (parent.getCurrentVersion() != application.getParentVersion()) {
            violations.add("上级授权已由版本 " + application.getParentVersion()
                    + " 变更为 " + parent.getCurrentVersion() + "，需按新版本重新申请");
        }

        // 2) 范围/层级/独占（仅在前置状态正常时继续，避免连锁噪声）
        if (violations.isEmpty()) {
            validateAgainstParent(parent, application.getLicensee(), application.getType(),
                    application.getStartDate(), application.getEndDate(),
                    application.getTerritories(), application.getMedia(),
                    application.getRequestedPolicy(), violations);
            validateSiblingConflicts(parent, application, violations);
        }

        if (!violations.isEmpty()) {
            application.markConflict(String.join("；", violations));
            return;
        }

        // 3) 计算下级的有效转授权策略：上级声明与申请策略逐字段取交集，绝不放大
        SublicensePolicy effectivePolicy = intersectPolicies(parent, application.getRequestedPolicy());

        LicenseGrant child = new LicenseGrant(application, parent, effectivePolicy);
        grantRepository.save(child);
        child.initChildChain();
        versionRepository.save(new GrantVersion(
                child, 1, child.getType(), child.getStartDate(), child.getEndDate(),
                child.getTerritories(), child.getMedia(),
                child.isSublicensable(), child.getMaxDepth(),
                child.getSubTerritories(), child.getSubMedia(),
                child.getSubStartDate(), child.getSubEndDate(), "转授权生效"));
        application.markApproved(child);
    }

    // ------------------------------------------------------------------
    // 申请相对上级授权的完整校验（创建时与批准时共用，保证两次校验口径一致）
    // ------------------------------------------------------------------

    private List<String> validateAgainstParent(LicenseGrant parent, String licensee, LicenseType type,
                                               LocalDate startDate, LocalDate endDate,
                                               List<String> territories, List<String> media,
                                               SublicensePolicy requestedPolicy,
                                               List<String> violations) {
        if (parent.getStatus() != GrantStatus.ACTIVE) {
            violations.add("上级授权当前状态为 " + parent.getStatus() + "，不得转授权");
        }
        if (parent.getLicensee().equals(licensee)) {
            violations.add("下级被授权方不能与上级被授权方相同");
        }
        if (!parent.isSublicensable()) {
            violations.add("上级授权未声明允许转授权");
        }

        // 层级：子级绝对深度不能超过上级声明的最大深度
        int childDepth = parent.getDepth() + 1;
        if (parent.getMaxDepth() != null && childDepth > parent.getMaxDepth()) {
            violations.add("超出上级允许的最大层级 " + parent.getMaxDepth()
                    + "（下级将处于第 " + childDepth + " 层）");
        }

        // 核心范围必须落在上级授权自身范围内
        if (!ScopeRules.datesWithin(startDate, endDate, parent.getStartDate(), parent.getEndDate())) {
            violations.add("转授期限 " + startDate + " ~ " + endDate
                    + " 超出上级授权期限 " + parent.getStartDate() + " ~ " + parent.getEndDate());
        }
        if (!ScopeRules.containsAll(parent.getTerritories(), territories)) {
            violations.add("转授地域 " + territories + " 超出上级授权地域 " + parent.getTerritories());
        }
        if (!ScopeRules.containsAll(parent.getMedia(), media)) {
            violations.add("转授媒介 " + media + " 超出上级授权媒介 " + parent.getMedia());
        }

        // 非独占上级不能授予超出自身权利的独占范围
        if (parent.getType() == LicenseType.NON_EXCLUSIVE && type == LicenseType.EXCLUSIVE) {
            violations.add("上级为非独占授权，不得向下授予独占授权");
        }

        // 申请范围还必须落在上级"允许向下转授"的声明范围内（期限/地域/媒介上限）
        LocalDate envelopeStart = parent.getSubStartDate() != null
                ? parent.getSubStartDate() : parent.getStartDate();
        LocalDate envelopeEnd = parent.getSubEndDate() != null
                ? parent.getSubEndDate() : parent.getEndDate();
        if (!ScopeRules.datesWithin(startDate, endDate, envelopeStart, envelopeEnd)) {
            violations.add("转授期限超出上级声明的可转授期限 " + envelopeStart + " ~ " + envelopeEnd);
        }
        List<String> envelopeTerritories = parent.getSubTerritories().isEmpty()
                ? parent.getTerritories() : parent.getSubTerritories();
        List<String> envelopeMedia = parent.getSubMedia().isEmpty()
                ? parent.getMedia() : parent.getSubMedia();
        if (!ScopeRules.containsAll(envelopeTerritories, territories)) {
            violations.add("转授地域超出上级声明的可转授地域 " + envelopeTerritories);
        }
        if (!ScopeRules.containsAll(envelopeMedia, media)) {
            violations.add("转授媒介超出上级声明的可转授媒介 " + envelopeMedia);
        }

        // 下级再转授的声明不得超过上级声明
        validateRequestedPolicyWithinParent(parent, requestedPolicy, childDepth, violations);
        return violations;
    }

    private void validateRequestedPolicyWithinParent(LicenseGrant parent, SublicensePolicy requested,
                                                      int childDepth, List<String> violations) {
        if (!requested.sublicensable()) {
            return;
        }
        if (requested.maxDepth() == null) {
            violations.add("允许下级转授权时必须声明最大层级");
            return;
        }
        if (requested.maxDepth() < childDepth + 1) {
            violations.add("下级最大层级至少为 " + (childDepth + 1));
        }
        if (parent.getMaxDepth() != null && requested.maxDepth() > parent.getMaxDepth()) {
            violations.add("下级声明的最大层级 " + requested.maxDepth()
                    + " 超过上级允许的 " + parent.getMaxDepth());
        }
        LocalDate envelopeStart = parent.getSubStartDate() != null
                ? parent.getSubStartDate() : parent.getStartDate();
        LocalDate envelopeEnd = parent.getSubEndDate() != null
                ? parent.getSubEndDate() : parent.getEndDate();
        LocalDate reqStart = requested.subStartDate();
        LocalDate reqEnd = requested.subEndDate();
        if (reqStart != null && reqStart.isBefore(envelopeStart)) {
            violations.add("下级可转授起始日期早于上级允许的 " + envelopeStart);
        }
        if (reqEnd != null && reqEnd.isAfter(envelopeEnd)) {
            violations.add("下级可转授截止日期晚于上级允许的 " + envelopeEnd);
        }
        if (reqStart != null && reqEnd != null && reqStart.isAfter(reqEnd)) {
            violations.add("下级可转授期限不合法");
        }
        List<String> envelopeTerritories = parent.getSubTerritories().isEmpty()
                ? parent.getTerritories() : parent.getSubTerritories();
        List<String> envelopeMedia = parent.getSubMedia().isEmpty()
                ? parent.getMedia() : parent.getSubMedia();
        if (requested.subTerritories() != null
                && !new LinkedHashSet<>(envelopeTerritories).containsAll(new LinkedHashSet<>(requested.subTerritories()))) {
            violations.add("下级可转授地域超出上级允许的 " + envelopeTerritories);
        }
        if (requested.subMedia() != null
                && !new LinkedHashSet<>(envelopeMedia).containsAll(new LinkedHashSet<>(requested.subMedia()))) {
            violations.add("下级可转授媒介超出上级允许的 " + envelopeMedia);
        }
    }

    /**
     * 独占兄弟冲突（原子判定）：
     * 上级为独占时，独占下级不得与任何已有有效下级（独占/非独占）在任一 地域×媒介×日期 上重叠；
     * 非独占下级不得与已有独占下级重叠。所有组合一起校验，任一冲突则整份申请失败。
     */
    private void validateSiblingConflicts(LicenseGrant parent, SublicenseApplication application,
                                          List<String> violations) {
        List<LicenseGrant> siblings = grantRepository.findActiveSiblingsOverlapping(
                parent.getId(), application.getStartDate(), application.getEndDate());

        Set<LicenseType> blocking = application.getType() == LicenseType.EXCLUSIVE
                ? Set.of(LicenseType.EXCLUSIVE, LicenseType.NON_EXCLUSIVE)
                : Set.of(LicenseType.EXCLUSIVE);

        for (LicenseGrant sibling : siblings) {
            if (!blocking.contains(sibling.getType())) {
                continue;
            }
            Set<String> t = ScopeRules.intersect(application.getTerritories(), sibling.getTerritories());
            Set<String> m = ScopeRules.intersect(application.getMedia(), sibling.getMedia());
            if (!t.isEmpty() && !m.isEmpty()) {
                violations.add("与已有下级授权 " + sibling.getGrantNo()
                        + "（" + sibling.getType() + "，" + sibling.getStartDate() + " ~ "
                        + sibling.getEndDate() + "，被授权方 " + sibling.getLicensee()
                        + "）在地域 " + t + " × 媒介 " + m + " 上冲突");
            }
        }
    }

    /** 上级声明与申请策略逐字段取交集，确保下发给下级的权利绝不大于上级声明 */
    private SublicensePolicy intersectPolicies(LicenseGrant parent, SublicensePolicy requested) {
        if (!requested.sublicensable() || !parent.isSublicensable()) {
            return SublicensePolicy.forbidden();
        }
        int maxDepth = parent.getMaxDepth() == null
                ? requested.maxDepth()
                : Math.min(parent.getMaxDepth(), requested.maxDepth());

        List<String> parentTerritoryEnvelope = parent.getSubTerritories().isEmpty()
                ? parent.getTerritories() : parent.getSubTerritories();
        List<String> parentMediaEnvelope = parent.getSubMedia().isEmpty()
                ? parent.getMedia() : parent.getSubMedia();

        List<String> reqTerritories = requested.subTerritories();
        List<String> reqMedia = requested.subMedia();
        Set<String> territories = new TreeSet<>(parentTerritoryEnvelope);
        if (reqTerritories != null) {
            territories.retainAll(new LinkedHashSet<>(reqTerritories));
        }
        Set<String> media = new TreeSet<>(parentMediaEnvelope);
        if (reqMedia != null) {
            media.retainAll(new LinkedHashSet<>(reqMedia));
        }
        LocalDate parentStart = parent.getSubStartDate() != null
                ? parent.getSubStartDate() : parent.getStartDate();
        LocalDate parentEnd = parent.getSubEndDate() != null
                ? parent.getSubEndDate() : parent.getEndDate();
        LocalDate start = requested.subStartDate() != null
                ? maxDate(requested.subStartDate(), parentStart) : parentStart;
        LocalDate end = requested.subEndDate() != null
                ? minDate(requested.subEndDate(), parentEnd) : parentEnd;

        return new SublicensePolicy(true, maxDepth,
                List.copyOf(territories), List.copyOf(media), start, end);
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate minDate(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static List<String> normalize(List<String> values, String label) {
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

    private static SublicensePolicy normalizeRequestedPolicy(SublicensePolicy policy) {
        if (policy == null || !policy.sublicensable()) {
            return SublicensePolicy.forbidden();
        }
        if (policy.maxDepth() == null) {
            throw BusinessException.badRequest("允许下级转授权时必须声明最大层级");
        }
        List<String> territories = policy.subTerritories() == null
                ? null : normalize(policy.subTerritories(), "下级可转授地域");
        List<String> media = policy.subMedia() == null
                ? null : normalize(policy.subMedia(), "下级可转授媒介");
        if (policy.subStartDate() != null && policy.subEndDate() != null
                && policy.subStartDate().isAfter(policy.subEndDate())) {
            throw BusinessException.badRequest("下级可转授期限不合法");
        }
        return new SublicensePolicy(true, policy.maxDepth(), territories, media,
                policy.subStartDate(), policy.subEndDate());
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SublicenseApplication getApplication(Long id) {
        return applicationRepository.findDetailById(id)
                .orElseThrow(() -> BusinessException.notFound("转授权申请不存在: " + id));
    }

    @Transactional(readOnly = true)
    public SublicenseApplication getApplicationByNo(String sublicenseNo) {
        return applicationRepository.findDetailByNo(sublicenseNo)
                .orElseThrow(() -> BusinessException.notFound("转授权申请不存在: " + sublicenseNo));
    }

    @Transactional(readOnly = true)
    public List<SublicenseDecision> listDecisions(Long applicationId) {
        getApplication(applicationId);
        return decisionRepository.findByApplicationIdOrderById(applicationId);
    }

    /** 冲突对象查询：与一份待决/已冲突转授权申请重叠的有效下级授权 */
    @Transactional(readOnly = true)
    public List<LicenseGrant> findConflictingChildren(Long applicationId) {
        SublicenseApplication application = getApplication(applicationId);
        Set<LicenseType> blocking = application.getType() == LicenseType.EXCLUSIVE
                ? Set.of(LicenseType.EXCLUSIVE, LicenseType.NON_EXCLUSIVE)
                : Set.of(LicenseType.EXCLUSIVE);
        return grantRepository.findActiveSiblingsOverlapping(
                        application.getParentGrant().getId(),
                        application.getStartDate(), application.getEndDate()).stream()
                .filter(g -> blocking.contains(g.getType()))
                .filter(g -> !ScopeRules.intersect(application.getTerritories(), g.getTerritories()).isEmpty()
                        && !ScopeRules.intersect(application.getMedia(), g.getMedia()).isEmpty())
                .toList();
    }
}
