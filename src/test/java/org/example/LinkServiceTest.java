package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LinkServiceTest {
    private InMemoryStorage storage;
    private LinkService linkService;
    private String userUuid;

    @BeforeEach
    void setup() {
        // Удаляю старый файл с тестами перед каждым тестом
        Config config = new Config();
        File f = new File("test_data.json");
        if (f.exists()) {
            assertTrue(f.delete(), "Не удалось удалить 'test_data.json' перед запуском теста");
        }

        // Создаю для тестов отдельный файл
        storage = new InMemoryStorage("test_data.json");
        userUuid = "test-user-uuid"; // тестовый пользователь
        linkService = new LinkService(storage, config);
    }

    @Test
    void testCreateValidLink() {
        // Проверка, что создаётся корректная ссылка, если все параметры валидны
        ShortLink link = linkService.create(userUuid, "https://google.com", 5, 60);

        assertNotNull(link);
        assertEquals("https://google.com", link.getOriginalUrl());
        assertEquals(5, link.getMaxClicks());
        assertEquals(userUuid, link.getOwnerUuid());
    }

    @Test
    void testInvalidUrlThrowsException() {
        // Проверка, что сервис отклонит некорректный URL
        assertThrows(IllegalArgumentException.class,
            () -> linkService.create(userUuid, "invalid-url", 5, 60));
    }

    @Test
    void testUniqueCodesForDifferentUsers() {
        // Проверка, что каждая созданная ссылка должна быть уникальной (даже если URL одинаковые)
        ShortLink link1 = linkService.create("user1", "https://google.com", 0, 60);
        ShortLink link2 = linkService.create("user2", "https://google.com", 0, 60);

        assertNotEquals(link1.getCode(), link2.getCode());
    }

    @Test
    void testInfoReturnsCorrectLink() {
        // Проверка, что метод info() должен вернуть ту же ссылку, что сохранена в хранилище
        ShortLink link = linkService.create(userUuid, "https://google.com", 0, 60);
        Optional<ShortLink> found = linkService.info(link.getCode());

        assertTrue(found.isPresent());
        assertEquals("https://google.com", found.get().getOriginalUrl());
    }

    @Test
    void testDeleteByOwner() {
        // Проверка, что владелец может удалить свою ссылку
        ShortLink link = linkService.create(userUuid, "https://google.com", 0, 60);

        boolean result = linkService.delete(link.getCode(), userUuid);

        assertTrue(result);
        assertTrue(storage.get(link.getCode()).isEmpty());
    }

    @Test
    void testDeleteByAnotherUserFails() {
        // Проверка, что другой пользователь не может удалить чужую ссылку
        ShortLink link = linkService.create(userUuid, "https://google.com", 0, 60);

        boolean result = linkService.delete(link.getCode(), "another-uuid");

        assertFalse(result);
        assertTrue(storage.get(link.getCode()).isPresent());
    }

    @Test
    void testEditLimitByOwner() {
        // Проверка, что владелец может изменить лимит кликов
        ShortLink link = linkService.create(userUuid, "https://google.com", 5, 60);

        boolean edited = linkService.editLimit(link.getCode(), userUuid, 10);
        assertTrue(edited);

        Optional<ShortLink> updated = storage.get(link.getCode());
        assertTrue(updated.isPresent());
        assertEquals(10, updated.get().getMaxClicks());
    }

    @Test
    void testEditLimitByAnotherUserFails() {
        // Проверка, что другой пользователь не может изменить чужой лимит
        ShortLink link = linkService.create(userUuid, "https://google.com", 5, 60);

        boolean edited = linkService.editLimit(link.getCode(), "intruder", 10);

        assertFalse(edited);
    }

    @Test
    void testClickCountIncrement() {
        // Проверка, что при вызове increaseClick() счётчик увеличивается
        ShortLink link = linkService.create(userUuid, "https://google.com", 3, 60);

        link.increaseClick();

        assertEquals(1, link.getClickCount());
    }

    @Test
    void testOpenIncreasesClickCount() {
        // Проверка, что при открытии ссылки счётчик кликов увеличивается
        ShortLink link = linkService.create(userUuid, "https://google.com", 5, 60);

        linkService.open(link.getCode());

        Optional<ShortLink> updated = storage.get(link.getCode());
        assertTrue(updated.isPresent());
        assertEquals(1, updated.get().getClickCount());
    }

    @Test
    void testLinkDeletedWhenClickLimitReached() {
        // Проверка, что ссылка удаляется, когда лимит кликов исчерпан
        ShortLink link = linkService.create(userUuid, "https://google.com", 1, 60);

        linkService.open(link.getCode());

        assertTrue(storage.get(link.getCode()).isEmpty());
    }

    @Test
    void testShortLinkIsExpiredWhenTtlPassed() {
        // Проверка, что ссылка считается просроченной, если время её TTL истекло
        long fiveSecondsAgo = System.currentTimeMillis() - 5000;
        long ttl = 1000;

        ShortLink link = new ShortLink("abc", "https://google.com", userUuid,
            fiveSecondsAgo, ttl, 0, 0);

        assertTrue(link.isExpired());
    }

    @Test
    void testNotExpiredLink() {
        // Проверка, что если TTL ещё не истёк, то ссылка активна
        ShortLink link = new ShortLink("abc", "https://google.com", userUuid,
            System.currentTimeMillis(), 100000, 0, 0);

        assertFalse(link.isExpired());
    }

    @Test
    void testDepletedLink() {
        // Проверка исчерпания лимита кликов
        ShortLink link = new ShortLink("abc", "https://google.com", userUuid,
            60000, 3);

        for (int i = 0; i < 3; i++) link.increaseClick();

        assertTrue(link.isDepleted());
    }

    @Test
    void testUnlimitedLinkNotDepleted() {
        // Проверка бесконечных кликов, ссылка не должна исчерпать лимит кликов
        ShortLink link = new ShortLink("abc", "https://google.com", userUuid,
            60000, 0);

        for (int i = 0; i < 100; i++) link.increaseClick();

        assertFalse(link.isDepleted());
    }

    @Test
    void testCreateStoresInStorage() {
        // Проверка, что метод create сохраняет ссылку в хранилище
        ShortLink link = linkService.create(userUuid, "https://google.com", 0, 60);

        assertTrue(storage.get(link.getCode()).isPresent());
    }

    @Test
    void testEditTtlChangesExpiration() {
        // Проверка, что изменение TTL меняет срок жизни ссылки, но не время создания
        ShortLink link = linkService.create(userUuid, "https://google.com", 0, 10);

        long oldTtl = link.getTtlMillis();
        long oldCreatedAt = link.getCreatedAt();

        boolean ok = linkService.editTtl(link.getCode(), userUuid, 100);
        assertTrue(ok);

        ShortLink updated = storage.get(link.getCode()).orElseThrow();
        long newTtl = updated.getTtlMillis();

        assertNotEquals(oldTtl, newTtl);
        assertEquals(100 * 1000L, newTtl);
    }
}
