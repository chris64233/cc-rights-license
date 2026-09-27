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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 转授权申请：当前有效被授权方（持权方）向其直接下级转授权利。
 *
 * <p>创建时固化上级授权当时的版本号 {@code parentVersion} 及上级范围快照；
 * 批准时必须重新校验：上级仍有效、版本一致或范围仍完全覆盖申请、申请方仍是被授权方、
 * 层级深度未超上限，以及独占兄弟冲突。整份申请的所有 地域 × 媒介 × 日期 组合原子校验。
 */
@Entity
@Table(name = "sublicense_applications",
        uniqueConstraints = @UniqueConstraint(columnNames = "sublicense_no"))
public class SublicenseApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 转授权号：申请方提供，全局唯一；重复提交时幂等返回已存在申请 */
    @Column(name = "sublicense_no", nullable = false, length = 100)
    private String sublicenseNo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    /** 直接上级授权 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_grant_id", nullable = false)
    private LicenseGrant parentGrant;

    /** 申请采用的上级授权版本 */
    @Column(name = "parent_version", nullable = false)
    private int parentVersion;

    /** 申请方（必须是上级授权的当前有效被授权方） */
    @Column(nullable = false, length = 200)
    private String applicant;

    /** 下级被授权方 */
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
    @CollectionTable(name = "sub_application_territories",
            joinColumns = @JoinColumn(name = "sublicense_application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", nullable = false, length = 100)
    private List<String> territories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sub_application_media",
            joinColumns = @JoinColumn(name = "sublicense_application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", nullable = false, length = 100)
    private List<String> media = new ArrayList<>();

    /** 下级持权方是否被允许继续转授权（不得超过上级声明） */
    @Column(nullable = false)
    private boolean sublicensable;

    private Integer maxDepth;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sub_application_sub_territories",
            joinColumns = @JoinColumn(name = "sublicense_application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", length = 100)
    private List<String> subTerritories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sub_application_sub_media",
            joinColumns = @JoinColumn(name = "sublicense_application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", length = 100)
    private List<String> subMedia = new ArrayList<>();

    private LocalDate subStartDate;

    private LocalDate subEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(length = 2000)
    private String conflictReason;

    /** 批准后生成的下级授权（保存审批决定与层级链的落点） */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_grant_id", unique = true)
    private LicenseGrant childGrant;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant decidedAt;

    protected SublicenseApplication() {
    }

    public SublicenseApplication(String sublicenseNo, Work work, LicenseGrant parentGrant,
                                 String applicant, String licensee, LicenseType type,
                                 LocalDate startDate, LocalDate endDate,
                                 List<String> territories, List<String> media,
                                 SublicensePolicy requestedPolicy) {
        this.sublicenseNo = sublicenseNo;
        this.work = work;
        this.parentGrant = parentGrant;
        this.parentVersion = parentGrant.getCurrentVersion();
        this.applicant = applicant;
        this.licensee = licensee;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.territories = new ArrayList<>(territories);
        this.media = new ArrayList<>(media);
        this.sublicensable = requestedPolicy.sublicensable();
        this.maxDepth = requestedPolicy.maxDepth();
        this.subTerritories = requestedPolicy.subTerritories() == null
                ? new ArrayList<>() : new ArrayList<>(requestedPolicy.subTerritories());
        this.subMedia = requestedPolicy.subMedia() == null
                ? new ArrayList<>() : new ArrayList<>(requestedPolicy.subMedia());
        this.subStartDate = requestedPolicy.subStartDate();
        this.subEndDate = requestedPolicy.subEndDate();
        this.createdAt = Instant.now();
    }

    public void markApproved(LicenseGrant child) {
        this.status = ApplicationStatus.APPROVED;
        this.childGrant = child;
        this.decidedAt = Instant.now();
        this.conflictReason = null;
    }

    public void markConflict(String reason) {
        this.status = ApplicationStatus.CONFLICT;
        this.conflictReason = reason;
        this.decidedAt = Instant.now();
    }

    public void markRejected(String reason) {
        this.status = ApplicationStatus.REJECTED;
        this.conflictReason = reason;
        this.decidedAt = Instant.now();
    }

    public SublicensePolicy getRequestedPolicy() {
        return new SublicensePolicy(sublicensable, maxDepth,
                List.copyOf(subTerritories), List.copyOf(subMedia), subStartDate, subEndDate);
    }

    public Long getId() {
        return id;
    }

    public String getSublicenseNo() {
        return sublicenseNo;
    }

    public Work getWork() {
        return work;
    }

    public LicenseGrant getParentGrant() {
        return parentGrant;
    }

    public int getParentVersion() {
        return parentVersion;
    }

    public String getApplicant() {
        return applicant;
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

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getConflictReason() {
        return conflictReason;
    }

    public LicenseGrant getChildGrant() {
        return childGrant;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
