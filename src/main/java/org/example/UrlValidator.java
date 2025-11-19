package org.example;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlValidator {
    // Проверка, что URL корректен и имеет схему http/https
    public static boolean isValid(String url) {
        // URL отсутствует или пуст
        if (url == null || url.isBlank()) return false;

        try {
            URI uri = new URI(url);

            // Допускается только http или https
            String scheme = uri.getScheme();
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
