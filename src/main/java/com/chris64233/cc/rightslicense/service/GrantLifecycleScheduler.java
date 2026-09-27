package com.chris64233.cc.rightslicense.service;

import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.repo.LicenseGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 到期巡检与失败任务补偿重试，保证级联传播「可重试、不遗漏」。
 */
@Component
public class GrantLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(GrantLifecycleScheduler.class);

    private final LicenseGrantRepository grantRepository;
    private final SublicenseService sublicenseService;
    private final CascadeService cascadeService;

    public GrantLifecycleScheduler(LicenseGrantRepository grantRepository,
                                   SublicenseService sublicenseService,
                                   CascadeService cascadeService) {
        this.grantRepository = grantRepository;
        this.sublicenseService = sublicenseService;
        this.cascadeService = cascadeService;
    }

    /** 扫描已到期但尚未到期处理的授权，逐条在独立事务中到期并级联下级。 */
    @Scheduled(cron = "${rights.cascade.expiry-cron:0 5 0 * * *}")
    public void expireDueGrants() {
        LocalDate today = LocalDate.now();
        List<Long> dueIds = grantRepository.findExpired(
                        List.of(GrantStatus.ACTIVE, GrantStatus.SUSPENDED), today)
                .stream().map(LicenseGrant::getId).toList();
        for (Long grantId : dueIds) {
            try {
                sublicenseService.expireGrant(grantId);
            } catch (RuntimeException ex) {
                log.warn("授权 #{} 到期处理失败，等待下一轮巡检: {}", grantId, ex.getMessage());
            }
        }
        if (!dueIds.isEmpty()) {
            log.info("到期巡检：{} 条授权进入到期流程", dueIds.size());
        }
    }

    /** 周期性重试未完成的级联任务。 */
    @Scheduled(fixedDelayString = "${rights.cascade.retry-delay-ms:60000}")
    public void retryPendingTasks() {
        cascadeService.processPending();
    }
}
