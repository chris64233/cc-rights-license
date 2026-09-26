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
@Table(name = "right_holder_decisions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"application_id", "holder_name"}),
                @UniqueConstraint(columnNames = {"event_id"})
        })
public class RightHolderDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LicenseApplication application;

    @Column(name = "holder_name", nullable = false, length = 128)
    private String holderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DecisionType decision;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    protected RightHolderDecision() {
    }

    public RightHolderDecision(LicenseApplication application, String holderName,
                               DecisionType decision, String eventId) {
        this.application = application;
        this.holderName = holderName;
        this.decision = decision;
        this.eventId = eventId;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LicenseApplication getApplication() {
        return application;
    }

    public String getHolderName() {
        return holderName;
    }

    public DecisionType getDecision() {
        return decision;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
