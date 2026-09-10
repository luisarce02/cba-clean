package com.cbclean.report.presentation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Resolves the response locale (English / Spanish) from the client's
 * Accept-Language header. Defaults to English when the header is absent
 * or requests an unsupported language.
 */
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.of("es")));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
