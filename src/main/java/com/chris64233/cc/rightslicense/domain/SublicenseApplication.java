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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 转授权申请。
 * 申请范围必须完全落在上级授权（及其转授权策略）范围内；
 * 审批时记录实际采用的上级授权版本；授权生效后通过 LicenseGrant 的层级链体现层级。
 */
@Entity
@Table(name = "sublicense_applications")
public class SublicenseApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务转授权号，全局唯一，保证重复提交幂等。 */
    @Column(name = "sublicense_number", nullable = false, unique = true, length = 100)
    private String sublicenseNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_grant_id", nullable = false)
    private LicenseGrant parentGrant;

    /** 申请方，必须是上级授权当前有效的被授权方。 */
    @Column(nullable = false, length = 200)
    private String applicant;

    @Column(nullable = false, length = 200)
    private String sublicensee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LicenseType type;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sublicense_app_territories",
            joinColumns = @JoinColumn(name = "sublicense_application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", nullable = false, length = 100)
    private List<String> territories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sublicense_app_media",
            joinColumns = @JoinColumn(name = "sublicense_application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", nullable = false, length = 100)
    private List<String> media = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SublicenseStatus status = SublicenseStatus.PENDING;

    @Column(name = "conflict_reason", length = 2000)
    private String conflictReason;

    /** 审批采用的上级授权版本（创建时快照，批准时更新为最新版本）。 */
    @Column(name = "parent_version", nullable = false)
    private long parentVersion;

    // ---- 生效后下级授权的转授权策略 ----

    @Column(nullable = false)
    private boolean sublicensable;

    @Column(name = "max_sublicense_levels")
    private Integer maxSublicenseLevels;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sublicense_app_policy_territories",
            joinColumns = @JoinColumn(name = "sublicense_application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", length = 100)
    private List<String> policyTerritories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "sublicense_app_policy_media",
            joinColumns = @JoinColumn(name = "sublicense_application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", length = 100)
    private List<String> policyMedia = new ArrayList<>();

    @Column(name = "policy_start_bound")
    private LocalDate policyStartBound;

    @Column(name = "policy_end_bound")
    private LocalDate policyEndBound;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SublicenseApplication() {
    }

    public SublicenseApplication(String sublicenseNumber, LicenseGrant parentGrant,
                                 String applicant, String sublicensee, LicenseType type,
                                 LocalDate startDate, LocalDate endDate,
                                 List<String> territories, List<String> media,
                                 SublicensePolicy policy) {
        this.sublicenseNumber = sublicenseNumber;
        this.parentGrant = parentGrant;
        this.applicant = applicant;
        this.sublicensee = sublicensee;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.territories = new ArrayList<>(territories);
        this.media = new ArrayList<>(media);
        this.parentVersion = parentGrant.getVersion();
        applyPolicy(policy);
        this.createdAt = Instant.now();
    }

    public void applyPolicy(SublicensePolicy policy) {
        SublicensePolicy effective = policy != null ? policy : SublicensePolicy.disabled();
        this.sublicensable = effective.sublicensable();
        this.maxSublicenseLevels = effective.maxLevels();
        this.policyTerritories = new ArrayList<>(effective.territories());
        this.policyMedia = new ArrayList<>(effective.media());
        this.policyStartBound = effective.startBound();
        this.policyEndBound = effective.endBound();
    }

    public SublicensePolicy toSublicensePolicy() {
        return new SublicensePolicy(sublicensable, maxSublicenseLevels,
                policyTerritories, policyMedia, policyStartBound, policyEndBound);
    }

    public void markApproved(long parentVersionAtApproval) {
        this.status = SublicenseStatus.APPROVED;
        this.parentVersion = parentVersionAtApproval;
    }

    public void markRejected() {
        this.status = SublicenseStatus.REJECTED;
    }

    public void markConflict(String reason) {
        this.status = SublicenseStatus.CONFLICT;
        this.conflictReason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getSublicenseNumber() {
        return sublicenseNumber;
    }

    public LicenseGrant getParentGrant() {
        return parentGrant;
    }

    public String getApplicant() {
        return applicant;
    }

    public String getSublicensee() {
        return sublicensee;
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

    public SublicenseStatus getStatus() {
        return status;
    }

    public String getConflictReason() {
        return conflictReason;
    }

    public long getParentVersion() {
        return parentVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
