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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "license_grants")
public class LicenseGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private LicenseApplication application;

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

    @ElementCollection
    @CollectionTable(name = "grant_territories",
            joinColumns = @JoinColumn(name = "grant_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"grant_id", "territory"}))
    @Column(name = "territory", nullable = false, length = 64)
    private Set<String> territories = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "grant_media",
            joinColumns = @JoinColumn(name = "grant_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"grant_id", "media"}))
    @Column(name = "media", nullable = false, length = 64)
    private Set<String> media = new LinkedHashSet<>();

    protected LicenseGrant() {
    }

    public LicenseGrant(LicenseApplication application) {
        this.application = application;
        this.work = application.getWork();
        this.licensee = application.getLicensee();
        this.licenseType = application.getLicenseType();
        this.startDate = application.getStartDate();
        this.endDate = application.getEndDate();
        this.territories = new LinkedHashSet<>(application.getTerritories());
        this.media = new LinkedHashSet<>(application.getMedia());
    }

    public Long getId() {
        return id;
    }

    public LicenseApplication getApplication() {
        return application;
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

    public Set<String> getTerritories() {
        return territories;
    }

    public Set<String> getMedia() {
        return media;
    }
}
