package org.example;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class User {
    // Уникальный идентификатор пользователя
    private final String uuid;
    // Список коротких ссылок, которыми владеет пользователь
    private final List<String> codes;

    // Конструктор использует Jackson при чтении из JSON
    @JsonCreator
    public User(@JsonProperty("uuid") String uuid,
                @JsonProperty("codes") List<String> codes) {
        this.uuid = uuid;
        // Создание копии списка
        this.codes = (codes == null) ? new ArrayList<>() : new ArrayList<>(codes);
    }

    // Создания нового пользователя в приложении
    public User(String uuid) {
        this(uuid, new ArrayList<>());
    }

    public String getUuid() {
        return uuid;
    }

    // Возвращение неизменяемого списка ссылок
    public List<String> getCodes() {
        return Collections.unmodifiableList(codes);
    }

    // Добавление короткой ссылки пользователю
    public void addCode(String code) {
        codes.add(code);
    }

    // Удаление короткой ссылки
    public void removeCode(String code) {
        codes.remove(code);
    }
}
