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

/** V10 must make the first member of every existing group its admin and creator, and keep old messages valid (T12). */
class GroupManagementMigrationIT extends AbstractIntegrationTest {

    @Test
    void v10_backfillsAdminsAndCreators_andAllowsSystemMessagesWithoutSender() throws Exception {
        String name = "migration_group_management";
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + name);
            statement.executeUpdate("CREATE DATABASE " + name);
        }
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("9").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, role, is_verified, created_at) VALUES
                        (1, 'ana', 'ana@test.local', 'USER', false, now()),
                        (2, 'bea', 'bea@test.local', 'USER', false, now()),
                        (3, 'cris', 'cris@test.local', 'USER', false, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO conversations (id, public_id, chat_type, group_name, created_at) VALUES
                        (10, '10000000-0000-7000-8000-000000000000', 'GROUP', 'Trail', now()),
                        (11, '11000000-0000-7000-8000-000000000000', 'INDIVIDUAL', NULL, now()),
                        (12, '12000000-0000-7000-8000-000000000000', 'GROUP', 'Sola', now())
                    """);
            // Bea joined the group first, although Ana's participation row has the lower id
            statement.executeUpdate("""
                    INSERT INTO conversation_participants (id, conversation_id, user_id, unread_count, joined_at) VALUES
                        (1, 10, 1, 0, now()),
                        (2, 10, 2, 0, now() - interval '1 day'),
                        (3, 10, 3, 0, now()),
                        (4, 11, 1, 0, now()),
                        (5, 11, 2, 0, now()),
                        (6, 12, 3, 0, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO messages (id, public_id, conversation_id, sender_id, content, sent_at) VALUES
                        (1, '20000000-0000-7000-8000-000000000000', 10, 1, 'hola', now())
                    """);

            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

            assertThat(single(statement, "SELECT role FROM conversation_participants WHERE id = 2")).isEqualTo("ADMIN");
            assertThat(single(statement, "SELECT role FROM conversation_participants WHERE id = 1")).isEqualTo("MEMBER");
            assertThat(single(statement, "SELECT role FROM conversation_participants WHERE id = 3")).isEqualTo("MEMBER");
            assertThat(single(statement, "SELECT created_by_id FROM conversations WHERE id = 10")).isEqualTo("2");
            assertThat(single(statement, "SELECT role FROM conversation_participants WHERE id = 6")).isEqualTo("ADMIN");
            // Private chats have neither admins nor creator
            assertThat(single(statement, "SELECT count(*) FROM conversation_participants WHERE conversation_id = 11 AND role = 'ADMIN'")).isEqualTo("0");
            assertThat(single(statement, "SELECT created_by_id FROM conversations WHERE id = 11")).isNull();
            assertThat(single(statement, "SELECT managed_by || '/' || history_visible_to_new FROM conversations WHERE id = 10")).isEqualTo("NONE/false");
            assertThat(single(statement, "SELECT type FROM messages WHERE id = 1")).isEqualTo("USER");

            statement.executeUpdate("""
                    INSERT INTO messages (id, public_id, conversation_id, sender_id, content, sent_at, type, system_payload)
                    VALUES (2, '20000000-0000-7000-8000-000000000001', 10, NULL, 'Ana añadió a Cris', now(), 'SYSTEM', '{"action":"MEMBER_ADDED"}')
                    """);
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO messages (id, public_id, conversation_id, sender_id, content, sent_at)
                    VALUES (3, '20000000-0000-7000-8000-000000000002', 10, NULL, 'sin remitente', now())
                    """)).isInstanceOf(SQLException.class);

            // ON DELETE SET NULL
            statement.executeUpdate("DELETE FROM conversation_participants WHERE user_id = 2");
            statement.executeUpdate("UPDATE conversations SET last_sender_id = NULL");
            statement.executeUpdate("DELETE FROM messages WHERE sender_id = 2");
            statement.executeUpdate("DELETE FROM users WHERE id = 2");
            assertThat(single(statement, "SELECT created_by_id FROM conversations WHERE id = 10")).isNull();
        }
    }

    private static String single(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
