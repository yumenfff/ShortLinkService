package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CleanupTask {

    // Хранилище ссылок
    private final StorageService storage;

    // Планировщик, который позволяет выполнять задачу периодически
    private final ScheduledExecutorService executor;

    // Конструктор
    public CleanupTask(StorageService storage, ScheduledExecutorService executor) {
        this.storage = storage;
        this.executor = executor;
    }

    // Запуск периодической очистки
    public void start() {
        int intervalSeconds = 1; //  проверка каждую секунду

        executor.scheduleAtFixedRate(() -> {
            try {
                // Список кодов ссылок, которые нужно удалить
                List<String> toRemove = new ArrayList<>();

                // Проверка ссылок в хранилище
                for (ShortLink l : storage.allLinks()) {
                    // Если TTL истёк, то удаление
                    if (l.isExpired()) {
                        toRemove.add(l.getCode());
                    }
                }

                // Удаление ссылок с просроченным TTL
                for (String code : toRemove) {
                    // Получение ссылки
                    ShortLink l = storage.get(code).orElse(null);

                    // Удаление ссылки из хранилища
                    storage.remove(code);

                    if (l != null) {
                        // Уведомление
                        System.out.printf("Ссылка: %s устарела и была удалена (владелец: %s)%n",
                                code, l.getOwnerUuid());
                    }
                }
            } catch (Exception e) {
                System.err.println("Ошибка при очистке устаревших ссылок: " + e.getMessage());
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }
}
