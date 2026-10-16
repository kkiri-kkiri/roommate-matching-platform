package com.kkirikkiri.auth.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class AuthLimitStoreSchemaTest {
    @Test void sourceAndIdentityCollationsMayDifferBetweenDomainsButMustMatchEachPair() {
        JdbcTemplate jdbc = mysql();
        schema(jdbc, "account_email", "utf8mb4_0900_as_cs");
        schema(jdbc, "verification_email", "utf8mb4_0900_ai_ci");
        assertDoesNotThrow(() -> new AuthLimitStore(jdbc, mock(PlatformTransactionManager.class)));
    }

    @Test void mismatchingAccountCollationFailsClosed() {
        JdbcTemplate jdbc = mysql();
        schema(jdbc, "account_email", "utf8mb4_0900_as_cs");
        when(jdbc.queryForList(contains("'auth_email_identity'"), eq("account_email")))
            .thenReturn(List.of(column("utf8mb4_0900_ai_ci")));
        assertThrows(IllegalStateException.class, () -> new AuthLimitStore(jdbc, mock(PlatformTransactionManager.class)));
    }

    @Test void missingVerificationIdentityFailsClosed() {
        JdbcTemplate jdbc = mysql();
        schema(jdbc, "account_email", "utf8mb4_0900_ai_ci");
        schema(jdbc, "verification_email", "utf8mb4_0900_ai_ci");
        when(jdbc.queryForList(contains("'auth_email_identity'"), eq("verification_email"))).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> new AuthLimitStore(jdbc, mock(PlatformTransactionManager.class)));
    }

    @SuppressWarnings("unchecked")
    private JdbcTemplate mysql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.getDataSource()).thenReturn(mock(javax.sql.DataSource.class));
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn("MySQL");
        return jdbc;
    }

    private void schema(JdbcTemplate jdbc, String identityColumn, String collation) {
        boolean account = identityColumn.equals("account_email");
        when(jdbc.queryForList(contains("table_name = ?"), eq(account ? "user" : "email_verification"),
                eq(account ? "school_email" : "email"))).thenReturn(List.of(column(collation)));
        when(jdbc.queryForList(contains("'auth_email_identity'"), eq(identityColumn)))
            .thenReturn(List.of(column(collation)));
    }

    private Map<String, Object> column(String collation) {
        return Map.of("CHARACTER_SET_NAME", "utf8mb4", "COLLATION_NAME", collation, "COLUMN_TYPE", "varchar(100)");
    }
}
