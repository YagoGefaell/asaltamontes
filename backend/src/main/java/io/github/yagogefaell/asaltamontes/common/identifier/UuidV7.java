package io.github.yagogefaell.asaltamontes.common.identifier;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562): 48 bits of Unix time in milliseconds, then version, a 12-bit counter and random bits.
 * Hibernate 6.6 (Spring Boot 3.5) has no v7 generator (it arrives in 7.0), and PostgreSQL 17 has none either.
 * Ids created by one JVM are strictly increasing, even inside the same millisecond (the counter), so they sort by
 * creation time and keep the unique index of {@code public_id} compact.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int COUNTER_BITS = 12;
    private static final int COUNTER_MASK = (1 << COUNTER_BITS) - 1;

    private static long lastMillis = -1;
    private static int counter;

    private UuidV7() {
    }

    public static UUID next() {
        long millis;
        int seq;
        synchronized (UuidV7.class) {
            long now = System.currentTimeMillis();
            if (now > lastMillis) {
                lastMillis = now;
                // Start in the lower half so a burst inside one millisecond has room to count up
                counter = RANDOM.nextInt(1 << (COUNTER_BITS - 1));
            } else {
                // Same millisecond (or the clock went back): keep counting; on overflow borrow the next millisecond
                counter++;
                if (counter > COUNTER_MASK) {
                    lastMillis++;
                    counter = 0;
                }
            }
            millis = lastMillis;
            seq = counter;
        }
        long msb = (millis << 16) | (0x7L << 12) | seq;
        long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }

    /** Whether the UUID is an RFC 9562 version 7 one, like the ones made here. */
    public static boolean isV7(UUID uuid) {
        return uuid.version() == 7 && uuid.variant() == 2;
    }
}
