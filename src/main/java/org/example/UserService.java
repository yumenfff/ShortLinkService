package org.example;

import java.util.UUID;

public class UserService {

    private final StorageService storage;
    private String currentUserId;

    public UserService(StorageService storage) {
        this.storage = storage;
    }

    // Установка текущего пользователя
    public String ensureUser(String uuid) {

        // Если UUID не передан, то создаётся новый пользователь
        if (uuid == null || uuid.isBlank()) {
            String newUuid = UUID.randomUUID().toString();
            storage.putUser(new User(newUuid));
            this.currentUserId = newUuid;
            return newUuid;
        }

        // Если пользователь существует, то переключаемся
        if (storage.getUser(uuid).isPresent()) {
            this.currentUserId = uuid;
            return uuid;
        }

        // Если пользователя не существует, то создаётся новый пользователь с заданным UUID
        storage.putUser(new User(uuid));
        this.currentUserId = uuid;
        return uuid;
    }

    // Возвращение UUID текущего пользователя
    public String getCurrentUser() {
        return this.currentUserId;
    }
}
