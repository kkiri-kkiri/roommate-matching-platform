package com.kkirikkiri.global.init;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
abstract class ReferenceDataInitializationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ApplicationContext context;

    @Test
    void commonDataIsLoadedAndRepeatedInitializationPreservesRows() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM dormitory WHERE dormitory_name = '나래'", Long.class) > 0);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM faculty WHERE faculty_name = '컴퓨터응용수학부'", Long.class) > 0);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM major m JOIN faculty f ON m.faculty_id = f.faculty_id "
                + "WHERE f.faculty_name = '컴퓨터응용수학부' AND m.major_name = '소프트웨어융합전공'", Long.class) > 0);
        assertSystemCategories();

        Map<String, Object> before = snapshot();
        var populator = new ResourceDatabasePopulator(new ClassPathResource("data-common.sql"));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            populator.populate(connection);
            populator.populate(connection);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
        assertEquals(before, snapshot());
        assertSystemCategories();
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> rows = new LinkedHashMap<>();
        for (String table : new String[] {"dormitory", "faculty", "major", "calendar_category"}) {
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return rows;
    }

    private void assertSystemCategories() {
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM calendar_category "
                + "WHERE category_code IN ('SCHOOL', 'DORMITORY') AND owner_user_id IS NULL "
                + "AND is_system = TRUE AND name_locked = TRUE", Long.class));
    }
}

@ActiveProfiles("local")
class LocalReferenceDataInitializationTest extends ReferenceDataInitializationTest {

    @Test
    void localAccountsExistAndAreNotDuplicated() {
        var initializer = context.getBean(DevDataInitializer.class);
        Long before = jdbc.queryForObject("SELECT COUNT(*) FROM user", Long.class);
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM user", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE login_id IN ('user01', 'admin01')", Long.class));
    }
}

@ActiveProfiles("reference-data-test")
class NonLocalReferenceDataInitializationTest extends ReferenceDataInitializationTest {

    @Test
    void testAccountInitializerIsNotRegistered() {
        assertTrue(context.getBeansOfType(DevDataInitializer.class).isEmpty());
    }
}
