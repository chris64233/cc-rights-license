package com.chris64233.cc.rightslicense.domain;

/**
 * 授权（含转授权）当前生命周期状态。
 * ACTIVE 有效；SUSPENDED 暂停（可恢复）；TERMINATED 终止（不可恢复）。
 */
public enum GrantStatus {
    ACTIVE,
    SUSPENDED,
    TERMINATED
}
