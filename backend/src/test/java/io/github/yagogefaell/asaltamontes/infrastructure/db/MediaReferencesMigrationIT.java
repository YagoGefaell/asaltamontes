package io.github.yagogefaell.asaltamontes.infrastructure.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;

/** V8 must turn the URLs of our own uploads into media ids on a database that already has data (T17c). */
class MediaReferencesMigrationIT extends AbstractIntegrationTest {

    private static final String OWN = "11111111-1111-4111-8111-111111111111";
    private static final String OTHERS = "22222222-2222-4222-8222-222222222222";
    private static final String COVER = "33333333-3333-4333-8333-333333333333";
    private static final String GROUP = "44444444-4444-4444-8444-444444444444";
    private static final String FOREIGN_GROUP = "66666666-6666-4666-8666-666666666666";
    private static final String AVATAR5 = "77777777-7777-4777-8777-777777777777";
    private static final String MISSING = "55555555-5555-4555-8555-555555555555";

    @Test
    void v8_linksExistingInternalUrlsAndLeavesTheRestAsExternal() throws Exception {
        String name = "migration_media_refs";
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.executeUpdate("DROP DATABASE IF EXISTS " + name);
            statement.executeUpdate("CREATE DATABASE " + name);
        }
        String url = POSTGRES.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + name + "$1");
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).target("7").load().migrate();

        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO users (id, username, email, role, is_verified, created_at) VALUES
                        (1, 'ana', 'ana@test.local', 'USER', false, now()),
                        (2, 'bea', 'bea@test.local', 'USER', false, now()),
                        (3, 'carla', 'carla@test.local', 'USER', false, now()),
                        (4, 'dani', 'dani@test.local', 'USER', false, now()),
                        (5, 'eva', 'eva@test.local', 'USER', false, now())
                    """);
            statement.executeUpdate("""
                    INSERT INTO media (id, owner_id, purpose, status, width, height, bytes, created_at) VALUES
                        ('%s', 1, 'AVATAR', 'READY', 100, 100, 10, now()),
                        ('%s', 1, 'AVATAR', 'READY', 100, 100, 10, now()),
                        ('%s', 1, 'COMMUNITY_COVER', 'READY', 100, 100, 10, now()),
                        ('%s', 1, 'GROUP_IMAGE', 'READY', 100, 100, 10, now()),
                        ('%s', 3, 'GROUP_IMAGE', 'READY', 100, 100, 10, now()),
                        ('%s', 5, 'AVATAR', 'READY', 100, 100, 10, now())
                    """.formatted(OWN, OTHERS, COVER, GROUP, FOREIGN_GROUP, AVATAR5));
            statement.executeUpdate("""
                    INSERT INTO user_profiles (user_id, profile_picture_url, followers_count, following_count, is_public_profile) VALUES
                        (1, '/media/avatar/%s/medium.jpg', 0, 0, false),
                        (2, '/media/avatar/%s/medium.jpg', 0, 0, false),
                        (3, 'https://lh3.googleusercontent.com/a/x', 0, 0, false),
                        (4, '/media/avatar/%s/medium.jpg', 0, 0, false),
                        (5, 'https://evil.example/media/avatar/%s/medium.jpg', 0, 0, false)
                    """.formatted(OWN, OTHERS, MISSING, AVATAR5));
            statement.executeUpdate("""
                    INSERT INTO communities (name, slug, image_url, member_count, created_by_id, created_at) VALUES
                        ('Con portada', 'con-portada', 'http://192.168.1.5/media/community-cover/%s/medium.jpg', 1, 1, now()),
                        ('Portada ajena', 'portada-ajena', '/media/community-cover/%s/medium.jpg', 1, 2, now()),
                        ('Sin portada', 'sin-portada', NULL, 1, 1, now())
                    """.formatted(COVER, COVER));
            statement.executeUpdate("""
                    INSERT INTO conversations (chat_type, group_name, group_image, created_at) VALUES
                        ('GROUP', 'Con imagen', '/media/group-image/%s/medium.jpg', now()),
                        ('GROUP', 'Imagen externa', 'https://example.com/a.png', now()),
                        ('GROUP', 'Imagen ajena', '/media/group-image/%s/medium.jpg', now())
                    """.formatted(GROUP, FOREIGN_GROUP));
            statement.executeUpdate("""
                    INSERT INTO conversation_participants (conversation_id, user_id, unread_count, joined_at)
                    SELECT id, 1, 0, now() FROM conversations WHERE group_name IN ('Con imagen', 'Imagen ajena')
                    """);

            Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();

            assertThat(avatarOf(statement, 1)).isEqualTo(OWN);
            assertThat(avatarOf(statement, 2)).isNull(); // media of another user: not linked
            assertThat(avatarOf(statement, 3)).isNull(); // external URL
            assertThat(avatarOf(statement, 4)).isNull(); // media that does not exist
            assertThat(single(statement, "SELECT profile_picture_url FROM user_profiles WHERE user_id = 3"))
                    .isEqualTo("https://lh3.googleusercontent.com/a/x");
            assertThat(single(statement, "SELECT cover_media_id FROM communities WHERE slug = 'con-portada'")).isEqualTo(COVER);
            assertThat(single(statement, "SELECT cover_media_id FROM communities WHERE slug = 'portada-ajena'")).isNull();
            assertThat(single(statement, "SELECT cover_media_id FROM communities WHERE slug = 'sin-portada'")).isNull();
            assertThat(single(statement, "SELECT group_media_id FROM conversations WHERE group_name = 'Con imagen'")).isEqualTo(GROUP);
            assertThat(single(statement, "SELECT group_media_id FROM conversations WHERE group_name = 'Imagen externa'")).isNull();

            // A foreign host in front of our path is gone: the text column is rewritten to the relative URL
            assertThat(avatarOf(statement, 5)).isEqualTo(AVATAR5);
            assertThat(single(statement, "SELECT profile_picture_url FROM user_profiles WHERE user_id = 5"))
                    .isEqualTo("/media/avatar/" + AVATAR5 + "/medium.jpg");
            assertThat(single(statement, "SELECT image_url FROM communities WHERE slug = 'con-portada'"))
                    .isEqualTo("/media/community-cover/" + COVER + "/medium.jpg");
            // Group image owned by somebody who is not in the group: not linked, kept as a legacy value
            assertThat(single(statement, "SELECT group_media_id FROM conversations WHERE group_name = 'Imagen ajena'")).isNull();

            // ON DELETE SET NULL
            statement.executeUpdate("DELETE FROM media WHERE id = '" + OWN + "'");
            assertThat(avatarOf(statement, 1)).isNull();
        }
    }

    private static String avatarOf(Statement statement, int userId) throws Exception {
        return single(statement, "SELECT avatar_media_id FROM user_profiles WHERE user_id = " + userId);
    }

    private static String single(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
