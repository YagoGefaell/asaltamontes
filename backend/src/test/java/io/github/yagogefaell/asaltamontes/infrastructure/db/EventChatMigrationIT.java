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

/** V16 gives every live event a chat with the people going (T21), over a database with data. */
class EventChatMigrationIT extends AbstractIntegrationTest {

    @Test
    void v16_createsTheChatOfEachLiveEvent_withTheGoingAttendees() throws Exception {
        String name = "migration_event_chat";
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + name);
            statement.executeUpdate("CREATE DATABASE " + name);
        }
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("15").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, role, is_verified, created_at) VALUES
                        (1, 'org', 'o@test.local', 'USER', false, now()),
                        (2, 'going', 'g@test.local', 'USER', false, now()),
                        (3, 'waiting', 'w@test.local', 'USER', false, now())
                    """);
            // 1: upcoming, 2: cancelled, 3: archived for a long time, 4: ended 3 days ago (still open)
            statement.executeUpdate("""
                    INSERT INTO events (id, public_id, title, type, status, starts_at, timezone, image_url, attendee_count, visibility, organizer_id, created_at) VALUES
                        (1, '10000000-0000-7000-8000-000000000001', 'Subida', 'MEETUP', 'SCHEDULED', now() + interval '2 days', 'Europe/Madrid', 'https://img/e1.png', 2, 'PUBLIC', 1, now()),
                        (2, '10000000-0000-7000-8000-000000000002', 'Cancelada', 'MEETUP', 'CANCELLED', now() + interval '2 days', 'Europe/Madrid', NULL, 1, 'PUBLIC', 1, now()),
                        (3, '10000000-0000-7000-8000-000000000003', 'Vieja', 'MEETUP', 'SCHEDULED', now() - interval '30 days', 'Europe/Madrid', NULL, 1, 'PUBLIC', 1, now()),
                        (4, '10000000-0000-7000-8000-000000000004', 'Reciente', 'MEETUP', 'SCHEDULED', now() - interval '3 days', 'Europe/Madrid', NULL, 1, 'PUBLIC', 1, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO event_attendees (event_id, user_id, status, waitlist_position, joined_at) VALUES
                        (1, 1, 'GOING', NULL, now()), (1, 2, 'GOING', NULL, now()), (1, 3, 'WAITLIST', 1, now()),
                        (2, 1, 'GOING', NULL, now()), (3, 1, 'GOING', NULL, now()), (4, 1, 'GOING', NULL, now())
                    """);

            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

            assertThat(single(statement, "SELECT string_agg(managed_ref_id::text, ',' ORDER BY managed_ref_id) FROM conversations WHERE managed_by = 'EVENT'"))
                    .isEqualTo("1,4");
            assertThat(single(statement, "SELECT group_name || '|' || group_image || '|' || chat_type FROM conversations WHERE managed_by = 'EVENT' AND managed_ref_id = 1"))
                    .isEqualTo("Subida|https://img/e1.png|GROUP");
            assertThat(single(statement, "SELECT string_agg(p.user_id::text, ',' ORDER BY p.user_id) FROM conversation_participants p "
                    + "JOIN conversations c ON c.id = p.conversation_id WHERE c.managed_by = 'EVENT' AND c.managed_ref_id = 1")).isEqualTo("1,2");
            assertThat(single(statement, "SELECT count(*) FROM events WHERE reminder_24h_sent_at IS NOT NULL OR reminder_2h_sent_at IS NOT NULL")).isEqualTo("0");
            // One chat per event
            assertThatThrownBy(() -> statement.executeUpdate("""
                    INSERT INTO conversations (public_id, chat_type, managed_by, managed_ref_id, created_at)
                    VALUES ('20000000-0000-7000-8000-000000000001', 'GROUP', 'EVENT', 1, now())
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
