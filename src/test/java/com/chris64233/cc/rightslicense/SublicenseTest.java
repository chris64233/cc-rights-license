package com.chris64233.cc.rightslicense;

import com.chris64233.cc.rightslicense.domain.ApplicationStatus;
import com.chris64233.cc.rightslicense.domain.DecisionValue;
import com.chris64233.cc.rightslicense.domain.GrantStatus;
import com.chris64233.cc.rightslicense.domain.GrantPropagation;
import com.chris64233.cc.rightslicense.domain.HaltReason;
import com.chris64233.cc.rightslicense.domain.LicenseGrant;
import com.chris64233.cc.rightslicense.domain.LicenseType;
import com.chris64233.cc.rightslicense.domain.PropagationOperation;
import com.chris64233.cc.rightslicense.domain.SublicenseApplication;
import com.chris64233.cc.rightslicense.domain.SublicenseDecision;
import com.chris64233.cc.rightslicense.domain.SublicensePolicy;
import com.chris64233.cc.rightslicense.domain.Work;
import com.chris64233.cc.rightslicense.repo.GrantPropagationRepository;
import com.chris64233.cc.rightslicense.service.BusinessException;
import com.chris64233.cc.rightslicense.service.GrantLifecycleService;
import com.chris64233.cc.rightslicense.service.LicenseService;
import com.chris64233.cc.rightslicense.service.RightsTreeQueryService;
import com.chris64233.cc.rightslicense.service.SublicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SublicenseTest extends AbstractIntegrationTest {

    @Autowired
    private LicenseService licenseService;
    @Autowired
    private SublicenseService sublicenseService;
    @Autowired
    private GrantLifecycleService lifecycleService;
    @Autowired
    private RightsTreeQueryService treeQueryService;
    @Autowired
    private GrantPropagationRepository propagationRepository;
    @Autowired
    private TestFixtures fixtures;

    private static SublicensePolicy policy(int maxDepth) {
        return new SublicensePolicy(true, maxDepth, null, null, null, null);
    }

    private LicenseGrant rootGrant(Work work, String licensee, LicenseType type, SublicensePolicy policy) {
        return fixtures.approveRootGrant(work, licensee, type,
                "2026-01-01", "2026-12-31", List.of("CN", "JP"), List.of("TV", "WEB"), policy);
    }

    private SublicenseApplication requestSub(LicenseGrant parent, String no, String licensee,
                                             LicenseType type, String start, String end,
                                             List<String> territories, List<String> media,
                                             SublicensePolicy requested) {
        return sublicenseService.createApplication(parent.getGrantNo(), no, licensee, type,
                LocalDate.parse(start), LocalDate.parse(end), territories, media, requested);
    }

    private SublicenseApplication approveSub(LicenseGrant parent, String no, String licensee,
                                             LicenseType type, String start, String end,
                                             List<String> territories, List<String> media) {
        return approveSub(parent, no, licensee, type, start, end, territories, media,
                SublicensePolicy.forbidden());
    }

    private SublicenseApplication approveSub(LicenseGrant parent, String no, String licensee,
                                             LicenseType type, String start, String end,
                                             List<String> territories, List<String> media,
                                             SublicensePolicy policy) {
        SublicenseApplication app = requestSub(parent, no, licensee, type,
                start, end, territories, media, policy);
        sublicenseService.submitDecision(app.getId(), parent.getLicensee(),
                DecisionValue.APPROVE, "evt-" + no);
        return sublicenseService.getApplication(app.getId());
    }

    // ---------- 1. 声明与申请前置校验 ----------

    @Test
    void rootPolicyMustDeclareDepthWithinItsOwnScope() {
        Work work = fixtures.createWork("100");
        // maxDepth < 2 无意义
        assertThatThrownBy(() -> fixtures.approveRootGrant(work, "甲", LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-12-31", List.of("CN"), List.of("TV"),
                new SublicensePolicy(true, 1, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最大层级");
        // 可转授期限超出授权期限
        assertThatThrownBy(() -> licenseService.createApplication(work.getCode(), "乙",
                LicenseType.NON_EXCLUSIVE,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of("CN"), List.of("TV"),
                new SublicensePolicy(true, 2, null, null,
                        LocalDate.parse("2025-12-01"), LocalDate.parse("2026-12-31"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可转授期限");
        // 可转授地域超出授权地域
        assertThatThrownBy(() -> licenseService.createApplication(work.getCode(), "丙",
                LicenseType.NON_EXCLUSIVE,
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                List.of("CN"), List.of("TV"),
                new SublicensePolicy(true, 2, List.of("US"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可转授地域");
    }

    @Test
    void nonSublicensableGrantRejectsSublicenseRequest() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, SublicensePolicy.forbidden());
        assertThatThrownBy(() -> requestSub(root, "SUB-1", "乙", LicenseType.EXCLUSIVE,
                "2026-02-01", "2026-03-31", List.of("CN"), List.of("TV"),
                SublicensePolicy.forbidden()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未声明允许转授权");
    }

    @Test
    void sublicenseScopeMustFallEntirelyWithinParent() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(3));

        // 期限超出
        assertThatThrownBy(() -> requestSub(root, "SUB-DATE", "乙", LicenseType.EXCLUSIVE,
                "2025-12-01", "2026-03-31", List.of("CN"), List.of("TV"),
                SublicensePolicy.forbidden()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("期限");
        // 地域超出
        assertThatThrownBy(() -> requestSub(root, "SUB-TERR", "乙", LicenseType.EXCLUSIVE,
                "2026-02-01", "2026-03-31", List.of("US"), List.of("TV"),
                SublicensePolicy.forbidden()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("地域");
        // 媒介超出
        assertThatThrownBy(() -> requestSub(root, "SUB-MEDIA", "乙", LicenseType.EXCLUSIVE,
                "2026-02-01", "2026-03-31", List.of("CN"), List.of("RADIO"),
                SublicensePolicy.forbidden()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("媒介");
    }

    @Test
    void nonExclusiveParentCannotGrantExclusive() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.NON_EXCLUSIVE, policy(3));
        // 创建期直接拒绝
        assertThatThrownBy(() -> requestSub(root, "SUB-X", "乙", LicenseType.EXCLUSIVE,
                "2026-02-01", "2026-03-31", List.of("CN"), List.of("TV"),
                SublicensePolicy.forbidden()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非独占");
    }

    @Test
    void sublicenseNumberCannotUseReservedRootPrefix() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(3));
        assertThatThrownBy(() -> requestSub(root, "G-999", "乙",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-03-31",
                List.of("CN"), List.of("TV"), SublicensePolicy.forbidden()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("保留前缀");
    }

    @Test
    void maxDepthIsEnforcedAcrossLevels() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(2));
        SublicenseApplication level2 = approveSub(root, "SUB-D2", "乙",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-03-31", List.of("CN"), List.of("TV"));
        LicenseGrant child = treeQueryService.getGrant(level2.getChildGrant().getGrantNo());
        assertThat(child.getDepth()).isEqualTo(2);
        assertThat(child.isSublicensable()).isFalse();

        // 即便手工构造一个允许继续转授的请求，也不能突破第 2 层上限
        assertThatThrownBy(() -> requestSub(child, "SUB-D3", "丙", LicenseType.NON_EXCLUSIVE,
                "2026-02-01", "2026-03-31", List.of("CN"), List.of("TV"), policy(3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最大层级");
    }

    // ---------- 2. 独占冲突与原子校验 ----------

    @Test
    void exclusiveParentExclusiveChildBlocksSiblingsButNonExclusiveCoexist() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(4));

        // 独占下级
        SublicenseApplication exclusive = approveSub(root, "SUB-E1", "乙",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-03-31", List.of("CN"), List.of("TV"));
        assertThat(exclusive.getStatus()).isEqualTo(ApplicationStatus.APPROVED);

        // 与独占下级重叠的非独占申请 → CONFLICT
        SublicenseApplication nonOverlap = requestSub(root, "SUB-N1", "丙",
                LicenseType.NON_EXCLUSIVE, "2026-02-15", "2026-02-20",
                List.of("CN"), List.of("TV"), SublicensePolicy.forbidden());
        sublicenseService.submitDecision(nonOverlap.getId(), root.getLicensee(),
                DecisionValue.APPROVE, "evt-N1");
        assertThat(sublicenseService.getApplication(nonOverlap.getId()).getStatus())
                .isEqualTo(ApplicationStatus.CONFLICT);

        // 与独占下级不相交（媒介不同）的非独占申请 → APPROVED
        SublicenseApplication disjoint = approveSub(root, "SUB-N2", "丁",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-03-31",
                List.of("CN"), List.of("WEB"));
        assertThat(disjoint.getStatus()).isEqualTo(ApplicationStatus.APPROVED);

        // 非独占之间并存：再来一个 WEB 非独占
        assertThat(approveSub(root, "SUB-N3", "戊",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-03-31",
                List.of("JP"), List.of("WEB")).getStatus())
                .isEqualTo(ApplicationStatus.APPROVED);

        // 冲突对象可查询
        assertThat(sublicenseService.findConflictingChildren(nonOverlap.getId()))
                .extracting(LicenseGrant::getGrantNo)
                .containsExactly("SUB-E1");
    }

    @Test
    void multiScopeApplicationIsValidatedAtomically() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(4));
        // 已有独占下级占据 CN×TV
        approveSub(root, "SUB-A", "乙", LicenseType.EXCLUSIVE,
                "2026-02-01", "2026-03-31", List.of("CN"), List.of("TV"));

        // 新申请含多个 地域×媒介，其中 JP×WEB 不冲突、但 CN×TV 冲突 → 整份 CONFLICT，不产生下级授权
        SublicenseApplication mixed = requestSub(root, "SUB-MIX", "丙", LicenseType.EXCLUSIVE,
                "2026-02-01", "2026-03-31", List.of("CN", "JP"), List.of("TV", "WEB"),
                SublicensePolicy.forbidden());
        sublicenseService.submitDecision(mixed.getId(), root.getLicensee(),
                DecisionValue.APPROVE, "evt-mix");
        SublicenseApplication result = sublicenseService.getApplication(mixed.getId());
        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.CONFLICT);
        assertThat(result.getChildGrant()).isNull();
        // 只有 SUB-A 一个下级
        assertThat(treeQueryService.getChildren(root.getGrantNo())).hasSize(1);
    }

    // ---------- 3. 幂等、审批人、版本记录 ----------

    @Test
    void sublicenseNumberIsIdempotentAndDecisionIsIdempotentByEvent() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(3));

        SublicenseApplication first = requestSub(root, "SUB-IDEM", "乙",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-03-31",
                List.of("CN"), List.of("TV"), SublicensePolicy.forbidden());
        // 相同转授权号、不同内容重复提交：返回首次申请
        SublicenseApplication replay = requestSub(root, "SUB-IDEM", "其他人",
                LicenseType.EXCLUSIVE, "2026-04-01", "2026-04-30",
                List.of("JP"), List.of("WEB"), SublicensePolicy.forbidden());
        assertThat(replay.getId()).isEqualTo(first.getId());
        assertThat(replay.getLicensee()).isEqualTo("乙");

        // 非当前被授权方不能审批
        assertThatThrownBy(() -> sublicenseService.submitDecision(first.getId(), "陌生人",
                DecisionValue.APPROVE, "evt-x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前被授权方");

        SublicenseDecision d1 = sublicenseService.submitDecision(first.getId(), root.getLicensee(),
                DecisionValue.APPROVE, "evt-dup");
        SublicenseDecision d2 = sublicenseService.submitDecision(first.getId(), root.getLicensee(),
                DecisionValue.APPROVE, "evt-dup");
        assertThat(d2.getId()).isEqualTo(d1.getId());
        assertThat(sublicenseService.listDecisions(first.getId())).hasSize(1);

        LicenseGrant child = treeQueryService.getGrant("SUB-IDEM");
        assertThat(child.getStatus()).isEqualTo(GrantStatus.ACTIVE);
        // 采用的上级版本为 v1，层级链 [root, child]
        assertThat(d1.getParentVersion()).isEqualTo(1);
        assertThat(child.getChain()).containsExactly(root.getId(), child.getId());
        assertThat(child.getAncestorPath()).isEqualTo("/" + root.getId() + "/");
    }

    @Test
    void approvedChildCarriesIntersectedPolicyAndVersionSnapshot() {
        Work work = fixtures.createWork("100");
        // 根允许转授到第 3 层、CN、TV、上半年
        SublicensePolicy rootPolicy = new SublicensePolicy(true, 3,
                List.of("CN"), List.of("TV"),
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"));
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, rootPolicy);

        // 下级在上级范围内声明更窄的再转授信封（最晚到 5 月底、maxDepth 仍为 3）→ 生效策略取交集
        SublicenseApplication app = requestSub(root, "SUB-CAP", "乙",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-05-31",
                List.of("CN"), List.of("TV"),
                new SublicensePolicy(true, 3, List.of("CN"), List.of("TV"),
                        LocalDate.parse("2026-02-01"), LocalDate.parse("2026-05-31")));
        sublicenseService.submitDecision(app.getId(), root.getLicensee(),
                DecisionValue.APPROVE, "evt-cap");
        LicenseGrant child = treeQueryService.getGrant("SUB-CAP");
        assertThat(child.getMaxDepth()).isEqualTo(3);
        assertThat(child.getSubTerritories()).containsExactly("CN");
        assertThat(child.getSubMedia()).containsExactly("TV");
        // 取更严的截止日
        assertThat(child.getSubEndDate()).isEqualTo(LocalDate.parse("2026-05-31"));
        assertThat(child.getSubStartDate()).isEqualTo(LocalDate.parse("2026-02-01"));
        assertThat(child.getCurrentVersion()).isEqualTo(1);

        // 超出上级声明的再转授信封在创建时即被拒绝
        assertThatThrownBy(() -> requestSub(root, "SUB-CAP2", "乙2",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-05-31",
                List.of("CN"), List.of("TV"),
                new SublicensePolicy(true, 4, List.of("CN"), List.of("TV"),
                        LocalDate.parse("2026-02-01"), LocalDate.parse("2026-05-31"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最大层级");
    }

    // ---------- 4. 级联：撤销/暂停/恢复/到期/范围缩减 ----------

    @Test
    void revokeCascadesToAllDescendantsWithoutOmission() {
        Work work = fixtures.createWork("100");
        // 一棵三层的树：root → c2 → c3，全部在撤销根后必须终止
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(4));
        LicenseGrant c2 = treeQueryService.getGrant(approveSub(root, "SUB-T2", "乙",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-06-30",
                List.of("CN"), List.of("TV"), policy(4)).getChildGrant().getGrantNo());
        LicenseGrant c3 = treeQueryService.getGrant(approveSub(c2, "SUB-T3", "丙",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-04-30",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());

        lifecycleService.revoke(root.getGrantNo(), "EVT-REVOKE-1");

        assertThat(treeQueryService.getGrant(root.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.TERMINATED);
        assertThat(treeQueryService.getGrant(c2.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.TERMINATED);
        assertThat(treeQueryService.getGrant(c3.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.TERMINATED);
        assertThat(treeQueryService.getGrant(c3.getGrantNo()).getHaltReason())
                .isEqualTo(HaltReason.PARENT_REVOKED);
        // 无遗漏：全部有效下级为零，待处理传播任务清零
        assertThat(treeQueryService.getDescendantsByStatus(root.getGrantNo(), GrantStatus.ACTIVE))
                .isEmpty();
        assertThat(lifecycleService.pendingPropagationCount()).isZero();

        // 撤销幂等：同一事件号重复提交不产生新影响
        lifecycleService.revoke(root.getGrantNo(), "EVT-REVOKE-1");
        assertThat(lifecycleService.pendingPropagationCount()).isZero();
    }

    @Test
    void suspendAndResumeCascadeThroughTree() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(4));
        LicenseGrant c2 = treeQueryService.getGrant(approveSub(root, "SUB-S2", "乙",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-06-30",
                List.of("CN"), List.of("TV"), policy(4)).getChildGrant().getGrantNo());
        LicenseGrant c3 = treeQueryService.getGrant(approveSub(c2, "SUB-S3", "丙",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-04-30",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());

        lifecycleService.suspend(root.getGrantNo(), "EVT-SUSP-1");
        assertThat(treeQueryService.getGrant(c2.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.SUSPENDED);
        assertThat(treeQueryService.getGrant(c3.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.SUSPENDED);
        // 暂停期间不得新增转授权
        assertThatThrownBy(() -> requestSub(c2, "SUB-S4", "丁", LicenseType.NON_EXCLUSIVE,
                "2026-03-01", "2026-03-15", List.of("CN"), List.of("TV"),
                SublicensePolicy.forbidden()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("SUSPENDED");

        lifecycleService.resume(root.getGrantNo(), "EVT-RES-1");
        assertThat(treeQueryService.getGrant(root.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);
        assertThat(treeQueryService.getGrant(c2.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);
        assertThat(treeQueryService.getGrant(c3.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);
    }

    @Test
    void expiryCascadesAndIsIdempotent() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = fixtures.approveRootGrant(work, "甲", LicenseType.EXCLUSIVE,
                "2026-01-01", "2026-02-28", List.of("CN"), List.of("TV"), policy(4));
        LicenseGrant c2 = treeQueryService.getGrant(approveSub(root, "SUB-E2", "乙",
                LicenseType.NON_EXCLUSIVE, "2026-01-15", "2026-02-15",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());

        // 第一阶段：仅下级到期（上级仍有效）→ 下级自身终止
        assertThat(lifecycleService.expireDueGrants(LocalDate.parse("2026-02-20"), "EXP1"))
                .isEqualTo(1);
        assertThat(treeQueryService.getGrant(c2.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.TERMINATED);
        assertThat(treeQueryService.getGrant(c2.getGrantNo()).getHaltReason())
                .isEqualTo(HaltReason.EXPIRED);
        assertThat(treeQueryService.getGrant(root.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);

        // 第二阶段：上级到期 → 上级终止（无活跃下级需要级联）
        assertThat(lifecycleService.expireDueGrants(LocalDate.parse("2026-03-01"), "EXP2"))
                .isEqualTo(1);
        assertThat(treeQueryService.getGrant(root.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.TERMINATED);

        // 再扫一次：幂等，不重复处理
        assertThat(lifecycleService.expireDueGrants(LocalDate.parse("2026-03-02"), "EXP3"))
                .isZero();
    }

    @Test
    void scopeReductionTerminatesOnlyChildrenOutsideNewScope() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(4));
        // 落在 CN×TV 上半年 的下级
        LicenseGrant keep = treeQueryService.getGrant(approveSub(root, "SUB-KEEP", "乙",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-03-31",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());
        // JP 的下级：地域被缩减掉后应终止
        LicenseGrant dropT = treeQueryService.getGrant(approveSub(root, "SUB-DROP-T", "丙",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-03-31",
                List.of("JP"), List.of("TV")).getChildGrant().getGrantNo());
        // 下半年的下级：期限被缩减掉后应终止
        LicenseGrant dropD = treeQueryService.getGrant(approveSub(root, "SUB-DROP-D", "丁",
                LicenseType.NON_EXCLUSIVE, "2026-09-01", "2026-10-31",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());

        lifecycleService.reduceScope(root.getGrantNo(), "EVT-REDUCE-1",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"),
                List.of("CN"), List.of("TV", "WEB"));

        LicenseGrant reloaded = treeQueryService.getGrant(root.getGrantNo());
        assertThat(reloaded.getCurrentVersion()).isEqualTo(2);
        assertThat(reloaded.getTerritories()).containsExactly("CN");
        assertThat(treeQueryService.getGrant(keep.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);
        assertThat(treeQueryService.getGrant(dropT.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.TERMINATED);
        assertThat(treeQueryService.getGrant(dropD.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.TERMINATED);
        assertThat(treeQueryService.getGrant(dropT.getGrantNo()).getHaltReason())
                .isEqualTo(HaltReason.PARENT_SCOPE_REDUCED);

        // 范围不能扩大：期限保持在现版本内，仅尝试把地域加回 JP → 被拒
        assertThatThrownBy(() -> lifecycleService.reduceScope(root.getGrantNo(), "EVT-REDUCE-2",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-06-30"),
                List.of("CN", "JP"), List.of("TV")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子集");
    }

    // ---------- 5. 并发：上级状态变化不得让新申请逃逸 ----------

    @Test
    void concurrentApprovalAndParentRevokeCannotEscape() throws Exception {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(3));
        String rootNo = root.getGrantNo();
        String licensee = root.getLicensee();
        SublicenseApplication app = requestSub(root, "SUB-CONC", "乙",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-03-31",
                List.of("CN"), List.of("TV"), SublicensePolicy.forbidden());
        Long appId = app.getId();

        var ready = new java.util.concurrent.CountDownLatch(2);
        var go = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var approve = pool.submit(() -> {
            ready.countDown();
            await(go);
            sublicenseService.submitDecision(appId, licensee,
                    DecisionValue.APPROVE, "evt-conc");
        });
        var revoke = pool.submit(() -> {
            ready.countDown();
            await(go);
            lifecycleService.revoke(rootNo, "EVT-CONC-REVOKE");
        });
        assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        go.countDown();
        approve.get(30, java.util.concurrent.TimeUnit.SECONDS);
        revoke.get(30, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        // 无论谁先拿到上级锁，都不得存在"上级已终止而下级仍有效"的逃逸状态
        LicenseGrant endRoot = treeQueryService.getGrant(rootNo);
        assertThat(endRoot.getStatus()).isEqualTo(GrantStatus.TERMINATED);
        SublicenseApplication endApp = sublicenseService.getApplication(appId);
        if (endApp.getStatus() == ApplicationStatus.APPROVED) {
            LicenseGrant child = treeQueryService.getGrant(endApp.getChildGrant().getGrantNo());
            assertThat(child.getStatus()).isEqualTo(GrantStatus.TERMINATED);
        } else {
            assertThat(endApp.getStatus()).isEqualTo(ApplicationStatus.CONFLICT);
        }
        assertThat(treeQueryService.getDescendantsByStatus(rootNo, GrantStatus.ACTIVE))
                .isEmpty();
    }

    @Test
    void resumeIsBlockedWhenExclusiveSiblingGrantedDuringSuspension() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(4));
        // 独占下级 A
        LicenseGrant a = treeQueryService.getGrant(approveSub(root, "SUB-A", "乙",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-06-30",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());
        // 暂停 A
        lifecycleService.suspend(a.getGrantNo(), "EVT-SA");
        assertThat(treeQueryService.getGrant(a.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.SUSPENDED);

        // A 暂停期间，在同一上级下尝试授出与之重叠的独占 B：独占兄弟冲突规则不看状态以外因素，
        // 但 A 已暂停（非 ACTIVE），不阻塞 B，故 B 可以生效
        LicenseGrant b = treeQueryService.getGrant(approveSub(root, "SUB-B", "丙",
                LicenseType.EXCLUSIVE, "2026-03-01", "2026-04-30",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());
        assertThat(b.getStatus()).isEqualTo(GrantStatus.ACTIVE);

        // 恢复 A：与有效独占兄弟 B 冲突，A 必须保持暂停（不得恢复出两个重叠独占）
        lifecycleService.resume(a.getGrantNo(), "EVT-RA");
        LicenseGrant aAfter = treeQueryService.getGrant(a.getGrantNo());
        assertThat(aAfter.getStatus()).isEqualTo(GrantStatus.SUSPENDED);
        assertThat(aAfter.getHaltReason()).isEqualTo(HaltReason.RESUME_CONFLICT);

        // 撤销 B 消除冲突后，对 A 再发起一次恢复，守卫放行使 A 恢复
        lifecycleService.revoke(b.getGrantNo(), "EVT-RB");
        lifecycleService.resume(a.getGrantNo(), "EVT-RA2");
        assertThat(treeQueryService.getGrant(a.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // ---------- 6. 权利树与有效范围查询 ----------

    @Test
    void rightsTreeChainAndEffectiveScopeAreQueryable() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(4));
        LicenseGrant c2 = treeQueryService.getGrant(approveSub(root, "SUB-Q2", "乙",
                LicenseType.EXCLUSIVE, "2026-02-01", "2026-06-30",
                List.of("CN"), List.of("TV"), policy(4)).getChildGrant().getGrantNo());
        LicenseGrant c3 = treeQueryService.getGrant(approveSub(c2, "SUB-Q3", "丙",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-04-30",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());

        List<LicenseGrant> subtree = treeQueryService.getSubtree(root.getGrantNo());
        assertThat(subtree).extracting(LicenseGrant::getGrantNo)
                .containsExactly(root.getGrantNo(), "SUB-Q2", "SUB-Q3");
        assertThat(treeQueryService.getChain(c3.getGrantNo()))
                .extracting(LicenseGrant::getGrantNo)
                .containsExactly(root.getGrantNo(), "SUB-Q2", "SUB-Q3");
        assertThat(treeQueryService.getChildren(root.getGrantNo()))
                .extracting(LicenseGrant::getGrantNo).containsExactly("SUB-Q2");

        // 终止 c2 后有效下级只剩根视角查询中的状态过滤
        lifecycleService.revoke(c2.getGrantNo(), "EVT-Q-REVOKE");
        assertThat(treeQueryService.getDescendantsByStatus(root.getGrantNo(), GrantStatus.ACTIVE))
                .isEmpty();
        assertThat(treeQueryService.getDescendantsByStatus(root.getGrantNo(), GrantStatus.TERMINATED))
                .extracting(LicenseGrant::getGrantNo)
                .containsExactlyInAnyOrder("SUB-Q2", "SUB-Q3");
    }

    @Test
    void revokeReachesEverySiblingWithoutOmission() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(3));
        List<String> siblingNos = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            String no = "SUB-B" + i;
            siblingNos.add(no);
            approveSub(root, no, "被授权方" + i, LicenseType.NON_EXCLUSIVE,
                    "2026-02-01", "2026-06-30", List.of("CN", "JP"), List.of("TV", "WEB"));
        }

        lifecycleService.revoke(root.getGrantNo(), "EVT-BULK-1");

        assertThat(treeQueryService.getChildren(root.getGrantNo()))
                .hasSize(5)
                .allSatisfy(child ->
                        assertThat(child.getStatus()).isEqualTo(GrantStatus.TERMINATED));
        assertThat(treeQueryService.getDescendantsByStatus(root.getGrantNo(), GrantStatus.ACTIVE))
                .isEmpty();
        assertThat(lifecycleService.pendingPropagationCount()).isZero();
    }

    @Test
    void pendingPropagationCanBeRetriedAndIsIdempotent() {
        Work work = fixtures.createWork("100");
        LicenseGrant root = rootGrant(work, "甲", LicenseType.EXCLUSIVE, policy(3));
        LicenseGrant child = treeQueryService.getGrant(approveSub(root, "SUB-R", "乙",
                LicenseType.NON_EXCLUSIVE, "2026-02-01", "2026-06-30",
                List.of("CN"), List.of("TV")).getChildGrant().getGrantNo());

        // 模拟一条"已落库但尚未应用"的传播任务（与触发事务保证不遗漏的语义一致）
        GrantPropagation manual = new GrantPropagation(root, child,
                PropagationOperation.SUSPEND, HaltReason.PARENT_SUSPENDED);
        propagationRepository.save(manual);
        assertThat(lifecycleService.pendingPropagationCount()).isEqualTo(1);
        assertThat(treeQueryService.getGrant(child.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.ACTIVE);

        // 重试后生效
        assertThat(lifecycleService.retryPendingPropagations()).isEqualTo(1);
        assertThat(treeQueryService.getGrant(child.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.SUSPENDED);
        assertThat(lifecycleService.pendingPropagationCount()).isZero();

        // 再次重试：没有待处理任务，结果不变（幂等）
        assertThat(lifecycleService.retryPendingPropagations()).isZero();
        assertThat(treeQueryService.getGrant(child.getGrantNo()).getStatus())
                .isEqualTo(GrantStatus.SUSPENDED);
    }
}
