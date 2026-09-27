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
 * 授权生命周期事件：撤销、暂停、恢复、到期、范围缩减等，作为级联传播的触发凭据。
 */
@Entity
@Table(name = "grant_lifecycle_events",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_no"))
public class GrantLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_no", nullable = false, length = 100)
    private String eventNo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grant_id", nullable = false)
    private LicenseGrant grant;

    @Column(nullable = false)
    private int grantVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropagationOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HaltReason reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected GrantLifecycleEvent() {
    }

    public GrantLifecycleEvent(String eventNo, LicenseGrant grant,
                               PropagationOperation operation, HaltReason reason) {
        this.eventNo = eventNo;
        this.grant = grant;
        this.grantVersion = grant.getCurrentVersion();
        this.operation = operation;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventNo() {
        return eventNo;
    }

    public LicenseGrant getGrant() {
        return grant;
    }

    public int getGrantVersion() {
        return grantVersion;
    }

    public PropagationOperation getOperation() {
        return operation;
    }

    public HaltReason getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
