package org.example;

import java.util.Collection;
import java.util.Optional;

public interface StorageService {

    // Получение ссылки
    Optional<ShortLink> get(String code);

    // Создание или обновление ссылки
    void put(ShortLink link);

    // Удаление ссылки
    void remove(String code);

    // Возвращение коллекции всех ссылок
    Collection<ShortLink> allLinks();

    // Поиск пользователя по UUID
    Optional<User> getUser(String uuid);

    // Сохранение пользователя
    void putUser(User user);

    // Сохранение текущего состояние хранилища
    void save();
}
