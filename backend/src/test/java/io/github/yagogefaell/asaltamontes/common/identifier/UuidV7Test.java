package io.github.yagogefaell.asaltamontes.common.identifier;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void generatedIdsAreVersion7WithTheRfcVariantAndTheCurrentTime() {
        long before = System.currentTimeMillis();
        UUID id = UuidV7.next();
        long after = System.currentTimeMillis();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
        assertThat(UuidV7.isV7(id)).isTrue();
        long millis = id.getMostSignificantBits() >>> 16;
        // A burst may borrow a few milliseconds from the future, never from the past
        assertThat(millis).isBetween(before, after + 5);
        assertThat(Instant.ofEpochMilli(millis)).isNotNull();
    }

    @Test
    void idsAreStrictlyIncreasingAndUnique_evenInsideTheSameMillisecond() {
        List<UUID> ids = IntStream.range(0, 20_000).mapToObj(i -> UuidV7.next()).toList();

        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);
        // String order (what the unique index sorts by, byte-wise) is creation order
        for (int i = 1; i < ids.size(); i++) {
            assertThat(unsignedCompare(ids.get(i - 1), ids.get(i))).isNegative();
        }
    }

    @Test
    void aV4UuidIsNotV7() {
        assertThat(UuidV7.isV7(UUID.randomUUID())).isFalse();
    }

    private static int unsignedCompare(UUID a, UUID b) {
        int high = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}
