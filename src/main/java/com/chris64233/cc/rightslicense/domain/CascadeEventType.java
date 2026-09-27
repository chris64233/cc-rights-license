package com.chris64233.cc.rightslicense.domain;

public enum CascadeEventType {
    /** 上级授权被撤销：下级终止 */
    REVOKED,
    /** 上级授权范围缩减：越界下级暂停 */
    SCOPE_REDUCED,
    /** 上级授权到期：下级暂停（自身也已到期则终止到期） */
    EXPIRED,
    /** 上级授权被暂停：下级暂停 */
    PARENT_SUSPENDED
}
