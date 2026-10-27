package io.github.yagogefaell.asaltamontes.event.maintenance;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.yagogefaell.asaltamontes.event.service.EventReminder;

/**
 * Reminders 24 h and 2 h before an event (T21), every 5 minutes. Each reminder is claimed with one
 * {@code UPDATE ... WHERE reminder_*_sent_at IS NULL ... RETURNING id}: an event is only ever returned once, so it
 * is idempotent however often the job runs, and even if several instances ran it at the same time no ShedLock is
 * needed for correctness (the database decides who gets each event). The notifications are created after the
 * commit by {@code NotificationListener}.
 *
 * <p>Each reminder has a one-hour window ({@code (23 h, 24 h]} and {@code (1 h, 2 h]} before the start), so an
 * event created or moved to start in 5 hours is not told "24 h before", and a job that was down for more than
 * an hour skips the reminder instead of sending it late. Moving the start resets the claims (EventService).
 */
@Service
public class EventReminderJob {

    private static final Logger log = LoggerFactory.getLogger(EventReminderJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public EventReminderJob(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate,
                            ApplicationEventPublisher eventPublisher) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void run() {
        try {
            run(Instant.now());
        } catch (RuntimeException e) {
            // The next tick tries again; a failing tick must not stop the schedule
            log.error("Event reminders failed", e);
        }
    }

    /** Returns how many reminders were claimed (one per event and kind). */
    public int run(Instant now) {
        Integer sent = transactionTemplate.execute(status -> {
            List<Long> in24h = claim("reminder_24h_sent_at", now, Duration.ofHours(23), Duration.ofHours(24));
            List<Long> in2h = claim("reminder_2h_sent_at", now, Duration.ofHours(1), Duration.ofHours(2));
            in24h.forEach(id -> eventPublisher.publishEvent(new EventReminder(id, 24)));
            in2h.forEach(id -> eventPublisher.publishEvent(new EventReminder(id, 2)));
            return in24h.size() + in2h.size();
        });
        int total = sent == null ? 0 : sent;
        if (total > 0) {
            log.info("Event reminders: {} sent", total);
        }
        return total;
    }

    // The column name is one of two constants of this class, never input
    private List<Long> claim(String column, Instant now, Duration from, Duration to) {
        return new ArrayList<>(jdbcTemplate.queryForList("""
                UPDATE events SET %s = ?
                WHERE %s IS NULL AND status = 'SCHEDULED' AND starts_at > ? AND starts_at <= ?
                RETURNING id
                """.formatted(column, column), Long.class,
                Timestamp.from(now), Timestamp.from(now.plus(from)), Timestamp.from(now.plus(to))));
    }
}
