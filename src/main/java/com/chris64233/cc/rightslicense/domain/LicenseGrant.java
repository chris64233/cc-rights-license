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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "license_grants")
public class LicenseGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    /** 根授权对应的一级授权申请；转授权时为空。 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", unique = true)
    private LicenseApplication application;

    /** 转授权对应的转授权申请；根授权时为空。 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sublicense_application_id", unique = true)
    private SublicenseApplication sublicenseApplication;

    /** 上级授权；根授权时为空。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_grant_id")
    private LicenseGrant parentGrant;

    @Column(name = "root_grant_id")
    private Long rootGrantId;

    /** 0 表示根授权，每向下转授一层加 1。 */
    @Column(nullable = false)
    private int depth;

    /** 层级链，例如根授权 #3 为 /3/，其下级 #7 为 /3/7/。 */
    @Column(nullable = false, length = 1000)
    private String chainPath = "";

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

    // ---- 转授权策略 ----

    @Column(nullable = false)
    private boolean sublicensable;

    /** 允许的最大绝对层级（根授权为 0）；null 表示不限层级。 */
    @Column(name = "max_sublicense_depth")
    private Integer maxSublicenseDepth;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_sublicense_territories",
            joinColumns = @JoinColumn(name = "grant_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", length = 100)
    private List<String> sublicensableTerritories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_sublicense_media",
            joinColumns = @JoinColumn(name = "grant_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", length = 100)
    private List<String> sublicensableMedia = new ArrayList<>();

    @Column(name = "sublicense_start_bound")
    private LocalDate sublicenseStartBound;

    @Column(name = "sublicense_end_bound")
    private LocalDate sublicenseEndBound;

    // ---- 生命周期 ----

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrantStatus status = GrantStatus.ACTIVE;

    @Column(name = "status_reason", length = 2000)
    private String statusReason;

    /** 授权版本：状态或范围每次变化加 1，转授权审批记录其采用的版本。 */
    @Column(name = "revision", nullable = false)
    private long version = 1L;

    @Column(nullable = false, updatable = false)
    private Instant grantedAt;

    protected LicenseGrant() {
    }

    /** 根授权。 */
    public LicenseGrant(LicenseApplication application, SublicensePolicy policy) {
        this.application = application;
        this.work = application.getWork();
        this.licensee = application.getLicensee();
        this.type = application.getType();
        this.startDate = application.getStartDate();
        this.endDate = application.getEndDate();
        this.territories = new ArrayList<>(application.getTerritories());
        this.media = new ArrayList<>(application.getMedia());
        this.depth = 0;
        applyPolicy(policy, 0);
        this.grantedAt = Instant.now();
    }

    /** 转授权。 */
    public LicenseGrant(LicenseGrant parent, SublicenseApplication application,
                        SublicensePolicy policy) {
        this.parentGrant = parent;
        this.sublicenseApplication = application;
        this.work = parent.getWork();
        this.licensee = application.getSublicensee();
        this.type = application.getType();
        this.startDate = application.getStartDate();
        this.endDate = application.getEndDate();
        this.territories = new ArrayList<>(application.getTerritories());
        this.media = new ArrayList<>(application.getMedia());
        this.depth = parent.getDepth() + 1;
        applyPolicy(policy, this.depth);
        this.grantedAt = Instant.now();
    }

    private void applyPolicy(SublicensePolicy policy, int ownDepth) {
        SublicensePolicy effective = policy != null ? policy : SublicensePolicy.disabled();
        this.sublicensable = effective.sublicensable();
        this.maxSublicenseDepth = effective.maxLevels() != null
                ? ownDepth + effective.maxLevels()
                : null;
        this.sublicensableTerritories = new ArrayList<>(effective.territories());
        this.sublicensableMedia = new ArrayList<>(effective.media());
        this.sublicenseStartBound = effective.startBound();
        this.sublicenseEndBound = effective.endBound();
    }

    /** 生成 id 后调用：确定根授权与层级链。 */
    public void initHierarchy() {
        if (parentGrant == null) {
            this.rootGrantId = this.id;
            this.chainPath = "/" + this.id + "/";
        } else {
            this.rootGrantId = parentGrant.getRootGrantId();
            this.chainPath = parentGrant.getChainPath() + this.id + "/";
        }
    }

    public void reduceScope(LocalDate newStartDate, LocalDate newEndDate,
                            List<String> newTerritories, List<String> newMedia) {
        this.startDate = newStartDate;
        this.endDate = newEndDate;
        this.territories = new ArrayList<>(newTerritories);
        this.media = new ArrayList<>(newMedia);
        this.version++;
    }

    public void markSuspended(String reason) {
        this.status = GrantStatus.SUSPENDED;
        this.statusReason = reason;
        this.version++;
    }

    public void markTerminated(String reason) {
        this.status = GrantStatus.TERMINATED;
        this.statusReason = reason;
        this.version++;
    }

    public void markExpired(String reason) {
        this.status = GrantStatus.EXPIRED;
        this.statusReason = reason;
        this.version++;
    }

    public Long getId() {
        return id;
    }

    public Work getWork() {
        return work;
    }

    public LicenseApplication getApplication() {
        return application;
    }

    public SublicenseApplication getSublicenseApplication() {
        return sublicenseApplication;
    }

    public LicenseGrant getParentGrant() {
        return parentGrant;
    }

    public Long getRootGrantId() {
        return rootGrantId;
    }

    public int getDepth() {
        return depth;
    }

    public String getChainPath() {
        return chainPath;
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

    public Integer getMaxSublicenseDepth() {
        return maxSublicenseDepth;
    }

    public List<String> getSublicensableTerritories() {
        return sublicensableTerritories;
    }

    public List<String> getSublicensableMedia() {
        return sublicensableMedia;
    }

    public LocalDate getSublicenseStartBound() {
        return sublicenseStartBound;
    }

    public LocalDate getSublicenseEndBound() {
        return sublicenseEndBound;
    }

    public GrantStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public long getVersion() {
        return version;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
