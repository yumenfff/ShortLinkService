package org.example;

import java.awt.*;
import java.net.URI;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class LinkService {
    private final StorageService storage;
    private final Config config;
    // Генератор случайных значений
    private final Random random = ThreadLocalRandom.current();

    public LinkService(StorageService storage, Config config) {
        this.storage = storage;
        this.config = config;
    }

    // Генерация случайной короткой ссылки
    private String generateCode() {
        int len = config.shortcodeLength();
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    // Генерация уникальной короткой ссылки и проверка ее отсутствия в хранилище
    private String generateUniqueCode() {
        for (int i = 0; i < 50; i++) {
            String code = generateCode();
            if (storage.get(code).isEmpty())
                return code;
        }
        throw new IllegalStateException("Невозможно сгенерировать уникальную ссылку");
    }

    // Создание новой короткой ссылки
    public ShortLink create(String ownerUuid, String originalUrl, long maxClicks, long ttlSeconds) {
        // Валидация URL
        if (!UrlValidator.isValid(originalUrl))
            throw new IllegalArgumentException("Некорректный URL-адрес");

        if (maxClicks < 0)
            throw new IllegalArgumentException("Лимит кликов не может быть отрицательным");

        if (ttlSeconds < 0)
            throw new IllegalArgumentException("Время жизни не может быть отрицательным");

        String code = generateUniqueCode();
        // Перевод TTL в миллисекунды
        long ttl = ttlSeconds * 1000L;

        // Создание объекта ссылки
        ShortLink link = new ShortLink(code, originalUrl, ownerUuid, ttl, maxClicks);
        // Сохранение в хранилище
        storage.put(link);

        System.out.printf("Создана короткая ссылка: %s -> %s (лимит кликов: %s, TTL: %s)%n",
            code,
            originalUrl,
            maxClicks == 0 ? "∞" : String.valueOf(maxClicks),
            ttlSeconds == 0 ? "∞" : ttlSeconds + " сек");
        return link;
    }

    // Открытие короткой ссылки в браузере
    public void open(String code) {
        Optional<ShortLink> maybe = storage.get(code);

        if (maybe.isEmpty()) {
            System.out.printf("Ссылка: %s не найдена%n", code);
            return;
        }

        ShortLink link = maybe.get();

        // Проверка TTL
        if (link.isExpired()) {
            storage.remove(code);
            System.out.printf("Ссылка: %s истекла и была удалена%n", code);
            return;
        }

        // Проверка лимита кликов до увеличения счетчика кликов
        if (link.isDepleted()) {
            storage.remove(code);
            System.out.printf("Ссылка: %s исчерпала лимит кликов и была удалена%n", code);
            return;
        }

        // Увеличиваем количество кликов
        link.increaseClick();
        storage.put(link);

        // Проверка не достигнут ли лимит после клика
        if (link.isDepleted()) {
            System.out.printf("Ссылка: %s достигла лимита кликов и была удалена%n", code);
            storage.remove(code);
        }

        // Переход в браузере
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(link.getOriginalUrl()));
            } else {
                System.out.println("Открытие ссылок не поддерживается на данном устройстве");
            }
        } catch (Exception e) {
            System.err.println("Ошибка! Не удалось открыть ссылку: " + e.getMessage());
        }
    }

    // Получение информации о короткой ссылке
    public Optional<ShortLink> info(String code) {
        return storage.get(code);
    }

    // Удаление ссылки (только владелец)
    public boolean delete(String code, String requesterUuid) {
        Optional<ShortLink> maybe = storage.get(code);

        if (maybe.isEmpty()) {
            System.out.printf("Ссылка: %s не найдена%n", code);
            return false;
        }

        ShortLink link = maybe.get();

        if (!link.getOwnerUuid().equals(requesterUuid)) {
            System.out.println("У вас нет прав для удаления этой ссылки");
            return false;
        }

        storage.remove(code);
        System.out.printf("Ссылка: %s удалена%n", code);
        return true;
    }

    // Редактирование лимита кликов (только владелец)
    public boolean editLimit(String code, String requesterUuid, long newMaxClicks) {
        Optional<ShortLink> maybe = storage.get(code);

        if (maybe.isEmpty()) {
            System.out.printf("Ссылка: %s не найдена%n", code);
            return false;
        }

        if (newMaxClicks < 0) {
            System.out.println("Лимит кликов не может быть отрицательным");
            return false;
        }

        ShortLink old = maybe.get();

        // Проверка доступа
        if (!old.getOwnerUuid().equals(requesterUuid)) {
            System.out.println("У вас нет прав для изменения этой ссылки");
            return false;
        }

        ShortLink updated = new ShortLink(
                old.getCode(),
                old.getOriginalUrl(),
                old.getOwnerUuid(),
                old.getCreatedAt(),
                old.getTtlMillis(),
                newMaxClicks,
                old.getClickCount()
        );
        storage.put(updated);
        return true;
    }

    // Редактирование времени жизни (только владелец)
    public boolean editTtl(String code, String requesterUuid, long newTtlSeconds) {
        Optional<ShortLink> maybe = storage.get(code);

        if (maybe.isEmpty()) {
            System.out.printf("Ссылка: %s не найдена%n", code);
            return false;
        }

        if (newTtlSeconds < 0) {
            System.out.println("TTL не может быть отрицательным");
            return false;
        }

        ShortLink old = maybe.get();

        // Проверка прав доступа
        if (!old.getOwnerUuid().equals(requesterUuid)) {
            System.out.println("У вас нет прав для изменения этой ссылки");
            return false;
        }

        long newTtlMillis = newTtlSeconds * 1000L;

        ShortLink updated = new ShortLink(
            old.getCode(),
            old.getOriginalUrl(),
            old.getOwnerUuid(),
            System.currentTimeMillis(), // Новый TTL начинает отсчёт заново
            newTtlMillis,
            old.getMaxClicks(),
            old.getClickCount()
        );

        storage.put(updated);
        return true;
    }
}
