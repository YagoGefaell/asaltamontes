package io.github.yagogefaell.asaltamontes.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.yagogefaell.asaltamontes.common.identifier.UuidV7;
import io.github.yagogefaell.asaltamontes.notification.maintenance.NotificationRetentionService;
import io.github.yagogefaell.asaltamontes.support.AbstractIntegrationTest;
import io.github.yagogefaell.asaltamontes.user.domain.UserAccount;
import io.github.yagogefaell.asaltamontes.user.service.UserService;

/** T18a: retention (read 90 days, unread 180) and account deletion. */
class NotificationRetentionIT extends AbstractIntegrationTest {

    @Autowired private NotificationRetentionService retention;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserService userService;

    @Test
    void purge_deletesOldReadAndOldUnread_butKeepsWhatIsStillWithinItsLimit() {
        UserAccount bea = testData.createUser();
        Instant now = Instant.now();
        UUID readOld = insert(bea, null, now.minus(Duration.ofDays(91)), now.minus(Duration.ofDays(60)));
        UUID readRecent = insert(bea, null, now.minus(Duration.ofDays(89)), now.minus(Duration.ofDays(30)));
        UUID unreadAfter90 = insert(bea, null, now.minus(Duration.ofDays(120)), null);
        UUID unreadOld = insert(bea, null, now.minus(Duration.ofDays(181)), null);
        UUID unreadRecent = insert(bea, null, now.minus(Duration.ofDays(179)), null);

        retention.purge(now);

        assertThat(exists(readOld)).isFalse();
        assertThat(exists(unreadOld)).isFalse();
        assertThat(exists(readRecent)).isTrue();
        assertThat(exists(unreadAfter90)).isTrue();
        assertThat(exists(unreadRecent)).isTrue();
    }

    @Test
    void purge_workInBatches_withMoreRowsThanOneBatch() {
        UserAccount bea = testData.createUser();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO notifications (public_id, recipient_id, type, created_at, read_at)
                SELECT gen_random_uuid(), ?, 'NEW_FOLLOWER', ?, ? FROM generate_series(1, 2500)
                """, bea.getId(), Timestamp.from(now.minus(Duration.ofDays(200))), Timestamp.from(now.minus(Duration.ofDays(100))));

        assertThat(retention.purge(now)).isGreaterThanOrEqualTo(2500);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE recipient_id = ?", Integer.class, bea.getId())).isZero();
    }

    @Test
    void deletingAnAccount_deletesTheNotificationsShereceivedAndTheOnesSheCaused() {
        UserAccount ana = testData.createUser();
        UserAccount bea = testData.createUser();
        UserAccount cris = testData.createUser();
        UUID received = insert(ana, bea, Instant.now(), null);
        UUID caused = insert(bea, ana, Instant.now(), null);
        UUID unrelated = insert(bea, cris, Instant.now(), null);
        jdbc.update("INSERT INTO notification_preferences (user_id, type, enabled) VALUES (?, 'NEW_FOLLOWER', false)", ana.getId());

        userService.deleteUser(ana.getId());

        assertThat(exists(received)).isFalse();
        assertThat(exists(caused)).isFalse();
        assertThat(exists(unrelated)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preferences WHERE user_id = ?", Integer.class, ana.getId())).isZero();
    }

    private UUID insert(UserAccount recipient, UserAccount actor, Instant createdAt, Instant readAt) {
        UUID id = UuidV7.next();
        jdbc.update("INSERT INTO notifications (public_id, recipient_id, actor_id, type, created_at, read_at) VALUES (?, ?, ?, 'NEW_FOLLOWER', ?, ?)",
                id, recipient.getId(), actor == null ? null : actor.getId(), Timestamp.from(createdAt),
                readAt == null ? null : Timestamp.from(readAt));
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE public_id = ?", Integer.class, id) > 0;
    }
}
