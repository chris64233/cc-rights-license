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
@Table(name = "rights_holders",
        uniqueConstraints = @UniqueConstraint(columnNames = {"work_id", "name"}))
public class RightsHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "work_id", nullable = false)
    private Work work;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal sharePercent;

    protected RightsHolder() {
    }

    public RightsHolder(Work work, String name, BigDecimal sharePercent) {
        this.work = work;
        this.name = name;
        this.sharePercent = sharePercent;
    }

    public Long getId() {
        return id;
    }

    public Work getWork() {
        return work;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getSharePercent() {
        return sharePercent;
    }
}
