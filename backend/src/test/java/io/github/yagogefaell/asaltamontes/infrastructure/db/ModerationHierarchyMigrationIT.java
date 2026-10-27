package io.github.yagogefaell.asaltamontes.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;

/** V14 backfills who expelled each already banned member and with which role (T19), over a database with data. */
class ModerationHierarchyMigrationIT extends AbstractIntegrationTest {

    @Test
    void v14_backfillsTheAuthorOfTheLatestBan_andTreatsAnythingNotOwnerAsModerator() throws Exception {
        String name = "migration_moderation_hierarchy";
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + name);
            statement.executeUpdate("CREATE DATABASE " + name);
        }
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("13").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, role, is_verified, created_at) VALUES
                        (1, 'owner', 'o@test.local', 'USER', false, now()),
                        (2, 'mod', 'm@test.local', 'USER', false, now()),
                        (3, 'byowner', 'a@test.local', 'USER', false, now()),
                        (4, 'bymod', 'b@test.local', 'USER', false, now()),
                        (5, 'nolog', 'c@test.local', 'USER', false, now()),
                        (6, 'active', 'd@test.local', 'USER', false, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO communities (id, public_id, slug, name, member_count, created_by_id, created_at) VALUES
                        (1, '10000000-0000-7000-8000-000000000001', 'c1', 'C1', 2, 1, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO community_members (id, community_id, user_account_id, role, status, joined_at) VALUES
                        (1, 1, 1, 'OWNER', 'ACTIVE', now()),
                        (2, 1, 2, 'MODERATOR', 'ACTIVE', now()),
                        (3, 1, 3, 'MEMBER', 'BANNED', now()),
                        (4, 1, 4, 'MEMBER', 'BANNED', now()),
                        (5, 1, 5, 'MEMBER', 'BANNED', now()),
                        (6, 1, 6, 'MEMBER', 'ACTIVE', now())
                    """);
            // byowner was banned first by the moderator and later by the owner: the latest BAN counts
            statement.executeUpdate("""
                    INSERT INTO community_moderation_log (public_id, community_id, actor_id, target_id, action, created_at) VALUES
                        ('30000000-0000-7000-8000-000000000001', 1, 2, 3, 'BAN', now() - interval '3 days'),
                        ('30000000-0000-7000-8000-000000000002', 1, 2, 3, 'UNBAN', now() - interval '2 days'),
                        ('30000000-0000-7000-8000-000000000003', 1, 1, 3, 'BAN', now() - interval '1 day'),
                        ('30000000-0000-7000-8000-000000000004', 1, 2, 4, 'BAN', now())
                    """);

            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

            assertThat(single(statement, "SELECT banned_by || '/' || banned_by_role FROM community_members WHERE id = 3")).isEqualTo("1/OWNER");
            assertThat(single(statement, "SELECT banned_by || '/' || banned_by_role FROM community_members WHERE id = 4")).isEqualTo("2/MODERATOR");
            // No log entry: nobody known, and the permissive role
            assertThat(single(statement, "SELECT banned_by FROM community_members WHERE id = 5")).isNull();
            assertThat(single(statement, "SELECT banned_by_role FROM community_members WHERE id = 5")).isEqualTo("MODERATOR");
            // Not banned: untouched
            assertThat(single(statement, "SELECT banned_by FROM community_members WHERE id = 6")).isNull();
            assertThat(single(statement, "SELECT banned_by_role FROM community_members WHERE id = 6")).isNull();

            assertThatThrownBy(() -> statement.executeUpdate("UPDATE community_members SET banned_by_role = 'MEMBER' WHERE id = 3"))
                    .isInstanceOf(SQLException.class);
            // ON DELETE SET NULL keeps the role: the hierarchy survives the deletion of the account
            statement.executeUpdate("UPDATE community_moderation_log SET actor_id = NULL WHERE actor_id = 1");
            statement.executeUpdate("DELETE FROM community_members WHERE user_account_id = 1");
            statement.executeUpdate("UPDATE communities SET created_by_id = 2");
            statement.executeUpdate("DELETE FROM users WHERE id = 1");
            assertThat(single(statement, "SELECT banned_by FROM community_members WHERE id = 3")).isNull();
            assertThat(single(statement, "SELECT banned_by_role FROM community_members WHERE id = 3")).isEqualTo("OWNER");
        }
    }

    private static String single(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
