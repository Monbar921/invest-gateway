package ru.invest.gateway;

import org.junit.jupiter.api.Test;
import ru.invest.gateway.authorization.service.filter.AnonymousRateLimiter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousRateLimiterTest {
    private final MutableClock clock = new MutableClock();

    @Test
    void burstUpToLimitThenWaitForRefill() {
        final AnonymousRateLimiter limiter = new AnonymousRateLimiter(60, 100, clock);

        for (int i = 0; i < 60; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        }
        // 60 в минуту - один запрос в секунду
        assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(1);
    }

    @Test
    void refillDoesNotExceedLimit() {
        final AnonymousRateLimiter limiter = new AnonymousRateLimiter(2, 100, clock);
        limiter.tryAcquire("10.0.0.1");

        clock.advance(Duration.ofHours(1));

        assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        assertThat(limiter.tryAcquire("10.0.0.1")).isEqualTo(30);
    }

    @Test
    void zeroMeansNoLimit() {
        final AnonymousRateLimiter limiter = new AnonymousRateLimiter(0, 100, clock);
        for (int i = 0; i < 1000; i++) {
            assertThat(limiter.tryAcquire("10.0.0.1")).isZero();
        }
    }

    @Test
    void manyClientsDoNotGrowMemoryWithoutBound() {
        final AnonymousRateLimiter limiter = new AnonymousRateLimiter(10, 3, clock);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("10.0.0." + i);
        }
        // при переполнении старые записи сбрасываются; новый клиент всё равно проходит
        assertThat(limiter.tryAcquire("10.0.1.1")).isZero();
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-10T10:00:00Z");

        void advance(final Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
