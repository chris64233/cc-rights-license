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

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private LicenseApplication application;

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

    @Column(nullable = false, updatable = false)
    private Instant grantedAt;

    protected LicenseGrant() {
    }

    public LicenseGrant(LicenseApplication application) {
        this.application = application;
        this.work = application.getWork();
        this.licensee = application.getLicensee();
        this.type = application.getType();
        this.startDate = application.getStartDate();
        this.endDate = application.getEndDate();
        this.territories = new ArrayList<>(application.getTerritories());
        this.media = new ArrayList<>(application.getMedia());
        this.grantedAt = Instant.now();
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

    public Instant getGrantedAt() {
        return grantedAt;
    }
}
