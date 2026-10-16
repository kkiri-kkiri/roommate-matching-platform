package com.kkirikkiri;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;
import com.kkirikkiri.global.init.*;

@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {
    @Autowired DataSource datasource;
    @Autowired ApplicationContext context;
    @Autowired Environment environment;

	@Test
	void contextLoads() throws Exception {
        try (var connection=datasource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
        }
        assertEquals("never",environment.getProperty("spring.sql.init.mode"));
        assertEquals("NONE",environment.getProperty("app.seed.mode"));
        assertTrue(context.getBeansOfType(SeedDataInitializer.class).isEmpty());
        assertTrue(context.getBeansOfType(SeedDataService.class).isEmpty());
        assertTrue(context.getBeansOfType(DevDataInitializer.class).isEmpty());
	}

}
