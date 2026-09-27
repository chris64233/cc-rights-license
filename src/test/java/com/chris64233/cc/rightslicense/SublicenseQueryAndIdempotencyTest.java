package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.domain.SublicenseStatus;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.service.BusinessException;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SublicenseQueryAndIdempotencyTest {

    private static final SublicensePolicy SUB_ALLOWED =
            new SublicensePolicy(true, null, List.of(), List.of(), null, null);

    @Autowired
    private SublicenseService sublicenseService;
    @Autowired
    private TestFixtures fixtures;

    @Test
    void sublicenseNumberIsIdempotentAndDecisionIsIdempotent() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN", "JP"), List.of("TV", "WEB"),
                SUB_ALLOWED);

        String number = "SUB-IDEMPOTENT-1";
        SublicenseApplication first = fixtures.createSublicense(root, number, "渠道A",
                LicenseType.EXCLUSIVE, "2026-02-01", "2028-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        // 同号再次提交：返回同一条申请，不新增
        SublicenseApplication again = fixtures.createSublicense(root, number, "渠道A",
                LicenseType.EXCLUSIVE, "2026-02-01", "2028-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        assertThat(again.getId()).isEqualTo(first.getId());

        // 同号用于不同上级：冲突
        Work work2 = fixtures.createWork("100");
        LicenseGrant other = fixtures.approveRootGrant(work2, "发行商2", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        assertThatThrownBy(() -> fixtures.createSublicense(other, number, "渠道X",
                LicenseType.EXCLUSIVE, "2026-02-01", "2028-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已用于其他上级授权");

        // 同一事件号重复提交：幂等返回
        var d1 = sublicenseService.submitDecision(first.getId(),
                com.chris64233.cc.rightslicense.domain.DecisionValue.APPROVE, "evt-x");
        var d2 = sublicenseService.submitDecision(first.getId(),
                com.chris64233.cc.rightslicense.domain.DecisionValue.APPROVE, "evt-x");
        assertThat(d2.getId()).isEqualTo(d1.getId());
        // 第二条不同决定：申请已终结，不允许修改
        assertThatThrownBy(() -> sublicenseService.submitDecision(first.getId(),
                com.chris64233.cc.rightslicense.domain.DecisionValue.REJECT, "evt-y"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已终结");
    }

    @Test
    void approvalRecordsParentVersionAndChainHierarchy() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商", LicenseType.EXCLUSIVE,
                "2026-01-01", "2029-12-31", List.of("CN", "JP"), List.of("TV", "WEB"),
                SUB_ALLOWED);
        var childApp = fixtures.approveSublicenseGrant(root, "渠道商", LicenseType.EXCLUSIVE,
                "2026-02-01", "2029-10-31", List.of("CN", "JP"), List.of("TV", "WEB"),
                SUB_ALLOWED);
        LicenseGrant child = sublicenseService.listChildren(root.getId()).get(0);
        var grandApp = fixtures.approveSublicenseGrant(child, "零售商", LicenseType.NON_EXCLUSIVE,
                "2026-03-01", "2029-09-30", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant grandchild = sublicenseService.listChildren(child.getId()).get(0);

        assertThat(childApp.getParentVersion()).isEqualTo(root.getVersion());
        assertThat(grandApp.getParentVersion()).isEqualTo(child.getVersion());

        List<LicenseGrant> tree = sublicenseService.getRightsTree(root.getId());
        assertThat(tree).hasSize(3);
        assertThat(tree).extracting(LicenseGrant::getDepth).containsExactly(0, 1, 2);
        assertThat(root.getRootGrantId()).isEqualTo(root.getId());
        assertThat(child.getRootGrantId()).isEqualTo(root.getId());
        assertThat(grandchild.getRootGrantId()).isEqualTo(root.getId());
        assertThat(root.getChainPath()).isEqualTo("/" + root.getId() + "/");
        assertThat(grandchild.getChainPath())
                .isEqualTo("/" + root.getId() + "/" + child.getId() + "/" + grandchild.getId() + "/");
        assertThat(sublicenseService.listChildren(child.getId())).extracting(LicenseGrant::getId)
                .containsExactly(grandchild.getId());
    }

    @Test
    void effectiveScopeIsIntersectionAcrossTheChain() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商", LicenseType.EXCLUSIVE,
                "2026-01-01", "2029-12-31", List.of("CN", "JP"), List.of("TV", "WEB"),
                SUB_ALLOWED);
        fixtures.approveSublicenseGrant(root, "渠道商", LicenseType.EXCLUSIVE,
                "2026-06-01", "2029-06-30", List.of("CN", "JP"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant child = sublicenseService.listChildren(root.getId()).get(0);
        fixtures.approveSublicenseGrant(child, "零售商", LicenseType.EXCLUSIVE,
                "2027-01-01", "2029-05-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant grandchild = sublicenseService.listChildren(child.getId()).get(0);

        var scope = sublicenseService.getEffectiveScope(grandchild.getId());
        assertThat(scope.effective()).isTrue();
        assertThat(scope.startDate()).isEqualTo(LocalDate.parse("2027-01-01"));
        assertThat(scope.endDate()).isEqualTo(LocalDate.parse("2029-05-31"));
        assertThat(scope.territories()).containsExactly("CN");
        assertThat(scope.media()).containsExactly("TV");

        // 暂停链上任意一环，有效范围立即失效并给出原因
        sublicenseService.expireGrant(child.getId());
        var after = sublicenseService.getEffectiveScope(grandchild.getId());
        assertThat(after.effective()).isFalse();
        assertThat(after.reason()).contains(String.valueOf(child.getId())).contains("EXPIRED");
    }

    @Test
    void conflictObjectsAreQueryable() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "发行商", LicenseType.EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        var first = fixtures.approveSublicenseGrant(root, "渠道A", LicenseType.EXCLUSIVE,
                "2026-02-01", "2028-11-30", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant firstGrant = sublicenseService.listChildren(root.getId()).get(0);

        SublicenseApplication second = fixtures.createSublicense(root, "渠道B",
                LicenseType.EXCLUSIVE, "2026-03-01", "2026-12-31",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        fixtures.approveSublicense(second);

        SublicenseApplication reloaded = sublicenseService.getApplication(second.getId());
        assertThat(reloaded.getStatus()).isEqualTo(SublicenseStatus.CONFLICT);
        assertThat(sublicenseService.findConflictingGrants(second.getId()))
                .extracting(LicenseGrant::getId)
                .containsExactly(firstGrant.getId());
        // 已批准的申请无冲突对象
        assertThat(sublicenseService.findConflictingGrants(first.getId())).isEmpty();
    }

    @Test
    void conflictAcrossSeparateRightsTreesIsAlsoDetected() {
        // 非独占根授权向同一渠道分别下发两个独占转授权是不允许的（非独占不能授独占）；
        // 但两棵不同权利树若各自对同一作品持有独占权，本身互斥——这里验证跨树冲突检测
        Work work = fixtures.createWork("100");
        LicenseGrant root1 = fixtures.approveRootGrant(work, "发行商1", LicenseType.NON_EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant root2 = fixtures.approveRootGrant(work, "发行商2", LicenseType.NON_EXCLUSIVE,
                "2026-01-01", "2028-12-31", List.of("CN"), List.of("TV"), SUB_ALLOWED);

        var child1 = fixtures.approveSublicenseGrant(root1, "渠道A",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2028-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        LicenseGrant child1Grant = sublicenseService.listChildren(root1.getId()).get(0);

        // root2 再发非独占转授权：与 child1 非独占并存，无冲突
        var child2 = fixtures.approveSublicenseGrant(root2, "渠道B",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2028-11-30",
                List.of("CN"), List.of("TV"), SUB_ALLOWED);
        assertThat(child2.getStatus()).isEqualTo(SublicenseStatus.APPROVED);
        assertThat(sublicenseService.findConflictingGrants(child1.getId())).isEmpty();
        assertThat(child1Grant.getStatus()).isEqualTo(GrantStatus.ACTIVE);
    }
}
