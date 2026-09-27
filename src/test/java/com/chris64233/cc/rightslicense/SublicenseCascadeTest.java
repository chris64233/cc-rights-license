package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.CascadeTask;
import com.chris64233.cc.rightslicense.domain.CascadeTaskStatus;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.CascadeService;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SublicenseCascadeTest {

    private static final SublicensePolicy SUB_ALLOWED =
            new SublicensePolicy(true, null, List.of(), List.of(), null, null);

    @Autowired
    private SublicenseService sublicenseService;
    @Autowired
    private CascadeService cascadeService;
    @Autowired
    private TestFixtures fixtures;

    private List<LicenseGrant> threeLevelTree() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN", "JP"), List.of("TV", "WEB"),
                SUB_ALLOWED);
        var childApp = fixtures.approveSublicenseGrant(root, "渠道商", LicenseType.EXCLUSIVE,
                "2026-02-01", "2028-11-30", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant child = sublicenseService.listChildren(root.getId()).get(0);
        fixtures.approveSublicenseGrant(child, "零售商", LicenseType.EXCLUSIVE,
                "2026-03-01", "2028-10-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant grandchild = sublicenseService.listChildren(child.getId()).get(0);
        assertThat(childApp.getParentVersion()).isEqualTo(root.getVersion());
        return List.of(root, child, grandchild);
    }

    @Test
    void revokingParentTerminatesAllDescendants() {
        List<LicenseGrant> tree = threeLevelTree();
        LicenseGrant root = tree.get(0);

        sublicenseService.revokeGrant(root.getId());

        List<LicenseGrant> after = sublicenseService.getRightsTree(root.getId());
        assertThat(after).extracting(LicenseGrant::getStatus)
                .containsOnly(GrantStatus.TERMINATED);
        assertThat(after.get(0).getStatusReason()).contains("撤销");
        assertThat(after.get(2).getStatusReason()).contains("撤销");
    }

    @Test
    void scopeReductionSuspendsDescendantsNoLongerCoveredAndKeepsCoveredOnes() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN", "JP"), List.of("TV", "WEB"),
                SUB_ALLOWED);
        fixtures.approveSublicenseGrant(root, "渠道CN", LicenseType.EXCLUSIVE,
                "2026-02-01", "2028-11-30", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        fixtures.approveSublicenseGrant(root, "渠道JP", LicenseType.EXCLUSIVE,
                "2026-02-01", "2028-11-30", List.of("JP"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant cnChild = sublicenseService.getRightsTree(root.getId()).stream()
                .filter(g -> g.getLicensee().equals("渠道CN")).findFirst().orElseThrow();
        LicenseGrant jpChild = sublicenseService.getRightsTree(root.getId()).stream()
                .filter(g -> g.getLicensee().equals("渠道JP")).findFirst().orElseThrow();

        // 把根授权地域缩到 CN：JP 下级失去覆盖进入暂停，CN 下级保持有效
        sublicenseService.reduceScope(root.getId(),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2028-12-31"),
                List.of("CN"), List.of("TV", "WEB"));

        assertThat(sublicenseService.getGrant(cnChild.getId()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);
        assertThat(sublicenseService.getGrant(jpChild.getId()).getStatus())
                .isEqualTo(GrantStatus.SUSPENDED);
        assertThat(sublicenseService.getGrant(jpChild.getId()).getStatusReason())
                .contains("缩减");
    }

    @Test
    void expirySuspendsDescendantsWhoseTermOutlivesTheParent() {
        List<LicenseGrant> tree = threeLevelTree();
        LicenseGrant child = tree.get(1);
        LicenseGrant grandchild = tree.get(2);

        sublicenseService.expireGrant(child.getId());

        // 直接到期：EXPIRED；其下级期限尚未届满：SUSPENDED
        assertThat(sublicenseService.getGrant(child.getId()).getStatus())
                .isEqualTo(GrantStatus.EXPIRED);
        assertThat(sublicenseService.getGrant(grandchild.getId()).getStatus())
                .isEqualTo(GrantStatus.SUSPENDED);
        // 根授权不受影响
        assertThat(sublicenseService.getGrant(tree.get(0).getId()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);
    }

    @Test
    void cascadeTasksCoverEveryDescendantAndAreRetryableUntilDone() {
        List<LicenseGrant> tree = threeLevelTree();
        LicenseGrant root = tree.get(0);

        sublicenseService.revokeGrant(root.getId());

        List<CascadeTask> tasks = cascadeService.listTasks().stream()
                .filter(t -> t.getSourceGrant().getId().equals(root.getId()))
                .toList();
        // root 之外的全部下级（child + grandchild）各一条
        assertThat(tasks).hasSize(2);
        assertThat(tasks).extracting(t -> t.getTargetGrant().getId())
                .containsExactlyInAnyOrder(tree.get(1).getId(), tree.get(2).getId());
        assertThat(tasks).extracting(CascadeTask::getStatus)
                .containsOnly(CascadeTaskStatus.DONE);

        // 重复触发不产生新任务、不报错
        cascadeService.processPending();
        assertThat(cascadeService.listTasks().stream()
                .filter(t -> t.getSourceGrant().getId().equals(root.getId()))
                .toList()).hasSize(2);
    }

    @Test
    void failedTaskIsRetriedAndDoesNotBlockOtherTasks() {
        List<LicenseGrant> tree = threeLevelTree();
        LicenseGrant child = tree.get(1);
        LicenseGrant grandchild = tree.get(2);

        // 模拟上次传播中断：grandchild 的「上级范围缩减」任务停留在 FAILED
        CascadeTask failed = new CascadeTask(grandchild, child,
                com.chris64233.cc.rightslicense.domain.CascadeEventType.SCOPE_REDUCED,
                "模拟历史失败的范围缩减传播");
        failed.markFailed("模拟处理异常");
        persistFailedTask(failed);
        CascadeTask stored = cascadeService.listTasksForGrant(grandchild.getId()).stream()
                .filter(t -> t.getStatus() == CascadeTaskStatus.FAILED)
                .findFirst().orElseThrow();
        assertThat(stored.getAttempts()).isZero();

        // 真正缩减 child 期限（不再覆盖 grandchild）：相同事件任务已存在，不会重复入队；
        // 补偿处理重试历史失败任务，状态最终收敛
        sublicenseService.reduceScope(child.getId(),
                LocalDate.parse("2026-02-01"), LocalDate.parse("2027-12-31"),
                List.of("CN"), List.of("TV"));
        cascadeService.processPending();

        CascadeTask retried = cascadeService.listTasksForGrant(grandchild.getId()).stream()
                .filter(t -> t.getId().equals(stored.getId())).findFirst().orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(CascadeTaskStatus.DONE);
        assertThat(retried.getAttempts()).isEqualTo(1);
        assertThat(retried.getLastError()).isNull();
        assertThat(sublicenseService.getGrant(grandchild.getId()).getStatus())
                .isEqualTo(GrantStatus.SUSPENDED);
    }

    @Autowired
    private com.chris64233.cc.rightslicense.repo.CascadeTaskRepository taskRepository;

    private void persistFailedTask(CascadeTask task) {
        taskRepository.save(task);
    }
}
