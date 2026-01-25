package dev.toganbayev.urlshortener.util;

import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class UrlUtils {

    public boolean isValid(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();

            // Validate that scheme is http or https
            return scheme != null &&
                   (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
