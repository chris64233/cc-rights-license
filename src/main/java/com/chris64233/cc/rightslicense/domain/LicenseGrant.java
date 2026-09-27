package com.chris64233.cc.rightslicense.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 一条已生效的授权。根授权来自权利人共同批准的 {@link LicenseApplication}；
 * 下级授权（转授权）来自上级被授权方批准的 {@link SublicenseApplication}。
 *
 * <p>层级通过 {@code parent}（直接上级）与 {@code depth}（根为 1）表达；
 * {@code ancestorPath} 为只含祖先的物化路径（根为 "/"，直接下级为 "/{rootId}/"），
 * 配合 {@link #descendantPrefix()} 做整棵子树查询；{@code chain} 保存含自身的完整层级链。
 * 授权范围发生缩减时写入新版本（{@link GrantVersion}），当前字段始终表示最新有效范围。
 */
@Entity
@Table(name = "license_grants",
        uniqueConstraints = @UniqueConstraint(columnNames = "grant_no"))
public class LicenseGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 授权号：根授权批准后按 G-{id} 生成；转授权使用申请方提供的转授权号，全局唯一、保证幂等 */
    @Column(name = "grant_no", nullable = false, length = 100)
    private String grantNo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", unique = true)
    private LicenseApplication application;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_application_id", unique = true)
    private SublicenseApplication subApplication;

    /** 直接上级授权；根授权为 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_grant_id")
    private LicenseGrant parent;

    /** 上级链，根在前自身在末，例如 [rootId, parentId, id] */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_chain",
            joinColumns = @JoinColumn(name = "grant_id"))
    @OrderColumn(name = "idx")
    @Column(name = "ancestor_id", nullable = false)
    private List<Long> chain = new ArrayList<>();

    /** 物化祖先路径（只含祖先）：根为 "/"，直接下级为 "/{rootId}/"，第 3 层为 "/{rootId}/{parentId}/" */
    @Column(name = "ancestor_path", nullable = false, length = 2000)
    private String ancestorPath;

    @Column(nullable = false)
    private int depth;

    @Column(nullable = false, length = 200)
    private String licensee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LicenseType type;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_territories",
            joinColumns = @JoinColumn(name = "grant_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", nullable = false, length = 100)
    private List<String> territories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_media",
            joinColumns = @JoinColumn(name = "grant_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", nullable = false, length = 100)
    private List<String> media = new ArrayList<>();

    // ---- 转授权声明（本级持权方可以再向他人授予什么） ----

    @Column(nullable = false)
    private boolean sublicensable;

    private Integer maxDepth;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_sub_territories",
            joinColumns = @JoinColumn(name = "grant_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", length = 100)
    private List<String> subTerritories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_sub_media",
            joinColumns = @JoinColumn(name = "grant_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", length = 100)
    private List<String> subMedia = new ArrayList<>();

    private LocalDate subStartDate;

    private LocalDate subEndDate;

    @Column(nullable = false)
    private int currentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrantStatus status = GrantStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private HaltReason haltReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant statusChangedAt;

    protected LicenseGrant() {
    }

    /** 根授权：来自权利人共同批准的原始申请 */
    public LicenseGrant(LicenseApplication application) {
        this.application = application;
        this.work = application.getWork();
        this.parent = null;
        this.depth = 1;
        this.licensee = application.getLicensee();
        this.grantNo = "G-" + application.getId();
        this.ancestorPath = "/";
        applyCore(application.getType(), application.getStartDate(), application.getEndDate(),
                application.getTerritories(), application.getMedia());
        applyPolicy(application.isSublicensable(), application.getMaxDepth(),
                application.getSubTerritories(), application.getSubMedia(),
                application.getSubStartDate(), application.getSubEndDate());
        this.createdAt = Instant.now();
    }

    /** 下级授权：来自上级被授权方批准的转授权申请；策略由上级按其声明裁剪 */
    public LicenseGrant(SublicenseApplication subApplication, LicenseGrant parent,
                        SublicensePolicy effectivePolicy) {
        this.subApplication = subApplication;
        this.application = null;
        this.work = parent.getWork();
        this.parent = parent;
        this.depth = parent.getDepth() + 1;
        this.licensee = subApplication.getLicensee();
        this.grantNo = subApplication.getSublicenseNo();
        this.ancestorPath = parent.getAncestorPath() + parent.getId() + "/";
        applyCore(subApplication.getType(), subApplication.getStartDate(), subApplication.getEndDate(),
                subApplication.getTerritories(), subApplication.getMedia());
        applyPolicy(effectivePolicy.sublicensable(), effectivePolicy.maxDepth(),
                effectivePolicy.subTerritories(), effectivePolicy.subMedia(),
                effectivePolicy.subStartDate(), effectivePolicy.subEndDate());
        this.createdAt = Instant.now();
    }

    private void applyCore(LicenseType licenseType, LocalDate start, LocalDate end,
                           List<String> territoryList, List<String> mediumList) {
        this.type = licenseType;
        this.startDate = start;
        this.endDate = end;
        this.territories = new ArrayList<>(territoryList);
        this.media = new ArrayList<>(mediumList);
    }

    private void applyPolicy(boolean canSublicense, Integer maxSublicenseDepth,
                             List<String> policyTerritories, List<String> policyMedia,
                             LocalDate policyStart, LocalDate policyEnd) {
        this.sublicensable = canSublicense;
        this.maxDepth = maxSublicenseDepth;
        this.subTerritories = policyTerritories == null ? new ArrayList<>() : new ArrayList<>(policyTerritories);
        this.subMedia = policyMedia == null ? new ArrayList<>() : new ArrayList<>(policyMedia);
        this.subStartDate = policyStart;
        this.subEndDate = policyEnd;
    }

    /** 根授权保存后补齐含自身的层级链 */
    public void initRootChain() {
        this.chain = new ArrayList<>(List.of(this.id));
    }

    /** 下级授权保存后补齐含自身的层级链（祖先链来自上级；必须走 getter 以兼容 JPA 懒加载代理） */
    public void initChildChain() {
        List<Long> full = new ArrayList<>(this.parent.getChain());
        full.add(this.id);
        this.chain = full;
    }

    /** 严格下级子树查询前缀：根为 "/{id}/%"，非根为 "{ancestorPath}{id}/%"，不含自身 */
    public String descendantPrefix() {
        String base = parent == null ? "/" : this.ancestorPath;
        return base + this.id + "/%";
    }

    /** 用新的有效范围生成新版本的入参快照（版本实体由 service 保存） */
    public void applyNewScope(LicenseType newType, LocalDate newStart, LocalDate newEnd,
                              List<String> newTerritories, List<String> newMedia) {
        applyCore(newType, newStart, newEnd, newTerritories, newMedia);
        this.currentVersion++;
        this.statusChangedAt = Instant.now();
    }

    /** 范围缩减后裁剪"可转授范围"上限（只缩小） */
    public void updatePolicyEnvelope(LocalDate newSubStart, LocalDate newSubEnd,
                                     java.util.Set<String> newSubTerritories,
                                     java.util.Set<String> newSubMedia) {
        this.subStartDate = newSubStart;
        this.subEndDate = newSubEnd;
        this.subTerritories = new ArrayList<>(newSubTerritories);
        this.subMedia = new ArrayList<>(newSubMedia);
        this.statusChangedAt = Instant.now();
    }

    public void markActive() {
        this.status = GrantStatus.ACTIVE;
        this.haltReason = null;
        this.statusChangedAt = Instant.now();
    }

    public void markSuspended(HaltReason reason) {
        this.status = GrantStatus.SUSPENDED;
        this.haltReason = reason;
        this.statusChangedAt = Instant.now();
    }

    public void markTerminated(HaltReason reason) {
        this.status = GrantStatus.TERMINATED;
        this.haltReason = reason;
        this.statusChangedAt = Instant.now();
    }

    public boolean isRoot() {
        return parent == null;
    }

    public boolean isActive() {
        return status == GrantStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getGrantNo() {
        return grantNo;
    }

    public Work getWork() {
        return work;
    }

    public LicenseApplication getApplication() {
        return application;
    }

    public SublicenseApplication getSubApplication() {
        return subApplication;
    }

    public LicenseGrant getParent() {
        return parent;
    }

    public List<Long> getChain() {
        return chain;
    }

    public String getAncestorPath() {
        return ancestorPath;
    }

    public int getDepth() {
        return depth;
    }

    public String getLicensee() {
        return licensee;
    }

    public LicenseType getType() {
        return type;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public List<String> getTerritories() {
        return territories;
    }

    public List<String> getMedia() {
        return media;
    }

    public boolean isSublicensable() {
        return sublicensable;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public List<String> getSubTerritories() {
        return subTerritories;
    }

    public List<String> getSubMedia() {
        return subMedia;
    }

    public LocalDate getSubStartDate() {
        return subStartDate;
    }

    public LocalDate getSubEndDate() {
        return subEndDate;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public GrantStatus getStatus() {
        return status;
    }

    public HaltReason getHaltReason() {
        return haltReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }
}
