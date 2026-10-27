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

/** V15 gives every existing community a chat with its non-banned members (T20), over a database with data. */
class CommunityChatMigrationIT extends AbstractIntegrationTest {

    @Test
    void v15_createsTheChatOfEachCommunity_withItsActiveAndMutedMembers_butNotTheExpelled() throws Exception {
        String name = "migration_community_chat";
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + name);
            statement.executeUpdate("CREATE DATABASE " + name);
        }
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("14").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, role, is_verified, created_at) VALUES
                        (1, 'owner', 'o@test.local', 'USER', false, now()),
                        (2, 'muted', 'm@test.local', 'USER', false, now()),
                        (3, 'banned', 'b@test.local', 'USER', false, now()),
                        (4, 'other', 'x@test.local', 'USER', false, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO communities (id, public_id, slug, name, image_url, member_count, created_by_id, created_at) VALUES
                        (1, '10000000-0000-7000-8000-000000000001', 'c1', 'C1', 'https://img/c1.png', 2, 1, now()),
                        (2, '10000000-0000-7000-8000-000000000002', 'c2', 'C2', NULL, 1, 4, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO community_members (id, community_id, user_account_id, role, status, joined_at) VALUES
                        (1, 1, 1, 'OWNER', 'ACTIVE', now()),
                        (2, 1, 2, 'MEMBER', 'MUTED', now()),
                        (3, 1, 3, 'MEMBER', 'BANNED', now()),
                        (4, 2, 4, 'OWNER', 'ACTIVE', now())
                    """);

            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

            assertThat(single(statement, "SELECT count(*) FROM conversations WHERE managed_by = 'COMMUNITY'")).isEqualTo("2");
            assertThat(single(statement, "SELECT group_name || '|' || group_image || '|' || chat_type FROM conversations WHERE managed_ref_id = 1"))
                    .isEqualTo("C1|https://img/c1.png|GROUP");
            assertThat(single(statement, "SELECT count(*) FROM conversation_participants p JOIN conversations c ON c.id = p.conversation_id "
                    + "WHERE c.managed_ref_id = 1 AND p.user_id IN (1, 2)")).isEqualTo("2");
            assertThat(single(statement, "SELECT count(*) FROM conversation_participants p JOIN conversations c ON c.id = p.conversation_id "
                    + "WHERE c.managed_ref_id = 1 AND p.user_id = 3")).isEqualTo("0");
            assertThat(single(statement, "SELECT count(*) FROM conversation_participants p JOIN conversations c ON c.id = p.conversation_id "
                    + "WHERE c.managed_ref_id = 2")).isEqualTo("1");
            assertThat(single(statement, "SELECT chat_enabled FROM communities WHERE id = 1")).isEqualTo("t");
            // One chat per community
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO conversations (public_id, chat_type, managed_by, managed_ref_id, created_at)
                    VALUES ('20000000-0000-7000-8000-000000000001', 'GROUP', 'COMMUNITY', 1, now())
                    """)).isInstanceOf(SQLException.class);
        }
    }

    private static String single(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
