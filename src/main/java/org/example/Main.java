package org.example;

import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {
    public static void main(String[] args) {

        // Загрузка конфига (config.properties) и хранилища
        Config config = new Config();
        InMemoryStorage storage = new InMemoryStorage(config.dataFile());
        LinkService linkService = new LinkService(storage, config);
        UserService userService = new UserService(storage);

        // Создание пользователя
        System.out.println("Добро пожаловать в программу по сокращению ссылок!");
        userService.ensureUser(null);
        System.out.println("Ваш UUID: " + userService.getCurrentUser());
        System.out.println(" ");
        System.out.println("Введите команду 'help', чтобы увидеть список доступных команд\n");

        // Запуск фоновой задачи очистки истёкших ссылок
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(1);
        new CleanupTask(storage, exec).start();

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("> ");
            if (!sc.hasNextLine()) break;

            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String cmd = parts[0].toLowerCase();

            try {
                switch (cmd) {

                    case "help":
                        printHelp();
                        break;

                    case "whoami":
                        System.out.println("Ваш UUID: " + userService.getCurrentUser());
                        break;

                    case "setuid":
                        handleSetUid(parts, userService, storage);
                        break;

                    case "create":
                        handleCreate(parts, config, linkService, userService.getCurrentUser());
                        break;

                    case "info":
                        handleInfo(parts, linkService, userService.getCurrentUser());
                        break;

                    case "open":
                        handleOpen(parts, linkService);
                        break;

                    case "delete":
                        handleDelete(parts, linkService, userService.getCurrentUser());
                        break;

                    case "edit":
                        handleEdit(parts, linkService, userService.getCurrentUser());
                        break;

                    case "list":
                        handleList(storage, userService.getCurrentUser());
                        break;

                    case "exit":
                        exec.shutdownNow();
                        System.out.println("Завершение работы ...");
                        return;

                    case "clear":
                        clearConsole();
                        break;

                    default:
                        System.out.println("Неизвестная команда. Введите: 'help', для просмотра списка доступных команд");
                        break;
                }
            } catch (Exception e) {
                System.err.println("Ошибка: " + e.getMessage());
            }
        }
    }

    // Команда setuid
    private static void handleSetUid(String[] parts, UserService userService, StorageService storage) {
        if (parts.length < 2) {
            System.out.println("Использование: setuid <UUID>");
            return;
        }

        String input = parts[1];

        String match = null;
        if (storage instanceof InMemoryStorage) {
            match = ((InMemoryStorage) storage).findUserUuidByPrefix(input);
        }

        if (match != null) {
            userService.ensureUser(match);
            System.out.println("Переключено на пользователя: " + match);
        } else {
            System.out.println("Пользователь с таким UUID не найден");
        }
    }

    // Команда create
    private static void handleCreate(String[] parts, Config config, LinkService linkService, String currentUser) {
        if (parts.length < 2) {
            System.out.println("Использование: create <URL> [лимит кликов] [TTL в сек]");
            return;
        }

        String url = parts[1];

        long maxClicks = config.defaultMaxClicks();
        if (parts.length >= 3) {
            try {
                maxClicks = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                System.out.println("Лимит кликов должен быть числом");
                return;
            }
        }

        long ttlSeconds = config.defaultTtlSeconds();
        if (parts.length >= 4) {
            try {
                ttlSeconds = Long.parseLong(parts[3]);
            } catch (NumberFormatException e) {
                System.out.println("TTL должно быть числом");
                return;
            }
        }

        linkService.create(currentUser, url, maxClicks, ttlSeconds);
    }

    // Команда info
    private static void handleInfo(String[] parts, LinkService linkService, String currentUser) {
        if (parts.length < 2) {
            System.out.println("Использование: info <короткая ссылка>");
            return;
        }

        Optional<ShortLink> info = linkService.info(parts[1]);

        info.ifPresentOrElse(l -> {
            boolean mine = l.getOwnerUuid().equals(currentUser);
            boolean infinite = l.getTtlMillis() == 0;

            String ttlDisplay;

            if (infinite) {
                ttlDisplay = "∞";
            } else {
                long left = (l.getTtlMillis() - (System.currentTimeMillis() - l.getCreatedAt())) / 1000;
                if (left < 0) left = 0;

                ttlDisplay = left + " сек из " + (l.getTtlMillis() / 1000) + " сек";
            }
            System.out.printf("""
                    Информация о ссылке:
                    Короткая ссылка: %s
                    URL: %s
                    Владелец: %s%s
                    Кликов: %d/%s
                    Создана: %s
                    TTL: %s
                    """,
                l.getCode(),
                l.getOriginalUrl(),
                l.getOwnerUuid(),
                mine ? " [Вы]" : "",
                l.getClickCount(),
                l.getMaxClicks() == 0 ? "∞" : String.valueOf(l.getMaxClicks()),
                formatTimestamp(l.getCreatedAt()),
                ttlDisplay
            );
        }, () -> System.out.println("Ссылка не найдена"));
    }

    // Команда open
    private static void handleOpen(String[] parts, LinkService linkService) {
        if (parts.length < 2) {
            System.out.println("Использование: open <короткая ссылка>");
            return;
        }
        linkService.open(parts[1]);
    }

    // Команда delete
    private static void handleDelete(String[] parts, LinkService linkService, String currentUser) {
        if (parts.length < 2) {
            System.out.println("Использование: delete <короткая ссылка>");
            return;
        }
        linkService.delete(parts[1], currentUser);
    }

    // Команда edit
    private static void handleEdit(String[] parts, LinkService linkService, String currentUser) {
        if (parts.length < 4) {
            System.out.println("Использование: edit <короткая ссылка> limit | ttl [значение]");
            return;
        }

        String code = parts[1];
        String field = parts[2];

        switch (field) {

            case "limit":
                try {
                    long newLimit = Long.parseLong(parts[3]);
                    boolean okLimit = linkService.editLimit(code, currentUser, newLimit);
                    System.out.println(okLimit ? "Лимит кликов изменён" : "Не удалось изменить лимит кликов");
                } catch (NumberFormatException e) {
                    System.out.println("Значение 'limit' должно быть числом");
                }
                break;

            case "ttl":
                try {
                    long newTtl = Long.parseLong(parts[3]);
                    boolean okTtl = linkService.editTtl(code, currentUser, newTtl);
                    System.out.println(okTtl ? "TTL изменён" : "Не удалось изменить TTL");
                } catch (NumberFormatException e) {
                    System.out.println("Значение 'ttl' должно быть числом.");
                }
                break;

            default:
                System.out.println("Неизвестный параметр. Используйте: 'limit' или 'ttl'");
        }
    }

    // Команда list
    private static void handleList(StorageService storage, String currentUser) {

        var all = storage.allLinks();

        if (all.isEmpty()) {
            System.out.println("Список пуст");
            return;
        }

        all.forEach(l -> {
                boolean mine = l.getOwnerUuid().equals(currentUser);

            String ttlDisplay;

            if (l.getTtlMillis() == 0) {
                ttlDisplay = "∞";
            } else {
                long ttlLeft = (l.getTtlMillis() - (System.currentTimeMillis() - l.getCreatedAt())) / 1000;
                if (ttlLeft < 0) ttlLeft = 0;
                ttlDisplay = ttlLeft + " сек";
            }

            System.out.printf("Короткая ссылка: %s -> %s (владелец: %s%s, кликов: %d/%s, TTL: %s)%n",
                l.getCode(),
                l.getOriginalUrl(),
                l.getOwnerUuid().substring(0, 8),
                mine ? " [Вы]" : "",
                l.getClickCount(),
                l.getMaxClicks() == 0 ? "∞" : String.valueOf(l.getMaxClicks()),
                ttlDisplay
            );
        });
    }

    // Команда clear
    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            System.out.println("Не удалось очистить терминал");
        }
    }

    // Форматирование времени
    private static String formatTimestamp(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // Вывод команд
    private static void printHelp() {
        System.out.println("""
                ------------------------------------------------------------------------------------------------------------
                whoami    -    показать UUID текущего пользователя
                ------------------------------------------------------------------------------------------------------------
                setuid <UUID пользователя>    -    переключиться на другого пользователя
                ------------------------------------------------------------------------------------------------------------
                create <URL> [лимит кликов] [TTL в сек]    -    создать короткую ссылку
                (по-умолчанию: лимит кликов = 0 (лимита нет); TTL = 0 (лимита нет))
                ------------------------------------------------------------------------------------------------------------
                open <короткая ссылка>    -    открыть короткую ссылку в браузере
                ------------------------------------------------------------------------------------------------------------
                info <короткая ссылка>    -    показать информацию о короткой ссылке
                ------------------------------------------------------------------------------------------------------------
                edit <короткая ссылка> limit | ttl <значение> - изменить количество кликов, или TTL (только владелец)
                ------------------------------------------------------------------------------------------------------------
                delete <короткая ссылка>    -    удалить ссылку (только владелец)
                ------------------------------------------------------------------------------------------------------------
                list    -    список всех ссылок
                ------------------------------------------------------------------------------------------------------------
                clear    -    очистить терминал
                ------------------------------------------------------------------------------------------------------------
                exit    -    выйти
                ------------------------------------------------------------------------------------------------------------
                """);
    }
}
