package com.chris64233.cc.rightslicense.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(name = "work_right_holders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"work_id", "holder_name"}))
public class WorkRightHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    @Column(name = "holder_name", nullable = false, length = 128)
    private String holderName;

    @Column(name = "share_percent", nullable = false, precision = 9, scale = 4)
    private BigDecimal sharePercent;

    protected WorkRightHolder() {
    }

    public WorkRightHolder(String holderName, BigDecimal sharePercent) {
        this.holderName = holderName;
        this.sharePercent = sharePercent;
    }

    public Long getId() {
        return id;
    }

    public Work getWork() {
        return work;
    }

    void setWork(Work work) {
        this.work = work;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getSharePercent() {
        return sharePercent;
    }
}
