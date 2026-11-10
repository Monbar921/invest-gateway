package ru.invest.gateway.authorization.service.filter;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket на клиента (IP): запас {@code requestsPerMinute} запросов, пополняется равномерно с той же скоростью.
 * Короткий всплеск (открытие карточки - несколько запросов сразу) проходит, постоянное выкачивание - нет.
 * <p>
 * Состояние - в памяти одного экземпляра gateway: при нескольких репликах лимит действует на каждую отдельно.
 */
@Slf4j
public class AnonymousRateLimiter {
    private static final double MILLIS_PER_MINUTE = 60_000d;
    private static final double MILLIS_PER_SECOND = 1_000d;

    private final int capacity;
    private final int maxClients;
    private final double tokensPerMilli;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public AnonymousRateLimiter(final int requestsPerMinute, final int maxClients, final Clock clock) {
        this.capacity = requestsPerMinute;
        this.maxClients = maxClients;
        this.tokensPerMilli = requestsPerMinute / MILLIS_PER_MINUTE;
        this.clock = clock;
    }

    /**
     * Списывает запрос клиента.
     *
     * @return 0, если запрос можно выполнить, иначе - через сколько секунд появится следующий
     */
    public long tryAcquire(final String client) {
        if (capacity <= 0) {
            return 0;
        }
        final long now = clock.millis();
        Bucket bucket = buckets.get(client);
        if (bucket == null) {
            evictIfFull(now);
            bucket = buckets.computeIfAbsent(client, key -> new Bucket(capacity, now));
        }
        return bucket.tryAcquire(now);
    }

    int trackedClients() {
        return buckets.size();
    }

    /**
     * Новый клиент, а карта полна: убираем клиентов, чей запас уже восстановился (они ничего не теряют).
     * Если и это не помогло (запросы с очень многих адресов), сбрасываем всё - лучше пропустить лишнее, чем упасть по памяти.
     */
    private void evictIfFull(final long now) {
        if (buckets.size() < maxClients) {
            return;
        }
        buckets.values().removeIf(bucket -> bucket.isFull(now));
        if (buckets.size() >= maxClients) {
            log.warn("Anonymous rate limiter: {} clients tracked, resetting", buckets.size());
            buckets.clear();
        }
    }

    private final class Bucket {
        private double tokens;
        private long updatedAt;

        private Bucket(final double tokens, final long updatedAt) {
            this.tokens = tokens;
            this.updatedAt = updatedAt;
        }

        private synchronized long tryAcquire(final long now) {
            refill(now);
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return Math.max(1, (long) Math.ceil((1 - tokens) / tokensPerMilli / MILLIS_PER_SECOND));
        }

        private synchronized boolean isFull(final long now) {
            refill(now);
            return tokens >= capacity;
        }

        private void refill(final long now) {
            if (now > updatedAt) {
                tokens = Math.min(capacity, tokens + (now - updatedAt) * tokensPerMilli);
                updatedAt = now;
            }
        }
    }
}
