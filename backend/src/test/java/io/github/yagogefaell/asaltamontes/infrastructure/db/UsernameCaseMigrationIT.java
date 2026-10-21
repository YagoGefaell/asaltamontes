package io.github.yagogefaell.asaltamontes.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;

/**
 * V2 must apply on a database that already has users whose username/email only differ in case:
 * migrate to V1, seed duplicates, then migrate to the latest version (T05).
 */
class UsernameCaseMigrationIT extends AbstractIntegrationTest {

    @Test
    void v2_renamesCaseInsensitiveDuplicatesAndEnforcesUniqueness() throws Exception {
        String url = createEmptyDatabase("migration_duplicates");
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("1").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (username, email, role, is_verified, created_at) VALUES
                        ('ana', 'ana@test.local', 'USER', false, now()),
                        ('Ana', 'ANA@test.local', 'USER', false, now()),
                        ('ANA', 'other@test.local', 'USER', false, now()),
                        ('bea', 'bea@test.local', 'USER', false, now())
                    """);

            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

            assertThat(query(statement, "SELECT username FROM users ORDER BY id"))
                    .containsExactly("ana", "Ana_2", "ANA_3", "bea");
            assertThat(query(statement, "SELECT email FROM users ORDER BY id"))
                    .containsExactly("ana@test.local", "ANA+dup2@test.local", "other@test.local", "bea@test.local");

            assertThatThrownBy(() -> statement.executeUpdate(
                    "INSERT INTO users (username, email, role, is_verified, created_at) VALUES ('BEA', 'x@test.local', 'USER', false, now())"))
                    .hasMessageContaining("ux_users_username_lower");
        }
    }

    private static String createEmptyDatabase(String name) throws Exception {
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + name);
            statement.executeUpdate("CREATE DATABASE " + name);
        }
        return POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
    }

    private static List<String> query(Statement statement, String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }
}
