package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.GrantPropagation;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.HaltReason;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.PropagationOperation;
import com.chris64233.cc.rightslicense.domain.PropagationStatus;
import com.chris64233.cc.rightslicense.repo.GrantPropagationRepository;
import com.chris64233.cc.rightslicense.repo.LicenseGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 级联传播处理器：消费 {@link GrantPropagation} 任务，把上级状态变化下发到每个下级授权。
 *
 * <p>可靠性：任务与触发事件同事务落库（不遗漏）；本处理器逐条以独立事务应用，
 * 失败任务保留 PENDING、记录失败次数与错误信息，可反复重试（{@link #processPending()}）。
 * 状态机守卫保证重复应用幂等：终止不可逆；暂停不覆盖终止；恢复仅作用于"因上级暂停"
 * 且所有上级当前均已恢复有效 的下级。
 */
@Service
public class GrantPropagationService {

    private static final Logger log = LoggerFactory.getLogger(GrantPropagationService.class);

    private final GrantPropagationRepository propagationRepository;
    private final LicenseGrantRepository grantRepository;
    private final GrantPropagationService self;

    public GrantPropagationService(GrantPropagationRepository propagationRepository,
                                   LicenseGrantRepository grantRepository,
                                   @Lazy GrantPropagationService self) {
        this.propagationRepository = propagationRepository;
        this.grantRepository = grantRepository;
        this.self = self;
    }

    /**
     * 应用全部待处理任务，返回本次成功应用（标记 APPLIED）的任务数。
     * 每条任务独立事务；单条失败只记录失败次数并保留 PENDING，不影响其他任务，可反复重试。
     */
    public int processPending() {
        List<GrantPropagation> pending =
                propagationRepository.findByStatusOrderById(PropagationStatus.PENDING);
        int applied = 0;
        for (GrantPropagation task : pending) {
            if (self.applyTask(task.getId())) {
                applied++;
            }
        }
        return applied;
    }

    public long pendingCount() {
        return propagationRepository.countByStatus(PropagationStatus.PENDING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean applyTask(Long taskId) {
        GrantPropagation task = propagationRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> BusinessException.notFound("传播任务不存在: " + taskId));
        if (task.getStatus() == PropagationStatus.APPLIED) {
            // 幂等重放
            return true;
        }
        LicenseGrant target = task.getTarget();
        try {
            switch (task.getOperation()) {
                case TERMINATE -> applyTerminate(target, task.getReason());
                case SUSPEND -> applySuspend(target, task.getReason());
                case RESUME -> applyResume(task.getSource().getId().equals(target.getId()), target);
            }
            task.markApplied();
            return true;
        } catch (RuntimeException ex) {
            // 失败也持久化重试次数，任务保持 PENDING；不向上抛出以免中断同批其他任务
            task.recordAttempt(truncate(ex.getMessage()));
            log.warn("授权级联传播任务 #{} 应用失败（已重试 {} 次）: {}",
                    task.getId(), task.getAttempts(), ex.getMessage());
            return false;
        }
    }

    private void applyTerminate(LicenseGrant target, HaltReason reason) {
        // 终止不可逆且优先级最高；重复终止幂等
        if (target.getStatus() != GrantStatus.TERMINATED) {
            target.markTerminated(reason);
        }
    }

    private void applySuspend(LicenseGrant target, HaltReason reason) {
        // 已终止的授权不得被暂停覆盖；已暂停的重复暂停幂等
        if (target.getStatus() == GrantStatus.ACTIVE) {
            target.markSuspended(reason);
        }
    }

    /**
     * @param selfTask true 表示恢复任务的目标就是被显式恢复的授权本身（其挂起原因为 SELF_SUSPENDED 或
     *                 RESUME_CONFLICT）；false 表示因上级恢复而传播到下级（挂起原因为 PARENT_SUSPENDED）。
     */
    private void applyResume(boolean selfTask, LicenseGrant target) {
        if (target.getStatus() != GrantStatus.SUSPENDED) {
            return;
        }
        HaltReason reason = target.getHaltReason();
        boolean resumableReason = selfTask
                ? (reason == HaltReason.SELF_SUSPENDED || reason == HaltReason.RESUME_CONFLICT)
                : (reason == HaltReason.PARENT_SUSPENDED || reason == HaltReason.RESUME_CONFLICT);
        if (!resumableReason) {
            // 因范围缩减等终止/挂起的节点不由恢复任务处理
            return;
        }
        if (!allAncestorsActive(target)) {
            // 仍有上级处于暂停/终止，本条暂不恢复；上级恢复时会产生新的恢复任务
            return;
        }
        if (hasSiblingConflictOnResume(target)) {
            // 暂停期间其兄弟位置可能被授予了独占授权：恢复会破坏独占性。
            // 保持暂停并标记 RESUME_CONFLICT，任务正常核销，需要人工调整（如撤销冲突授权后再次发起恢复）。
            target.markSuspended(HaltReason.RESUME_CONFLICT);
            return;
        }
        target.markActive();
    }

    /**
     * 恢复独占/非独占授权时的兄弟冲突：与待恢复授权日期重叠的有效兄弟授权中，
     * 待恢复为独占时任意兄弟（独占/非独占）都冲突；待恢复为非独占时任一独占兄弟冲突。
     */
    private boolean hasSiblingConflictOnResume(LicenseGrant target) {
        if (target.getParent() == null) {
            return false;
        }
        List<LicenseGrant> activeSiblings = grantRepository.findActiveSiblingsOverlapping(
                target.getParent().getId(), target.getStartDate(), target.getEndDate());
        for (LicenseGrant sibling : activeSiblings) {
            if (sibling.getId().equals(target.getId())) {
                continue;
            }
            // 日期已由查询过滤；还需在任一 地域 × 媒介 上真正重叠才算冲突
            if (ScopeRules.intersect(target.getTerritories(), sibling.getTerritories()).isEmpty()
                    || ScopeRules.intersect(target.getMedia(), sibling.getMedia()).isEmpty()) {
                continue;
            }
            if (target.getType() == com.chris64233.cc.rightslicense.domain.LicenseType.EXCLUSIVE) {
                return true;
            }
            if (sibling.getType() == com.chris64233.cc.rightslicense.domain.LicenseType.EXCLUSIVE) {
                return true;
            }
        }
        return false;
    }

    private boolean allAncestorsActive(LicenseGrant target) {
        for (Long ancestorId : target.getChain().subList(0, target.getChain().size() - 1)) {
            LicenseGrant ancestor = grantRepository.findById(ancestorId).orElse(null);
            if (ancestor == null || ancestor.getStatus() != GrantStatus.ACTIVE) {
                return false;
            }
        }
        return true;
    }

    private static String truncate(String message) {
        if (message == null) {
            return "未知错误";
        }
        return message.length() > 1900 ? message.substring(0, 1900) : message;
    }
}
