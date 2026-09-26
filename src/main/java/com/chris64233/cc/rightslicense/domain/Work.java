package com.chris64233.cc.rightslicense.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "works")
public class Work {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_code", nullable = false, unique = true, length = 64)
    private String workCode;

    @Column(nullable = false)
    private String title;

    @OneToMany(mappedBy = "work", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<WorkRightHolder> rightHolders = new ArrayList<>();

    protected Work() {
    }

    public Work(String workCode, String title) {
        this.workCode = workCode;
        this.title = title;
    }

    public void addRightHolder(WorkRightHolder holder) {
        rightHolders.add(holder);
        holder.setWork(this);
    }

    public Long getId() {
        return id;
    }

    public String getWorkCode() {
        return workCode;
    }

    public String getTitle() {
        return title;
    }

    public List<WorkRightHolder> getRightHolders() {
        return rightHolders;
    }
}
