package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.GrantLifecycleEvent;
import com.chris64233.cc.rightslicense.domain.GrantPropagation;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.GrantVersion;
import com.chris64233.cc.rightslicense.domain.HaltReason;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.PropagationOperation;
import com.chris64233.cc.rightslicense.repo.GrantLifecycleEventRepository;
import com.chris64233.cc.rightslicense.repo.GrantPropagationRepository;
import com.chris64233.cc.rightslicense.repo.GrantVersionRepository;
import com.chris64233.cc.rightslicense.repo.LicenseGrantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 授权生命周期管理：撤销、暂停、恢复、范围缩减、到期。
 *
 * <p>每次操作都以事件号幂等；对目标授权加悲观写锁，并在<b>同一事务</b>内为全部受影响下级
 * 写入 {@link GrantPropagation} 任务后立即逐条应用（应用失败保留任务待重试，保证不遗漏）。
 * 范围缩减会生成新版本，不再被新上级范围完整覆盖的下级立即终止；仍被覆盖的下级保留。
 */
@Service
public class GrantLifecycleService {

    private final LicenseGrantRepository grantRepository;
    private final GrantVersionRepository versionRepository;
    private final GrantLifecycleEventRepository eventRepository;
    private final GrantPropagationRepository propagationRepository;
    private final GrantPropagationService propagationService;

    public GrantLifecycleService(LicenseGrantRepository grantRepository,
                                 GrantVersionRepository versionRepository,
                                 GrantLifecycleEventRepository eventRepository,
                                 GrantPropagationRepository propagationRepository,
                                 GrantPropagationService propagationService) {
        this.grantRepository = grantRepository;
        this.versionRepository = versionRepository;
        this.eventRepository = eventRepository;
        this.propagationRepository = propagationRepository;
        this.propagationService = propagationService;
    }

    /** 撤销授权：本级终止，全部下级（任意层级）终止 */
    @Transactional
    public LicenseGrant revoke(String grantNo, String eventNo) {
        return applyHalt(grantNo, eventNo, PropagationOperation.TERMINATE,
                HaltReason.REVOKED, HaltReason.PARENT_REVOKED);
    }

    /** 暂停授权：本级暂停，全部下级（任意层级）暂停，可恢复 */
    @Transactional
    public LicenseGrant suspend(String grantNo, String eventNo) {
        return applyHalt(grantNo, eventNo, PropagationOperation.SUSPEND,
                HaltReason.SELF_SUSPENDED, HaltReason.PARENT_SUSPENDED);
    }

    /**
     * 恢复授权。目标授权必须处于暂停（含恢复冲突 RESUME_CONFLICT）。
     * 不直接把目标置为有效，而是为"自身 + 全部下级"写入恢复任务，由传播守卫统一校验
     * （祖先均有效、无独占兄弟冲突），保证恢复后仍满足独占约束。
     */
    @Transactional
    public LicenseGrant resume(String grantNo, String eventNo) {
        requireEventNo(eventNo);
        if (eventRepository.findByEventNo(eventNo).isPresent()) {
            return grantRepository.findByGrantNo(grantNo)
                    .orElseThrow(() -> BusinessException.notFound("授权不存在: " + grantNo));
        }
        LicenseGrant grant = lock(grantNo);
        if (grant.getStatus() != GrantStatus.SUSPENDED) {
            throw BusinessException.conflict("授权当前状态为 " + grant.getStatus() + "，无需恢复");
        }
        List<GrantPropagation> tasks = new java.util.ArrayList<>();
        // 自身也作为恢复目标，由守卫判定能否恢复（消除独占兄弟冲突后再次调用即可放行）
        tasks.add(new GrantPropagation(grant, grant,
                PropagationOperation.RESUME, HaltReason.PARENT_SUSPENDED));
        grantRepository.findSubtree(grant.descendantPrefix()).stream()
                .map(child -> new GrantPropagation(grant, child,
                        PropagationOperation.RESUME, HaltReason.PARENT_SUSPENDED))
                .forEach(tasks::add);
        eventRepository.save(new GrantLifecycleEvent(
                eventNo, grant, PropagationOperation.RESUME, HaltReason.PARENT_SUSPENDED));
        schedule(tasks);
        return grant;
    }

    private LicenseGrant applyHalt(String grantNo, String eventNo, PropagationOperation operation,
                                   HaltReason selfReason, HaltReason childReason) {
        requireEventNo(eventNo);
        if (eventRepository.findByEventNo(eventNo).isPresent()) {
            // 事件号幂等：重复操作直接返回当前状态
            return grantRepository.findByGrantNo(grantNo)
                    .orElseThrow(() -> BusinessException.notFound("授权不存在: " + grantNo));
        }
        LicenseGrant grant = lock(grantNo);
        if (operation == PropagationOperation.TERMINATE) {
            grant.markTerminated(selfReason);
        } else {
            if (grant.getStatus() == GrantStatus.TERMINATED) {
                throw BusinessException.conflict("授权已终止，不能暂停");
            }
            if (grant.getStatus() == GrantStatus.SUSPENDED) {
                throw BusinessException.conflict("授权已处于暂停状态");
            }
            grant.markSuspended(selfReason);
        }
        eventRepository.save(new GrantLifecycleEvent(eventNo, grant, operation, selfReason));
        enqueueForDescendants(grant, operation, childReason);
        return grant;
    }

    /**
     * 范围缩减（只能缩小，不能扩大或改变独占性）。生成新版本；
     * 不再被新范围完整覆盖的下级终止，仍被完整覆盖的下级保留。同时把"可转授范围"裁剪到新范围。
     */
    @Transactional
    public LicenseGrant reduceScope(String grantNo, String eventNo,
                                    LocalDate newStart, LocalDate newEnd,
                                    List<String> newTerritories, List<String> newMedia) {
        requireEventNo(eventNo);
        if (eventRepository.findByEventNo(eventNo).isPresent()) {
            return grantRepository.findByGrantNo(grantNo)
                    .orElseThrow(() -> BusinessException.notFound("授权不存在: " + grantNo));
        }
        LicenseGrant grant = lock(grantNo);
        if (grant.getStatus() == GrantStatus.TERMINATED) {
            throw BusinessException.conflict("授权已终止，不能缩减范围");
        }
        LocalDate start = newStart != null ? newStart : grant.getStartDate();
        LocalDate end = newEnd != null ? newEnd : grant.getEndDate();
        List<String> territories = newTerritories != null && !newTerritories.isEmpty()
                ? newTerritories : grant.getTerritories();
        List<String> media = newMedia != null && !newMedia.isEmpty()
                ? newMedia : grant.getMedia();

        validateReduction(grant, start, end, territories, media);

        // 保存旧版本快照已经存在于版本表；写入新版本
        int nextVersion = grant.getCurrentVersion() + 1;
        grant.applyNewScope(grant.getType(), start, end, territories, media);
        clampPolicyToScope(grant, start, end, territories, media);

        versionRepository.save(new GrantVersion(
                grant, nextVersion, grant.getType(), start, end, territories, media,
                grant.isSublicensable(), grant.getMaxDepth(),
                grant.getSubTerritories(), grant.getSubMedia(),
                grant.getSubStartDate(), grant.getSubEndDate(), "范围缩减"));
        eventRepository.save(new GrantLifecycleEvent(
                eventNo, grant, PropagationOperation.TERMINATE, HaltReason.PARENT_SCOPE_REDUCED));

        // 不再被新范围完整覆盖的下级 → 终止；仍被覆盖的不动
        List<LicenseGrant> descendants = grantRepository.findSubtree(grant.descendantPrefix());
        List<GrantPropagation> tasks = new java.util.ArrayList<>();
        for (LicenseGrant child : descendants) {
            if (child.getStatus() == GrantStatus.TERMINATED) {
                continue;
            }
            if (!ScopeRules.datesWithin(child.getStartDate(), child.getEndDate(), start, end)
                    || !ScopeRules.containsAll(territories, child.getTerritories())
                    || !ScopeRules.containsAll(media, child.getMedia())) {
                tasks.add(new GrantPropagation(
                        grant, child, PropagationOperation.TERMINATE, HaltReason.PARENT_SCOPE_REDUCED));
            }
        }
        schedule(tasks);
        return grant;
    }

    /** 到期扫描：把已过截止日且仍有效的授权（根或下级）标记终止并级联 */
    @Transactional
    public int expireDueGrants(LocalDate today, String eventNoPrefix) {
        int expired = 0;
        List<LicenseGrant> active = grantRepository.findAll().stream()
                .filter(LicenseGrant::isActive)
                .filter(g -> g.getEndDate().isBefore(today))
                .toList();
        for (LicenseGrant grant : active) {
            String eventNo = eventNoPrefix + "-" + grant.getGrantNo();
            if (eventRepository.findByEventNo(eventNo).isPresent()) {
                continue;
            }
            grant.markTerminated(HaltReason.EXPIRED);
            eventRepository.save(new GrantLifecycleEvent(
                    eventNo, grant, PropagationOperation.TERMINATE, HaltReason.PARENT_EXPIRED));
            enqueueForDescendants(grant, PropagationOperation.TERMINATE, HaltReason.PARENT_EXPIRED);
            expired++;
        }
        return expired;
    }

    // ------------------------------------------------------------------

    private LicenseGrant lock(String grantNo) {
        return grantRepository.findByGrantNoForUpdate(grantNo)
                .orElseThrow(() -> BusinessException.notFound("授权不存在: " + grantNo));
    }

    /**
     * 为全部下级（任意层级）写入传播任务。任务与状态变化同事务落库（保证不遗漏）；
     * 事务提交后再尽力应用，避免在同一事务内看不到尚未提交的任务。
     * 应用失败的任务保留为 PENDING，由 {@code retryPendingPropagations} 重试。
     */
    private void enqueueForDescendants(LicenseGrant grant, PropagationOperation operation,
                                       HaltReason childReason) {
        List<LicenseGrant> descendants = grantRepository.findSubtree(grant.descendantPrefix());
        List<GrantPropagation> tasks = descendants.stream()
                .map(child -> new GrantPropagation(grant, child, operation, childReason))
                .toList();
        schedule(tasks);
    }

    /**
     * 持久化一批传播任务（与触发事件同事务，保证不遗漏），并注册事务提交后回调尽力应用。
     * afterCommit 只注册一次（同事务多次调用共享一个标志）；即使回调未运行，任务仍是 PENDING 可重试。
     */
    private void schedule(List<GrantPropagation> tasks) {
        if (tasks.isEmpty()) {
            return;
        }
        tasks.forEach(propagationRepository::save);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    propagationService.processPending();
                }
            });
        }
    }

    /** 显式重试尚未应用的级联任务，返回成功应用数 */
    @Transactional(readOnly = true)
    public int retryPendingPropagations() {
        return propagationService.processPending();
    }

    public long pendingPropagationCount() {
        return propagationService.pendingCount();
    }

    private void validateReduction(LicenseGrant grant, LocalDate start, LocalDate end,
                                   List<String> territories, List<String> media) {
        if (start.isBefore(grant.getStartDate()) || end.isAfter(grant.getEndDate())
                || start.isAfter(end)) {
            throw BusinessException.badRequest("缩减后的期限必须落在原期限内");
        }
        if (!new LinkedHashSet<>(grant.getTerritories()).containsAll(new LinkedHashSet<>(territories))) {
            throw BusinessException.badRequest("缩减后的地域只能是原地域的子集");
        }
        if (!new LinkedHashSet<>(grant.getMedia()).containsAll(new LinkedHashSet<>(media))) {
            throw BusinessException.badRequest("缩减后的媒介只能是原媒介的子集");
        }
        if (territories.isEmpty() || media.isEmpty()) {
            throw BusinessException.badRequest("缩减后地域与媒介均不能为空");
        }
    }

    /** 范围缩减后，把可转授声明一并裁剪进新范围 */
    private void clampPolicyToScope(LicenseGrant grant, LocalDate start, LocalDate end,
                                    List<String> territories, List<String> media) {
        if (!grant.isSublicensable()) {
            return;
        }
        Set<String> subT = new LinkedHashSet<>(grant.getSubTerritories().isEmpty()
                ? grant.getTerritories() : grant.getSubTerritories());
        subT.retainAll(new LinkedHashSet<>(territories));
        Set<String> subM = new LinkedHashSet<>(grant.getSubMedia().isEmpty()
                ? grant.getMedia() : grant.getSubMedia());
        subM.retainAll(new LinkedHashSet<>(media));
        LocalDate subStart = grant.getSubStartDate() != null
                ? max(grant.getSubStartDate(), start) : start;
        LocalDate subEnd = grant.getSubEndDate() != null
                ? min(grant.getSubEndDate(), end) : end;
        grant.getSubTerritories().clear();
        grant.getSubTerritories().addAll(subT);
        grant.getSubMedia().clear();
        grant.getSubMedia().addAll(subM);
        // 通过新的构造快照需要可写字段；LicenseGrant 的 sub 日期字段由下面反射式设置不可行，
        // 改为新增包内方法（见 LicenseGrant#updatePolicyEnvelope）
        grant.updatePolicyEnvelope(subStart, subEnd, subT, subM);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static void requireEventNo(String eventNo) {
        if (eventNo == null || eventNo.isBlank()) {
            throw BusinessException.badRequest("事件号不能为空");
        }
    }
}
