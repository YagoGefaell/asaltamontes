package io.github.yagogefaell.asaltamontes.user.export;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * "Download my data" (T24d, GDPR portability): one JSON document with what the user herself put into the app.
 * <p>
 * Scope: only rows she authored or owns. Other people appear only as the public username of someone she follows or
 * blocked (her own action) and as the public id / slug of the thing she acted on; never their profile, their posts,
 * their messages or any internal bigint id (D-021). Tokens, password hash, sessions and moderation notes are left out.
 * <p>
 * Size: every list is read in keyset pages of {@link #PAGE_SIZE} rows and written straight to the stream, so memory
 * does not grow with the account, and each list stops at {@code maxRowsPerSection} (named in {@code truncatedSections}).
 * No connection is held while the client reads: each page is a short autocommit query.
 */
@Service
public class DataExportService {

    static final int PAGE_SIZE = 500;
    static final String FORMAT = "asaltamontes-export-v1";

    /** A list of rows; the SQL takes (userId, lastKey, limit) and must expose {@code k} (increasing bigint) ordered by it. */
    private record Section(String name, String sql) {}

    private static final String ACCOUNT_SQL = """
            SELECT u.username, u.email, u.is_verified AS "emailVerified", u.created_at AS "createdAt",
                   u.last_connection AS "lastConnection", p.full_name AS "fullName", p.bio, p.city,
                   p.running_level AS "runningLevel", p.birth_date AS "birthDate", p.phone_number AS "phoneNumber",
                   p.profile_picture_url AS "profilePictureUrl", p.is_public_profile AS "publicProfile",
                   p.dm_policy AS "dmPolicy", p.followers_count AS "followersCount", p.following_count AS "followingCount"
            FROM users u LEFT JOIN user_profiles p ON p.user_id = u.id
            WHERE u.id = ?""";

    private static final String PREFERENCES_SQL =
            "SELECT type, enabled FROM notification_preferences WHERE user_id = ? ORDER BY type";

    private static final List<Section> SECTIONS = List.of(
            new Section("posts", """
                    SELECT p.id AS k, p.public_id AS id, c.slug AS "communitySlug", p.body, p.created_at AS "createdAt",
                           p.like_count AS "likeCount", p.comment_count AS "commentCount"
                    FROM posts p LEFT JOIN communities c ON c.id = p.community_id
                    WHERE p.author_id = ? AND p.deleted_at IS NULL AND p.id > ? ORDER BY p.id LIMIT ?"""),
            new Section("comments", """
                    SELECT pc.id AS k, pc.public_id AS id, p.public_id AS "postId", pc.body, pc.created_at AS "createdAt"
                    FROM post_comments pc JOIN posts p ON p.id = pc.post_id
                    WHERE pc.author_id = ? AND pc.deleted_at IS NULL AND pc.id > ? ORDER BY pc.id LIMIT ?"""),
            new Section("likes", """
                    SELECT pl.post_id AS k, p.public_id AS "postId", pl.created_at AS "createdAt"
                    FROM post_likes pl JOIN posts p ON p.id = pl.post_id
                    WHERE pl.user_id = ? AND pl.post_id > ? ORDER BY pl.post_id LIMIT ?"""),
            new Section("messages", """
                    SELECT m.id AS k, m.public_id AS id, cv.public_id AS "conversationId", cv.chat_type AS "chatType",
                           m.content, m.sent_at AS "sentAt"
                    FROM messages m JOIN conversations cv ON cv.id = m.conversation_id
                    WHERE m.sender_id = ? AND m.type = 'USER' AND m.id > ? ORDER BY m.id LIMIT ?"""),
            new Section("eventsOrganized", """
                    SELECT e.id AS k, e.public_id AS id, e.title, e.description, e.type, e.status,
                           e.starts_at AS "startsAt", e.ends_at AS "endsAt", e.city,
                           e.meeting_point_name AS "meetingPointName", e.visibility, e.created_at AS "createdAt"
                    FROM events e
                    WHERE e.organizer_id = ? AND e.id > ? ORDER BY e.id LIMIT ?"""),
            new Section("eventAttendance", """
                    SELECT ea.id AS k, e.public_id AS "eventId", e.title AS "eventTitle", ea.status, ea.joined_at AS "joinedAt"
                    FROM event_attendees ea JOIN events e ON e.id = ea.event_id
                    WHERE ea.user_id = ? AND ea.id > ? ORDER BY ea.id LIMIT ?"""),
            new Section("communities", """
                    SELECT cm.id AS k, c.slug, c.name, cm.role, cm.status, cm.joined_at AS "joinedAt"
                    FROM community_members cm JOIN communities c ON c.id = cm.community_id
                    WHERE cm.user_account_id = ? AND cm.id > ? ORDER BY cm.id LIMIT ?"""),
            new Section("following", """
                    SELECT uf.id AS k, u.username, uf.created_at AS "createdAt"
                    FROM user_follow uf JOIN users u ON u.id = uf.following_id
                    WHERE uf.follower_id = ? AND uf.id > ? ORDER BY uf.id LIMIT ?"""),
            new Section("blockedUsers", """
                    SELECT ub.blocked_id AS k, u.username, ub.created_at AS "createdAt"
                    FROM user_blocks ub JOIN users u ON u.id = ub.blocked_id
                    WHERE ub.blocker_id = ? AND ub.blocked_id > ? ORDER BY ub.blocked_id LIMIT ?"""),
            // No content_snapshot, resolution or handler: they are copies of other people's text or internal moderation notes
            new Section("reportsFiled", """
                    SELECT r.id AS k, r.public_id AS id, r.target_type AS "targetType", r.target_public_id AS "targetId",
                           r.reason, r.details, r.status, r.created_at AS "createdAt"
                    FROM reports r
                    WHERE r.reporter_id = ? AND r.id > ? ORDER BY r.id LIMIT ?"""));

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final int maxRowsPerSection;

    public DataExportService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                             @Value("${asaltamontes.export.max-rows-per-section:50000}") int maxRowsPerSection) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.maxRowsPerSection = maxRowsPerSection;
    }

    /** Writes the export of {@code userId} as JSON; does not close {@code out}. */
    public void write(Long userId, OutputStream out) throws IOException {
        List<String> truncated = new ArrayList<>();
        try (JsonGenerator gen = objectMapper.getFactory().createGenerator(out, JsonEncoding.UTF8)) {
            gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            gen.setCodec(objectMapper);
            gen.writeStartObject();
            gen.writeStringField("format", FORMAT);
            gen.writeStringField("exportedAt", Instant.now().toString());
            gen.writeFieldName("account");
            List<Map<String, Object>> account = jdbc.queryForList(ACCOUNT_SQL, userId);
            gen.writeObject(account.isEmpty() ? Map.of() : account.get(0));
            gen.writeFieldName("notificationPreferences");
            gen.writeObject(jdbc.queryForList(PREFERENCES_SQL, userId));
            for (Section section : SECTIONS) {
                if (writeSection(gen, section, userId)) {
                    truncated.add(section.name());
                }
            }
            gen.writeFieldName("truncatedSections");
            gen.writeObject(truncated);
            gen.writeEndObject();
        }
    }

    /** Returns true when the list was cut at the cap. */
    private boolean writeSection(JsonGenerator gen, Section section, Long userId) throws IOException {
        gen.writeFieldName(section.name());
        gen.writeStartArray();
        long last = 0;
        int written = 0;
        boolean cut = false;
        while (true) {
            int limit = Math.min(PAGE_SIZE, maxRowsPerSection - written);
            if (limit <= 0) {
                // The cap is reached: is there anything left?
                cut = !jdbc.queryForList(section.sql(), userId, last, 1).isEmpty();
                break;
            }
            List<Map<String, Object>> page = jdbc.queryForList(section.sql(), userId, last, limit);
            for (Map<String, Object> row : page) {
                last = ((Number) row.remove("k")).longValue();
                gen.writeObject(row);
            }
            written += page.size();
            if (page.size() < limit) {
                break;
            }
        }
        gen.writeEndArray();
        return cut;
    }
}
