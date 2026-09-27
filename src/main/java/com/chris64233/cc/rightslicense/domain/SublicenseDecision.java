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

/**
 * 转授权审批决定。由上级授权的当前被授权方作出，每个申请只能有一条决定，
 * 同一事件号重复提交幂等返回。
 */
@Entity
@Table(name = "sublicense_decisions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "event_number"}))
public class SublicenseDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private SublicenseApplication application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionValue decision;

    @Column(name = "event_number", nullable = false, length = 100)
    private String eventNumber;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    protected SublicenseDecision() {
    }

    public SublicenseDecision(SublicenseApplication application, DecisionValue decision,
                              String eventNumber) {
        this.application = application;
        this.decision = decision;
        this.eventNumber = eventNumber;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public SublicenseApplication getApplication() {
        return application;
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
