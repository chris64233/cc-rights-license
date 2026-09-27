package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.CascadeTask;
import com.chris64233.cc.rightslicense.domain.CascadeTaskStatus;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.repo.CascadeTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;

/**
 * 级联传播：上级授权状态变化时，对全部受影响下级授权执行暂停或终止。
 *
 * 任务在状态变化所在事务内落库（不遗漏），实际处理在独立事务中逐条执行，
 * 失败标记为 FAILED 并可反复重试，成功才置为 DONE。
 */
@Service
public class CascadeService {

    private static final Logger log = LoggerFactory.getLogger(CascadeService.class);

    private final CascadeTaskRepository taskRepository;
    /** 自注入代理，确保 processOne 的 REQUIRES_NEW 在自调用时仍然生效。 */
    private final CascadeService self;

    public CascadeService(CascadeTaskRepository taskRepository, @Lazy CascadeService self) {
        this.taskRepository = taskRepository;
        this.self = self;
    }

    /** 必须在持有状态变化事务（且通常持有根授权锁）时调用。 */
    @Transactional
    public void enqueue(CascadeTask task) {
        boolean exists = taskRepository.existsByTargetGrantIdAndEventTypeAndSourceGrantId(
                task.getTargetGrant().getId(), task.getEventType(), task.getSourceGrant().getId());
        if (!exists) {
            taskRepository.save(task);
        }
    }

    /** 事务提交后尽力触发一次传播处理；无事务上下文时立即处理。 */
    public void triggerAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    processPending();
                }
            });
        } else {
            processPending();
        }
    }

    /**
     * 处理所有待处理/失败任务。每个任务独立事务，单个失败只记录错误，不影响其他任务与后续重试。
     */
    public int processPending() {
        int processed = 0;
        for (CascadeTaskStatus status : List.of(CascadeTaskStatus.PENDING, CascadeTaskStatus.FAILED)) {
            List<CascadeTask> tasks = taskRepository.findByStatusOrderById(status);
            for (CascadeTask task : tasks) {
                processed++;
                try {
                    self.processOne(task.getId());
                } catch (RuntimeException ex) {
                    // 错误已在独立事务中落库为 FAILED，等待下一轮重试，不中断整批处理
                    log.debug("级联任务 #{} 本轮处理未成功: {}", task.getId(), ex.getMessage());
                }
            }
        }
        return processed;
    }

    /** 重试单条任务（管理/调度入口）；任务不存在或已完成时为空操作。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processOne(Long taskId) {
        CascadeTask task = taskRepository.findByIdForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus() == CascadeTaskStatus.DONE) {
            return;
        }
        task.incrementAttempts();
        try {
            apply(task);
            task.markDone();
        } catch (RuntimeException ex) {
            log.warn("级联任务 #{} 处理失败（第 {} 次尝试）: {}",
                    task.getId(), task.getAttempts(), ex.getMessage());
            task.markFailed(ex.getMessage());
            throw ex;
        }
    }

    private void apply(CascadeTask task) {
        LicenseGrant target = task.getTargetGrant();
        if (target.getStatus() == GrantStatus.TERMINATED) {
            return;
        }
        LocalDate today = LocalDate.now();
        switch (task.getEventType()) {
            case REVOKED -> target.markTerminated(task.getDetail());
            case EXPIRED -> {
                if (target.getEndDate().isBefore(today)) {
                    target.markExpired(task.getDetail());
                } else {
                    // 上级已到期而下级期限尚未届满：失去权利来源，暂停
                    target.markSuspended(task.getDetail());
                }
            }
            case SCOPE_REDUCED, PARENT_SUSPENDED -> {
                LicenseGrant source = task.getSourceGrant();
                boolean stillCovered = ScopeRules.contains(
                        new ScopeRules.Scope(source.getStartDate(), source.getEndDate(),
                                source.getTerritories(), source.getMedia()),
                        new ScopeRules.Scope(target.getStartDate(), target.getEndDate(),
                                target.getTerritories(), target.getMedia()));
                if (stillCovered && source.getStatus() == GrantStatus.ACTIVE) {
                    // 缩减后的范围仍完整覆盖该下级（或缩减被再次放宽），无需改变其状态
                    return;
                }
                if (target.getEndDate().isBefore(today)) {
                    target.markExpired(task.getDetail());
                } else {
                    target.markSuspended(task.getDetail());
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CascadeTask> listTasks() {
        return taskRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public List<CascadeTask> listTasksForGrant(Long grantId) {
        return taskRepository.findByTargetGrantIdOrderById(grantId);
    }
}
