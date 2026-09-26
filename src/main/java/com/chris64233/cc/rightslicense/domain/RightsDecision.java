package com.chris64233.cc.rightslicense.domain;

import jakarta.persistence.Column;
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

import java.time.Instant;

@Entity
@Table(name = "rights_decisions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"application_id", "holder_id"}),
                @UniqueConstraint(columnNames = {"application_id", "event_number"})
        })
public class RightsDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private LicenseApplication application;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "holder_id", nullable = false)
    private RightsHolder holder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionValue decision;

    @Column(name = "event_number", nullable = false, length = 100)
    private String eventNumber;

    @Column(nullable = false, updatable = false)
    private Instant decidedAt;

    protected RightsDecision() {
    }

    public RightsDecision(LicenseApplication application, RightsHolder holder,
                          DecisionValue decision, String eventNumber) {
        this.application = application;
        this.holder = holder;
        this.decision = decision;
        this.eventNumber = eventNumber;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LicenseApplication getApplication() {
        return application;
    }

    public RightsHolder getHolder() {
        return holder;
    }

    public DecisionValue getDecision() {
        return decision;
    }

    public String getEventNumber() {
        return eventNumber;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
