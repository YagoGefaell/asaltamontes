package io.github.yagogefaell.asaltamontes.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.common.identifier.UuidV7;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;

/** V9 must give every existing row a public id (UUID v7, ordered by creation time) on a database that has data (T30a). */
class PublicIdsMigrationIT extends AbstractIntegrationTest {

    @Test
    void v9_backfillsEveryExistingRowWithAUniqueUuidV7_inCreationOrder() throws Exception {
        String name = "migration_public_ids";
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + name);
            statement.executeUpdate("CREATE DATABASE " + name);
        }
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("8").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, role, is_verified, created_at) VALUES
                        (1, 'ana', 'ana@test.local', 'USER', false, now()),
                        (2, 'bea', 'bea@test.local', 'USER', false, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO communities (name, slug, member_count, created_by_id, created_at) VALUES
                        ('Vieja', 'vieja', 1, 1, '2026-01-01 10:00:00'),
                        ('Nueva', 'nueva', 1, 1, '2026-06-01 10:00:00')
                    """);
            statement.executeUpdate("""
                    INSERT INTO conversations (chat_type, created_at) VALUES
                        ('INDIVIDUAL', '2026-02-01 10:00:00'), ('INDIVIDUAL', '2026-03-01 10:00:00')
                    """);
            statement.executeUpdate("""
                    INSERT INTO messages (conversation_id, sender_id, content, sent_at)
                    SELECT 1, 1, 'm' || n, timestamp '2026-02-01 10:00:00' + n * interval '1 second'
                    FROM generate_series(1, 50) n
                    """);
            statement.executeUpdate("""
                    INSERT INTO events (title, type, status, starts_at, timezone, visibility, organizer_id, created_at) VALUES
                        ('Evento', 'MEETUP', 'SCHEDULED', now() + interval '3 days', 'Europe/Madrid', 'PUBLIC', 1, now())
                    """);
        }

        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            for (String table : List.of("communities", "conversations", "messages", "events")) {
                try (ResultSet rows = statement.executeQuery("SELECT public_id FROM " + table + " ORDER BY id")) {
                    List<UUID> ids = new ArrayList<>();
                    while (rows.next()) {
                        ids.add(rows.getObject(1, UUID.class));
                    }
                    assertThat(ids).as(table).isNotEmpty().doesNotHaveDuplicates().allMatch(UuidV7::isV7);
                    // Created in this order, so they sort the same way
                    assertThat(ids).as(table).isSortedAccordingTo(UUID::compareTo);
                }
            }
            try (ResultSet notNull = statement.executeQuery("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE column_name = 'public_id' AND is_nullable = 'NO' AND table_name IN
                          ('communities', 'conversations', 'messages', 'events')
                    """)) {
                notNull.next();
                assertThat(notNull.getInt(1)).isEqualTo(4);
            }
            try (ResultSet unique = statement.executeQuery("""
                    SELECT count(*) FROM pg_indexes WHERE indexname LIKE 'uk\\_%\\_public\\_id'
                    """)) {
                unique.next();
                assertThat(unique.getInt(1)).isEqualTo(4);
            }
        }
    }
}
