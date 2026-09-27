package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.CascadeEventType;
import com.chris64233.cc.rightslicense.domain.CascadeTask;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicenseDecision;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.domain.SublicenseStatus;
import com.chris64233.cc.rightslicense.repo.LicenseGrantRepository;
import com.chris64233.cc.rightslicense.repo.SublicenseApplicationRepository;
import com.chris64233.cc.rightslicense.repo.SublicenseDecisionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 受控转授权。
 *
 * 并发约束：创建/审批转授权申请、撤销、缩减范围、到期处理，都先按根授权加悲观写锁，
 * 再锁定直接上级授权，保证新的下级申请不会与上级状态变化交错而逃逸限制。
 */
@Service
public class SublicenseService {

    private final LicenseGrantRepository grantRepository;
    private final SublicenseApplicationRepository applicationRepository;
    private final SublicenseDecisionRepository decisionRepository;
    private final CascadeService cascadeService;
    private final EntityManager entityManager;

    public SublicenseService(LicenseGrantRepository grantRepository,
                             SublicenseApplicationRepository applicationRepository,
                             SublicenseDecisionRepository decisionRepository,
                             CascadeService cascadeService,
                             EntityManager entityManager) {
        this.grantRepository = grantRepository;
        this.applicationRepository = applicationRepository;
        this.decisionRepository = decisionRepository;
        this.cascadeService = cascadeService;
        this.entityManager = entityManager;
    }

    // ------------------------------------------------------------------
    // 转授权申请
    // ------------------------------------------------------------------

    /**
     * 创建转授权申请。转授权号全局唯一：重复提交同一转授权号时幂等返回已有申请，
     * 但请求内容必须与首次一致，否则视为冲突。
     */
    @Transactional
    public SublicenseApplication createApplication(String sublicenseNumber, Long parentGrantId,
                                                   String applicant, String sublicensee,
                                                   LicenseType type,
                                                   LocalDate startDate, LocalDate endDate,
                                                   List<String> territories, List<String> media,
                                                   SublicensePolicy policy) {
        if (sublicenseNumber == null || sublicenseNumber.isBlank()) {
            throw BusinessException.badRequest("转授权号不能为空");
        }
        var existing = applicationRepository.findBySublicenseNumber(sublicenseNumber);
        if (existing.isPresent()) {
            SublicenseApplication found = existing.get();
            if (!found.getParentGrant().getId().equals(parentGrantId)) {
                throw BusinessException.conflict("转授权号已用于其他上级授权: " + sublicenseNumber);
            }
            return found;
        }
        LicenseGrant parent = lockChainOf(parentGrantId);
        validateApplicant(parent, applicant);
        validateSublicensee(sublicensee, parent);
        if (type == null) {
            throw BusinessException.badRequest("授权类型不能为空");
        }
        validateDates(startDate, endDate);
        List<String> cleanTerritories = normalizeScope(territories, "地域");
        List<String> cleanMedia = normalizeScope(media, "媒介");

        // 整份申请（全部地域 × 媒介 × 日期组合）原子校验：必须完全落在上级有效范围内
        validateWithinParent(parent, type, startDate, endDate, cleanTerritories, cleanMedia);
        // 申请方是上级当前被授权方，且上级必须处于有效状态
        validateParentActive(parent);
        // 下级授权自带的转授权策略必须被上级的剩余授权空间夹取，防止通过声明宽松策略逃逸层级/范围限制
        SublicensePolicy clampedPolicy = clampChildPolicy(parent, policy,
                startDate, endDate, cleanTerritories, cleanMedia);
        return applicationRepository.save(new SublicenseApplication(
                sublicenseNumber, parent, applicant, sublicensee, type,
                startDate, endDate, cleanTerritories, cleanMedia, clampedPolicy));
    }

    /**
     * 夹取下级授权可再转授的策略：
     * 层级绝对上界沿用上级；可转授的地域/媒介/期限为「申请范围 ∩ 上级可转授范围 ∩ 申请声明」。
     */
    private static SublicensePolicy clampChildPolicy(LicenseGrant parent, SublicensePolicy requested,
                                                     LocalDate childStart, LocalDate childEnd,
                                                     List<String> childTerritories,
                                                     List<String> childMedia) {
        if (requested == null || !requested.sublicensable() || !parent.isSublicensable()) {
            return SublicensePolicy.disabled();
        }
        int childDepth = parent.getDepth() + 1;
        if (parent.getMaxSublicenseDepth() != null && childDepth >= parent.getMaxSublicenseDepth()) {
            // 本级已是允许的最深层，不能再向下转授
            return SublicensePolicy.disabled();
        }
        Integer absoluteCap = parent.getMaxSublicenseDepth();
        if (requested.maxLevels() != null) {
            int requestedCap = childDepth + requested.maxLevels();
            absoluteCap = absoluteCap == null ? requestedCap : Math.min(absoluteCap, requestedCap);
        }
        Integer maxLevels = absoluteCap == null ? null : absoluteCap - childDepth;

        List<String> parentAllowedTerritories = parent.getSublicensableTerritories().isEmpty()
                ? parent.getTerritories() : parent.getSublicensableTerritories();
        List<String> requestedTerritories = requested.territories().isEmpty()
                ? childTerritories : requested.territories();
        List<String> allowedTerritories = intersectList(childTerritories,
                intersectList(parentAllowedTerritories, requestedTerritories));

        List<String> parentAllowedMedia = parent.getSublicensableMedia().isEmpty()
                ? parent.getMedia() : parent.getSublicensableMedia();
        List<String> requestedMedia = requested.media().isEmpty()
                ? childMedia : requested.media();
        List<String> allowedMedia = intersectList(childMedia,
                intersectList(parentAllowedMedia, requestedMedia));

        LocalDate parentStartBound = parent.getSublicenseStartBound() != null
                ? parent.getSublicenseStartBound() : parent.getStartDate();
        LocalDate parentEndBound = parent.getSublicenseEndBound() != null
                ? parent.getSublicenseEndBound() : parent.getEndDate();
        LocalDate requestedStartBound = requested.startBound() != null
                ? requested.startBound() : childStart;
        LocalDate requestedEndBound = requested.endBound() != null
                ? requested.endBound() : childEnd;
        LocalDate allowedStart = parentStartBound.isAfter(requestedStartBound)
                ? parentStartBound : requestedStartBound;
        LocalDate allowedEnd = parentEndBound.isBefore(requestedEndBound)
                ? parentEndBound : requestedEndBound;
        allowedStart = allowedStart.isBefore(childStart) ? childStart : allowedStart;
        allowedEnd = allowedEnd.isAfter(childEnd) ? childEnd : allowedEnd;

        if (allowedTerritories.isEmpty() || allowedMedia.isEmpty()
                || allowedStart.isAfter(allowedEnd)) {
            return SublicensePolicy.disabled();
        }
        return new SublicensePolicy(true, maxLevels,
                allowedTerritories, allowedMedia, allowedStart, allowedEnd);
    }

    private static List<String> intersectList(List<String> left, List<String> right) {
        Set<String> rightSet = new LinkedHashSet<>(right);
        Set<String> result = new LinkedHashSet<>();
        for (String value : new LinkedHashSet<>(left)) {
            if (rightSet.contains(value)) {
                result.add(value);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 提交转授权审批决定（由上级授权当前被授权方作出）。
     * 同一事件号幂等；每个申请只允许一条决定。
     */
    @Transactional
    public SublicenseDecision submitDecision(Long applicationId, DecisionValue decisionValue,
                                             String eventNumber) {
        if (decisionValue == null) {
            throw BusinessException.badRequest("决定不能为空");
        }
        if (eventNumber == null || eventNumber.isBlank()) {
            throw BusinessException.badRequest("事件号不能为空");
        }
        SublicenseApplication application = applicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> BusinessException.notFound("转授权申请不存在: " + applicationId));

        var existingByEvent = decisionRepository
                .findByApplicationIdAndEventNumber(application.getId(), eventNumber);
        if (existingByEvent.isPresent()) {
            return existingByEvent.get();
        }
        if (application.getStatus() != SublicenseStatus.PENDING) {
            throw BusinessException.conflict(
                    "转授权申请已终结（" + application.getStatus() + "），无法再提交决定");
        }
        if (decisionRepository.existsByApplicationId(application.getId())) {
            throw BusinessException.conflict("该转授权申请已有审批决定，决定不可修改");
        }

        SublicenseDecision decision = decisionRepository.save(
                new SublicenseDecision(application, decisionValue, eventNumber));

        if (decisionValue == DecisionValue.REJECT) {
            application.markRejected();
            return decision;
        }
        finalizeApproval(application);
        return decision;
    }

    private void finalizeApproval(SublicenseApplication application) {
        // 与上级状态变化（撤销/缩减/到期）串行化：先锁根授权，再锁直接上级
        LicenseGrant parent = lockChainOf(application.getParentGrant().getId());

        // 审批时点重新原子校验：申请必须仍完全落在上级当前有效范围内；
        // 若等待锁期间上级发生状态变化，则整份申请标记冲突，决定记录保留。
        try {
            validateParentActive(parent);
            validateWithinParent(parent, application.getType(),
                    application.getStartDate(), application.getEndDate(),
                    application.getTerritories(), application.getMedia());
        } catch (BusinessException ex) {
            application.markConflict(ex.getMessage());
            return;
        }

        List<LicenseGrant> conflicts = findConflictingGrants(parent, application);
        if (!conflicts.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            for (LicenseGrant conflict : conflicts) {
                reasons.add("与已有授权#" + conflict.getId()
                        + "（" + conflict.getType() + "，" + conflict.getStartDate()
                        + " ~ " + conflict.getEndDate() + "，被授权方 " + conflict.getLicensee()
                        + "）在地域/媒介/期限上重叠");
            }
            application.markConflict(String.join("；", reasons));
            return;
        }

        LicenseGrant grant = grantRepository.save(
                new LicenseGrant(parent, application, application.toSublicensePolicy()));
        grant.initHierarchy();
        application.markApproved(parent.getVersion());
    }

    // ------------------------------------------------------------------
    // 上级授权生命周期：撤销 / 缩减 / 到期
    // ------------------------------------------------------------------

    /** 撤销授权：自身终止，全部下级授权级联终止。 */
    @Transactional
    public LicenseGrant revokeGrant(Long grantId) {
        LicenseGrant grant = lockChainOf(grantId);
        if (grant.getStatus() == GrantStatus.TERMINATED) {
            return grant;
        }
        grant.markTerminated("授权 #" + grant.getId() + " 被撤销");
        enqueueForSubtree(grant, CascadeEventType.REVOKED,
                "上级授权 #" + grant.getId() + " 已被撤销");
        cascadeService.triggerAfterCommit();
        return grant;
    }

    /**
     * 缩减授权范围（只允许缩小，不允许扩大）。
     * 缩减后不再被覆盖的下级授权级联暂停；任务处理时会按上级最新范围重新判定。
     */
    @Transactional
    public LicenseGrant reduceScope(Long grantId, LocalDate newStart, LocalDate newEnd,
                                    List<String> newTerritories, List<String> newMedia) {
        LicenseGrant grant = lockChainOf(grantId);
        if (grant.getStatus() == GrantStatus.TERMINATED) {
            throw BusinessException.conflict("授权已终止，无法缩减范围");
        }
        validateDates(newStart, newEnd);
        List<String> cleanTerritories = normalizeScope(newTerritories, "地域");
        List<String> cleanMedia = normalizeScope(newMedia, "媒介");
        boolean isReduction = !newStart.isBefore(grant.getStartDate())
                && !newEnd.isAfter(grant.getEndDate())
                && grant.getTerritories().containsAll(cleanTerritories)
                && grant.getMedia().containsAll(cleanMedia);
        if (!isReduction) {
            throw BusinessException.badRequest("只能缩减授权范围，不允许扩大地域、媒介或期限");
        }
        grant.reduceScope(newStart, newEnd, cleanTerritories, cleanMedia);
        enqueueForSubtree(grant, CascadeEventType.SCOPE_REDUCED,
                "上级授权 #" + grant.getId() + " 范围已缩减，不再覆盖该下级授权");
        cascadeService.triggerAfterCommit();
        return grant;
    }

    /** 立即使授权到期（到期巡检之外的管理入口），下级级联暂停或到期。 */
    @Transactional
    public LicenseGrant expireGrant(Long grantId) {
        LicenseGrant grant = lockChainOf(grantId);
        if (grant.getStatus() == GrantStatus.TERMINATED
                || grant.getStatus() == GrantStatus.EXPIRED) {
            return grant;
        }
        grant.markExpired("授权 #" + grant.getId() + " 已到期");
        enqueueForSubtree(grant, CascadeEventType.EXPIRED,
                "上级授权 #" + grant.getId() + " 已到期");
        cascadeService.triggerAfterCommit();
        return grant;
    }

    private void enqueueForSubtree(LicenseGrant source, CascadeEventType eventType, String detail) {
        List<LicenseGrant> subtree = grantRepository.findSubtree(source.getChainPath());
        for (LicenseGrant child : subtree) {
            if (child.getId().equals(source.getId())) {
                continue;
            }
            cascadeService.enqueue(new CascadeTask(child, source, eventType, detail));
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public SublicenseApplication getApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> BusinessException.notFound("转授权申请不存在: " + applicationId));
    }

    @Transactional(readOnly = true)
    public List<SublicenseDecision> listDecisions(Long applicationId) {
        getApplication(applicationId);
        return decisionRepository.findByApplicationIdOrderById(applicationId);
    }

    @Transactional(readOnly = true)
    public LicenseGrant getGrant(Long grantId) {
        return grantRepository.findById(grantId)
                .orElseThrow(() -> BusinessException.notFound("授权不存在: " + grantId));
    }

    /** 权利树：以指定授权为根的整棵子树（含自身，父先于子）。 */
    @Transactional(readOnly = true)
    public List<LicenseGrant> getRightsTree(Long grantId) {
        LicenseGrant root = getGrant(grantId);
        return grantRepository.findSubtree(root.getChainPath());
    }

    /** 某授权的直接下级。 */
    @Transactional(readOnly = true)
    public List<LicenseGrant> listChildren(Long grantId) {
        getGrant(grantId);
        return grantRepository.findByParentGrantIdOrderById(grantId);
    }

    /**
     * 有效范围：沿层级链自根向下逐层求交（日期取交集、地域/媒介取交集），
     * 并报告链上第一个非有效状态的授权。
     */
    @Transactional(readOnly = true)
    public EffectiveScope getEffectiveScope(Long grantId) {
        LicenseGrant grant = getGrant(grantId);
        List<LicenseGrant> chain = chainOf(grant);

        LocalDate start = null;
        LocalDate end = null;
        Set<String> territories = null;
        Set<String> media = null;
        for (LicenseGrant node : chain) {
            if (node.getStatus() != GrantStatus.ACTIVE) {
                return new EffectiveScope(grant.getId(), false,
                        "授权 #" + node.getId() + " 当前状态为 " + node.getStatus()
                                + "（" + (node.getStatusReason() != null ? node.getStatusReason() : "无说明") + "）",
                        null, null, List.of(), List.of());
            }
            start = start == null ? node.getStartDate()
                    : (node.getStartDate().isAfter(start) ? node.getStartDate() : start);
            end = end == null ? node.getEndDate()
                    : (node.getEndDate().isBefore(end) ? node.getEndDate() : end);
            territories = intersect(territories, node.getTerritories());
            media = intersect(media, node.getMedia());
        }
        if (start.isAfter(end) || territories.isEmpty() || media.isEmpty()) {
            return new EffectiveScope(grant.getId(), false,
                    "层级链范围交集为空，授权不再有效", null, null, List.of(), List.of());
        }
        return new EffectiveScope(grant.getId(), true, null,
                start, end, List.copyOf(territories), List.copyOf(media));
    }

    public record EffectiveScope(Long grantId, boolean effective, String reason,
                                 LocalDate startDate, LocalDate endDate,
                                 List<String> territories, List<String> media) {
    }

    /** 转授权申请的冲突对象查询（按审批时同样的规则实时计算）。 */
    @Transactional(readOnly = true)
    public List<LicenseGrant> findConflictingGrants(Long applicationId) {
        SublicenseApplication application = getApplication(applicationId);
        return findConflictingGrants(application.getParentGrant(), application);
    }

    // ------------------------------------------------------------------
    // 内部校验
    // ------------------------------------------------------------------

    /**
     * 锁定从根到目标授权的整条链（按层级顺序加悲观写锁），返回从数据库重新读取的目标授权。
     * 所有改变上级状态或新增下级的操作都走这里，保证并发下不会逃逸限制。
     *
     * 注意：必须先以标量查询取得根 id（不污染持久化上下文），加锁等待结束后再用
     * PESSIMISTIC_WRITE + refresh 重新装载目标，否则会拿到等待前缓存的旧状态。
     */
    private LicenseGrant lockChainOf(Long grantId) {
        Long rootGrantId = grantRepository.findRootIdById(grantId)
                .orElseThrow(() -> BusinessException.notFound("授权不存在: " + grantId));
        if (!rootGrantId.equals(grantId)) {
            // 先锁根授权（同一权利树内所有并发操作的统一点）
            grantRepository.findByIdForUpdate(rootGrantId)
                    .orElseThrow(() -> BusinessException.notFound("根授权不存在"));
        }
        // 再锁目标授权，并强制按加锁后的最新结果刷新，消除一级缓存旧值
        LicenseGrant grant = grantRepository.findByIdForUpdate(grantId)
                .orElseThrow(() -> BusinessException.notFound("授权不存在: " + grantId));
        entityManager.refresh(grant, LockModeType.PESSIMISTIC_WRITE);
        return grant;
    }

    private List<LicenseGrant> chainOf(LicenseGrant grant) {
        Deque<LicenseGrant> chain = new ArrayDeque<>();
        LicenseGrant current = grant;
        while (current != null) {
            chain.addFirst(current);
            current = current.getParentGrant();
        }
        return List.copyOf(chain);
    }

    private static void validateApplicant(LicenseGrant parent, String applicant) {
        if (applicant == null || applicant.isBlank()) {
            throw BusinessException.badRequest("申请方不能为空");
        }
        if (!applicant.equals(parent.getLicensee())) {
            throw BusinessException.unprocessable(
                    "申请方必须是上级授权的当前被授权方（" + parent.getLicensee() + "）");
        }
    }

    private static void validateSublicensee(String sublicensee, LicenseGrant parent) {
        if (sublicensee == null || sublicensee.isBlank()) {
            throw BusinessException.badRequest("下级被授权方不能为空");
        }
        if (sublicensee.equals(parent.getLicensee())) {
            throw BusinessException.badRequest("下级被授权方不能与上级被授权方相同");
        }
    }

    private static void validateParentActive(LicenseGrant parent) {
        if (parent.getStatus() != GrantStatus.ACTIVE) {
            throw BusinessException.conflict(
                    "上级授权 #" + parent.getId() + " 当前状态为 " + parent.getStatus()
                            + "，不能授予新的转授权");
        }
    }

    /**
     * 原子校验整份申请是否完全落在上级授权（含其转授权策略）范围内：
     * 层级、期限、全部地域、全部媒介任一不满足则整份拒绝。
     */
    private static void validateWithinParent(LicenseGrant parent, LicenseType type,
                                             LocalDate startDate, LocalDate endDate,
                                             List<String> territories, List<String> media) {
        if (!parent.isSublicensable()) {
            throw BusinessException.unprocessable("上级授权 #" + parent.getId() + " 不允许转授权");
        }
        int childDepth = parent.getDepth() + 1;
        if (parent.getMaxSublicenseDepth() != null && childDepth > parent.getMaxSublicenseDepth()) {
            throw BusinessException.unprocessable(
                    "超出上级授权允许的转授权层级（最深到第 " + parent.getMaxSublicenseDepth() + " 层）");
        }
        if (type == LicenseType.EXCLUSIVE && parent.getType() != LicenseType.EXCLUSIVE) {
            throw BusinessException.unprocessable("非独占上级授权不能授予独占转授权");
        }
        LocalDate startBound = parent.getSublicenseStartBound() != null
                ? parent.getSublicenseStartBound() : parent.getStartDate();
        LocalDate endBound = parent.getSublicenseEndBound() != null
                ? parent.getSublicenseEndBound() : parent.getEndDate();
        if (startDate.isBefore(startBound) || endDate.isAfter(endBound)) {
            throw BusinessException.unprocessable(
                    "转授权期限必须落在上级允许的期限范围 " + startBound + " ~ " + endBound + " 内");
        }
        List<String> allowedTerritories = parent.getSublicensableTerritories().isEmpty()
                ? parent.getTerritories() : parent.getSublicensableTerritories();
        List<String> missingTerritories = ScopeRules.notContained(territories, allowedTerritories);
        if (!missingTerritories.isEmpty()) {
            throw BusinessException.unprocessable("转授权地域超出上级允许范围: " + missingTerritories);
        }
        List<String> allowedMedia = parent.getSublicensableMedia().isEmpty()
                ? parent.getMedia() : parent.getSublicensableMedia();
        List<String> missingMedia = ScopeRules.notContained(media, allowedMedia);
        if (!missingMedia.isEmpty()) {
            throw BusinessException.unprocessable("转授权媒介超出上级允许范围: " + missingMedia);
        }
    }

    /**
     * 冲突检测（按 地域 × 媒介 × 日期 组合，闭区间）：
     * - 独占转授权：不得与同一作品任何有效授权（含其他权利树）重叠；
     * - 非独占转授权：不得与任何有效独占授权重叠。
     * 自身层级链上的祖先授权天然覆盖申请范围，不计入冲突。
     */
    private List<LicenseGrant> findConflictingGrants(LicenseGrant parent,
                                                     SublicenseApplication application) {
        Set<LicenseType> blockingTypes = application.getType() == LicenseType.EXCLUSIVE
                ? Set.of(LicenseType.EXCLUSIVE, LicenseType.NON_EXCLUSIVE)
                : Set.of(LicenseType.EXCLUSIVE);
        List<LicenseGrant> overlapping = grantRepository.findOverlapping(
                parent.getWork().getId(), application.getStartDate(), application.getEndDate(),
                blockingTypes, GrantStatus.ACTIVE);

        Set<Long> ancestors = ancestorIds(parent);
        ScopeRules.Scope requested = new ScopeRules.Scope(
                application.getStartDate(), application.getEndDate(),
                application.getTerritories(), application.getMedia());
        List<LicenseGrant> conflicts = new ArrayList<>();
        for (LicenseGrant grant : overlapping) {
            if (ancestors.contains(grant.getId())) {
                continue;
            }
            if (application.getId() != null
                    && grant.getSublicenseApplication() != null
                    && grant.getSublicenseApplication().getId().equals(application.getId())) {
                // 已批准申请自身生成的授权不计入冲突对象
                continue;
            }
            if (ScopeRules.scopeCellsOverlap(requested,
                    new ScopeRules.Scope(grant.getStartDate(), grant.getEndDate(),
                            grant.getTerritories(), grant.getMedia()))) {
                conflicts.add(grant);
            }
        }
        return conflicts;
    }

    private static Set<Long> ancestorIds(LicenseGrant parent) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String segment : parent.getChainPath().split("/")) {
            if (!segment.isBlank()) {
                ids.add(Long.parseLong(segment));
            }
        }
        return ids;
    }

    private static Set<String> intersect(Set<String> current, List<String> next) {
        if (current == null) {
            return new LinkedHashSet<>(next);
        }
        Set<String> result = new LinkedHashSet<>(current);
        result.retainAll(new LinkedHashSet<>(next));
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
}
