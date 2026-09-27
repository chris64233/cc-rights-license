package com.chris64233.cc.rightslicense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 状态级联传播任务。上级授权发生 暂停/恢复/终止/范围缩减 时，
 * 对每一个受影响的下级授权写入一条任务（与触发事件同事务落库，保证不遗漏），
 * 随后逐条应用；应用失败保留为 PENDING 并可重试（{@code attempts} 记录重试次数）。
 *
 * <p>状态机守卫保证重复应用是幂等的：例如目标已 TERMINATED 则不再变化。
 */
@Entity
@Table(name = "grant_propagations",
        indexes = {
                @Index(name = "idx_propagation_status", columnList = "status"),
                @Index(name = "idx_propagation_target", columnList = "target_grant_id")
        })
public class GrantPropagation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 触发传播的上级授权 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "source_grant_id", nullable = false)
    private LicenseGrant source;

    /** 受影响的下级授权 */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "target_grant_id", nullable = false)
    private LicenseGrant target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropagationOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HaltReason reason;

    /** 触发传播时上级授权的版本，便于审计 */
    @Column(nullable = false)
    private int sourceVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropagationStatus status = PropagationStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 2000)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant appliedAt;

    protected GrantPropagation() {
    }

    public GrantPropagation(LicenseGrant source, LicenseGrant target,
                            PropagationOperation operation, HaltReason reason) {
        this.source = source;
        this.target = target;
        this.operation = operation;
        this.reason = reason;
        this.sourceVersion = source.getCurrentVersion();
        this.createdAt = Instant.now();
    }

    public void recordAttempt(String error) {
        this.attempts++;
        this.lastError = error;
    }

    public void markApplied() {
        this.status = PropagationStatus.APPLIED;
        this.appliedAt = Instant.now();
        this.lastError = null;
    }

    public Long getId() {
        return id;
    }

    public LicenseGrant getSource() {
        return source;
    }

    public LicenseGrant getTarget() {
        return target;
    }

    public PropagationOperation getOperation() {
        return operation;
    }

    public HaltReason getReason() {
        return reason;
    }

    public int getSourceVersion() {
        return sourceVersion;
    }

    public PropagationStatus getStatus() {
        return status;
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

    public Instant getAppliedAt() {
        return appliedAt;
    }
}
