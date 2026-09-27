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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "license_applications")
public class LicenseApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

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
    @CollectionTable(name = "application_territories",
            joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", nullable = false, length = 100)
    private List<String> territories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "application_media",
            joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", nullable = false, length = 100)
    private List<String> media = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(length = 2000)
    private String conflictReason;

    // ---- 根授权的转授权声明（申请创建时由权利人一方声明，批准后固化到授权版本 v1） ----

    @Column(nullable = false)
    private boolean sublicensable = false;

    /** 允许的最大绝对层级深度；不允许转授权时为 null */
    private Integer maxDepth;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "application_sub_territories",
            joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", nullable = false, length = 100)
    private List<String> subTerritories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "application_sub_media",
            joinColumns = @JoinColumn(name = "application_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", nullable = false, length = 100)
    private List<String> subMedia = new ArrayList<>();

    private LocalDate subStartDate;

    private LocalDate subEndDate;

    protected LicenseApplication() {
    }

    public LicenseApplication(Work work, String licensee, LicenseType type,
                              LocalDate startDate, LocalDate endDate,
                              List<String> territories, List<String> media) {
        this(work, licensee, type, startDate, endDate, territories, media,
                SublicensePolicy.forbidden());
    }

    public LicenseApplication(Work work, String licensee, LicenseType type,
                              LocalDate startDate, LocalDate endDate,
                              List<String> territories, List<String> media,
                              SublicensePolicy policy) {
        this.work = work;
        this.licensee = licensee;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.territories = new ArrayList<>(territories);
        this.media = new ArrayList<>(media);
        this.sublicensable = policy.sublicensable();
        this.maxDepth = policy.maxDepth();
        if (policy.subTerritories() != null) {
            this.subTerritories = new ArrayList<>(policy.subTerritories());
        }
        if (policy.subMedia() != null) {
            this.subMedia = new ArrayList<>(policy.subMedia());
        }
        this.subStartDate = policy.subStartDate();
        this.subEndDate = policy.subEndDate();
    }

    public Long getId() {
        return id;
    }

    public Work getWork() {
        return work;
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

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getConflictReason() {
        return conflictReason;
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

    public SublicensePolicy getPolicy() {
        return new SublicensePolicy(sublicensable, maxDepth,
                List.copyOf(subTerritories), List.copyOf(subMedia), subStartDate, subEndDate);
    }

    public void markRejected() {
        this.status = ApplicationStatus.REJECTED;
    }

    public void markApproved() {
        this.status = ApplicationStatus.APPROVED;
    }

    public void markConflict(String reason) {
        this.status = ApplicationStatus.CONFLICT;
        this.conflictReason = reason;
    }
}
