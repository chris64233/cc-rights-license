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
 * 转授权审批决定：上级持权方（被授权方）对转授权申请的决定。
 * 决定事件不可修改；同一事件号重复提交时幂等返回首次决定。
 */
@Entity
@Table(name = "sublicense_decisions",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"sublicense_application_id", "applicant"}),
                @UniqueConstraint(columnNames = {"sublicense_application_id", "event_number"})
        })
public class SublicenseDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sublicense_application_id", nullable = false)
    private SublicenseApplication application;

    /** 作出决定时上级授权的版本，用于审计"采用的上级授权版本" */
    @Column(nullable = false)
    private int parentVersion;

    /** 决定人（必须为上级授权当前被授权方/持权方） */
    @Column(nullable = false, length = 200)
    private String applicant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DecisionValue decision;

    @Column(name = "event_number", nullable = false, length = 100)
    private String eventNumber;

    @Column(nullable = false, updatable = false)
    private Instant decidedAt;

    protected SublicenseDecision() {
    }

    public SublicenseDecision(SublicenseApplication application, int parentVersion,
                              String applicant, DecisionValue decision, String eventNumber) {
        this.application = application;
        this.parentVersion = parentVersion;
        this.applicant = applicant;
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

    public int getParentVersion() {
        return parentVersion;
    }

    public String getApplicant() {
        return applicant;
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
