package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {

    // Объект для хранения конфигурации
    private final Properties props = new Properties();

    public Config() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                System.out.println("config.properties не найден");
            }
        } catch (IOException e) {
            System.out.println("Не удалось загрузить 'config.properties'. Будут использоваться значения по умолчанию");
        }
    }

    // Длина генерируемых коротких ссылок. По умолчанию: 6
    public int shortcodeLength() {
        return Integer.parseInt(props.getProperty("shortlink.length", "6"));
    }

    // TTL. По умолчанию: 0 (нет лимита)
    public long defaultTtlSeconds() {
        return Long.parseLong(props.getProperty("default.ttl.seconds", "0"));
    }

    // Название файла, в котором хранятся данные приложения. По умолчанию: ./data.json (в корне проекта)
    public String dataFile() {
        return props.getProperty("data.file", "./data.json");
    }

    // Лимит кликов. По умолчанию: 0 (нет лимита)
    public long defaultMaxClicks() {
        return Long.parseLong(props.getProperty("default.max.clicks", "0"));
    }
}
