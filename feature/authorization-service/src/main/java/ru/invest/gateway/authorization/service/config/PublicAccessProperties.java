package ru.invest.gateway.authorization.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Публичный режим: чтение облигаций без входа и ограничение частоты анонимных запросов.
 *
 * @param enabled   открыть ли чтение {@code /api/bonds/**} без входа; выключен - всё под логином, как раньше
 * @param rateLimit ограничение анонимных запросов к {@code /api/**} по IP
 */
@ConfigurationProperties("invest.gateway.public-access")
public record PublicAccessProperties(@DefaultValue("true") boolean enabled,
                                     @DefaultValue RateLimit rateLimit) {

    /**
     * Token bucket на IP: запас {@code requestsPerMinute} запросов, пополняется с той же скоростью.
     *
     * @param requestsPerMinute сколько анонимных запросов в минуту можно с одного IP; 0 - без ограничения
     * @param maxClients        сколько IP держать в памяти; при переполнении старые записи сбрасываются
     */
    public record RateLimit(@DefaultValue("120") int requestsPerMinute,
                            @DefaultValue("10000") int maxClients) {
    }
}
