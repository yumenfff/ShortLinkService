package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStorage implements StorageService {

    // Map всех коротких ссылок
    private final Map<String, ShortLink> links = new ConcurrentHashMap<>();

    // Map всех пользователей
    private final Map<String, User> users = new ConcurrentHashMap<>();

    // Jackson ObjectMapper для JSON
    private final ObjectMapper mapper;

    // Файл, где хранится JSON с данными
    private final File file;

    // Класс, описывающий структуру JSON файла
    static class Dump {
        public List<ShortLink> links;
        public List<User> users;
    }

    // Конструктор инициализирует мапперы и загружает данные из файла
    public InMemoryStorage(String path) {
        this.file = new File(path);

        this.mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    // Загружает данные из файла JSON. Если файла нет, то создается новый файл
    private synchronized void load() {
        if (!file.exists()) {
            System.out.println("Файл данных не найден, создан новый файл\n");
            return;
        }

        if (file.length() == 0) {
            System.out.println("Файл данных пуст\n");
            return;
        }

        try {
            Dump d = mapper.readValue(file, Dump.class);

            if (d != null) {
                if (d.links != null) d.links.forEach(l -> links.put(l.getCode(), l));
                if (d.users != null) d.users.forEach(u -> users.put(u.getUuid(), u));
            }
            System.out.printf(
                "Файл данных загружен (ссылок: %d, пользователей: %d)%n\n",
                links.size(), users.size()
            );
        } catch (IOException e) {
            System.out.println("Не удалось загрузить данные (файл повреждён или у него неверный формат)\n");
        }
    }

    // Поиск пользователя по префиксу UUID
    public String findUserUuidByPrefix(String prefix) {
        for (User u : users.values()) {
            if (u.getUuid().startsWith(prefix)) return u.getUuid();
        }
        return null;
    }

    // Получение короткой ссылки
    @Override
    public Optional<ShortLink> get(String code) {
        return Optional.ofNullable(links.get(code));
    }

    // Сохранение и обновление ссылки
    @Override
    public void put(ShortLink link) {
        links.put(link.getCode(), link);

        // Обновление данных пользователя
        users.compute(link.getOwnerUuid(), (uuid, user) -> {
            if (user == null) user = new User(uuid);
            user.addCode(link.getCode());
            return user;
        });

        save();
    }

    // Удаление короткой ссылки
    @Override
    public void remove(String code) {
        ShortLink removed = links.remove(code);

        if (removed != null) {
            User u = users.get(removed.getOwnerUuid());
            if (u != null) u.removeCode(code);
        }

        save();
    }

    // Возврат коллекции всех ссылок
    @Override
    public Collection<ShortLink> allLinks() {
        return links.values();
    }

    // Получение пользователя по UUID
    @Override
    public Optional<User> getUser(String uuid) {
        return Optional.ofNullable(users.get(uuid));
    }

    // Добавление или обновление пользователя
    @Override
    public void putUser(User user) {
        users.put(user.getUuid(), user);
        save();
    }

    // Сохранение данных в JSON файл
    @Override
    public synchronized void save() {
        Dump d = new Dump();
        d.links = new ArrayList<>(links.values());
        d.users = new ArrayList<>(users.values());

        try {
            mapper.writeValue(file, d);
        } catch (IOException e) {
            System.err.println("Не удалось сохранить данные в файл: " + e.getMessage() + "\n");
        }
    }
}
