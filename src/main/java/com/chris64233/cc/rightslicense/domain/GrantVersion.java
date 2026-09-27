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
 * 授权版本快照：每次范围缩减/扩大生成新版本，转授权申请记录其采用的上级授权版本。
 */
@Entity
@Table(name = "grant_versions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"grant_id", "version"}))
public class GrantVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id", nullable = false)
    private LicenseGrant grant;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LicenseType type;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_version_territories",
            joinColumns = @JoinColumn(name = "grant_version_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", nullable = false, length = 100)
    private List<String> territories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_version_media",
            joinColumns = @JoinColumn(name = "grant_version_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", nullable = false, length = 100)
    private List<String> media = new ArrayList<>();

    private boolean sublicensable;

    private Integer maxDepth;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_version_sub_territories",
            joinColumns = @JoinColumn(name = "grant_version_id"))
    @OrderColumn(name = "idx")
    @Column(name = "territory", length = 100)
    private List<String> subTerritories = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "grant_version_sub_media",
            joinColumns = @JoinColumn(name = "grant_version_id"))
    @OrderColumn(name = "idx")
    @Column(name = "medium", length = 100)
    private List<String> subMedia = new ArrayList<>();

    private LocalDate subStartDate;

    private LocalDate subEndDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 500)
    private String changeReason;

    protected GrantVersion() {
    }

    public GrantVersion(LicenseGrant grant, int version, LicenseType type,
                        LocalDate startDate, LocalDate endDate,
                        List<String> territories, List<String> media,
                        boolean sublicensable, Integer maxDepth,
                        List<String> subTerritories, List<String> subMedia,
                        LocalDate subStartDate, LocalDate subEndDate,
                        String changeReason) {
        this.grant = grant;
        this.version = version;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.territories = new ArrayList<>(territories);
        this.media = new ArrayList<>(media);
        this.sublicensable = sublicensable;
        this.maxDepth = maxDepth;
        this.subTerritories = subTerritories == null ? new ArrayList<>() : new ArrayList<>(subTerritories);
        this.subMedia = subMedia == null ? new ArrayList<>() : new ArrayList<>(subMedia);
        this.subStartDate = subStartDate;
        this.subEndDate = subEndDate;
        this.createdAt = Instant.now();
        this.changeReason = changeReason;
    }

    public Long getId() {
        return id;
    }

    public LicenseGrant getGrant() {
        return grant;
    }

    public int getVersion() {
        return version;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getChangeReason() {
        return changeReason;
    }
}
