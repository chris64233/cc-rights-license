package com.chris64233.cc.rightslicense.domain;

/**
 * 授权被暂停或终止的原因。
 */
public enum HaltReason {
    /** 上级撤销，整棵子树终止 */
    PARENT_REVOKED,
    /** 上级授权到期，整棵子树终止 */
    PARENT_EXPIRED,
    /** 上级主动暂停，整棵子树暂停 */
    PARENT_SUSPENDED,
    /** 恢复时与暂停期间新授予的有效兄弟授权冲突，保持暂停，需人工处理 */
    RESUME_CONFLICT,
    /** 上级范围缩减后本级不再完全落在上级有效范围内 */
    PARENT_SCOPE_REDUCED,
    /** 本级被持权方主动暂停 */
    SELF_SUSPENDED,
    /** 本级撤销 */
    REVOKED,
    /** 本级到期 */
    EXPIRED
}
