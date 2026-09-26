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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "license_applications")
public class LicenseApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    @Column(nullable = false)
    private String licensee;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false, length = 20)
    private LicenseType licenseType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "conflict_reason", length = 2000)
    private String conflictReason;

    @ElementCollection
    @CollectionTable(name = "application_territories",
            joinColumns = @JoinColumn(name = "application_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "territory"}))
    @Column(name = "territory", nullable = false, length = 64)
    private Set<String> territories = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "application_media",
            joinColumns = @JoinColumn(name = "application_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "media"}))
    @Column(name = "media", nullable = false, length = 64)
    private Set<String> media = new LinkedHashSet<>();

    protected LicenseApplication() {
    }

    public LicenseApplication(Work work, String licensee, LicenseType licenseType,
                              LocalDate startDate, LocalDate endDate,
                              Set<String> territories, Set<String> media) {
        this.work = work;
        this.licensee = licensee;
        this.licenseType = licenseType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.territories = new LinkedHashSet<>(territories);
        this.media = new LinkedHashSet<>(media);
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

    public LicenseType getLicenseType() {
        return licenseType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getConflictReason() {
        return conflictReason;
    }

    public void setConflictReason(String conflictReason) {
        this.conflictReason = conflictReason;
    }

    public Set<String> getTerritories() {
        return territories;
    }

    public Set<String> getMedia() {
        return media;
    }
}
