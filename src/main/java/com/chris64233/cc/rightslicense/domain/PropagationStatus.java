package com.chris64233.cc.rightslicense.domain;

/**
 * 级联传播记录的处理状态。PENDING 表示待重试，APPLIED 表示已生效。
 */
public enum PropagationStatus {
    PENDING,
    APPLIED
}
