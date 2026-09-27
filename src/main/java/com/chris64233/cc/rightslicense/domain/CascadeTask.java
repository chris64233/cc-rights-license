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
 * 级联传播任务：上级授权到期、撤销或范围缩减时，为每个受影响下级授权生成一条任务。
 * 任务与状态变化在同一事务落库，处理可重试，不允许遗漏。
 */
@Entity
@Table(name = "cascade_tasks",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"target_grant_id", "event_type", "source_grant_id"}))
public class CascadeTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 需要被暂停或终止的下级授权。 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "target_grant_id", nullable = false)
    private LicenseGrant targetGrant;

    /** 触发本次传播的上级授权。 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "source_grant_id", nullable = false)
    private LicenseGrant sourceGrant;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private CascadeEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CascadeTaskStatus status = CascadeTaskStatus.PENDING;

    @Column(length = 2000)
    private String detail;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected CascadeTask() {
    }

    public CascadeTask(LicenseGrant targetGrant, LicenseGrant sourceGrant,
                       CascadeEventType eventType, String detail) {
        this.targetGrant = targetGrant;
        this.sourceGrant = sourceGrant;
        this.eventType = eventType;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public void markDone() {
        this.status = CascadeTaskStatus.DONE;
        this.processedAt = Instant.now();
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = CascadeTaskStatus.FAILED;
        this.processedAt = Instant.now();
        this.lastError = error != null && error.length() > 1900 ? error.substring(0, 1900) : error;
    }

    public void resetForRetry() {
        this.status = CascadeTaskStatus.PENDING;
        this.processedAt = null;
        this.lastError = null;
    }

    public Long getId() {
        return id;
    }

    public LicenseGrant getTargetGrant() {
        return targetGrant;
    }

    public LicenseGrant getSourceGrant() {
        return sourceGrant;
    }

    public CascadeEventType getEventType() {
        return eventType;
    }

    public CascadeTaskStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
