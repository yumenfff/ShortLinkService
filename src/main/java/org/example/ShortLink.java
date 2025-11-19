package org.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShortLink {
    // Уникальная короткая ссылка
    private final String code;
    // Исходный  URL
    private final String originalUrl;
    // UUID владельца ссылки
    private final String ownerUuid;
    // Время создания ссылки
    private final long createdAt;
    // Время жизни ссылки в миллисекундах
    private final long ttlMillis;
    // Лимит кликов
    private final long maxClicks;
    // Счётчик кликов
    private long clickCount;

    // Конструктор для Jackson
    @JsonCreator
    public ShortLink(
            @JsonProperty("code") String code,
            @JsonProperty("originalUrl") String originalUrl,
            @JsonProperty("ownerUuid") String ownerUuid,
            @JsonProperty("createdAt") long createdAt,
            @JsonProperty("ttlMillis") long ttlMillis,
            @JsonProperty("maxClicks") long maxClicks,
            @JsonProperty("clickCount") long clickCount
    ) {
        this.code = code;
        this.originalUrl = originalUrl;
        this.ownerUuid = ownerUuid;
        this.createdAt = createdAt;
        this.ttlMillis = ttlMillis;
        this.maxClicks = maxClicks;
        this.clickCount = clickCount;
    }

    // Конструктор при создании новой ссылки
    public ShortLink(String code, String originalUrl, String ownerUuid, long ttlMillis, long maxClicks) {
        this(code, originalUrl, ownerUuid, Instant.now().toEpochMilli(), ttlMillis, maxClicks, 0);
    }

    // Геттеры
    public String getCode() {
        return code;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getOwnerUuid() {
        return ownerUuid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    public long getMaxClicks() {
        return maxClicks;
    }

    public long getClickCount() {
        return clickCount;
    }

    // Увеличивает счётчик кликов на 1
    public void increaseClick() {
        this.clickCount++;
    }

    // Проверка, истёк ли срок действия ссылки
    public boolean isExpired() {
        if (ttlMillis == 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        long age = now - createdAt;
        return age >= ttlMillis;
    }

    // Проверка, исчерпан ли лимит кликов
    public boolean isDepleted() {
        return maxClicks > 0 && clickCount >= maxClicks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShortLink shortLink)) return false;
        return Objects.equals(code, shortLink.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }
}
