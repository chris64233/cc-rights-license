package com.chris64233.cc.rightslicense;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 集成测试基类：每个用例前清空全部业务表并重置自增 ID。
 * 测试使用共享的内存 H2（DB_CLOSE_DELAY=-1），不清理会让跨测试类的授权数据污染全局计数断言。
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final String[] TABLES = {
            "sublicense_decisions",
            "rights_decisions",
            "grant_propagations",
            "grant_lifecycle_events",
            "grant_chain",
            "grant_territories",
            "grant_media",
            "grant_sub_territories",
            "grant_sub_media",
            "grant_version_territories",
            "grant_version_media",
            "grant_version_sub_territories",
            "grant_version_sub_media",
            "grant_versions",
            "sub_application_territories",
            "sub_application_media",
            "sub_application_sub_territories",
            "sub_application_sub_media",
            "sublicense_applications",
            "license_grants",
            "application_territories",
            "application_media",
            "application_sub_territories",
            "application_sub_media",
            "license_applications",
            "rights_holders",
            "works"
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table : TABLES) {
                jdbcTemplate.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            }
        } finally {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }
}
