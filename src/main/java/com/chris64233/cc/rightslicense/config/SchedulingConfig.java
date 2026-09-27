package com.chris64233.cc.rightslicense.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 到期巡检与级联重试的定时任务开关。测试环境通过 rights.scheduling.enabled=false 关闭，
 * 避免后台调度与用例断言相互干扰。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rights.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
