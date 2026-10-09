/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package space.vkurse.core.notification;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.util.ClassUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Turns a content into what the channels send. A template {@code name} is two classpath files under
 * {@code templates/notification/}: {@code name.html}, which is put inside {@code layout.html}, and {@code name.txt}.
 * Values written with {@code th:text} are HTML-escaped.
 */
class TemplateRenderer {
    private static final String DIRECTORY = "templates/notification/";
    private static final String LAYOUT = "layout.html";

    private final SpringTemplateEngine engine = new SpringTemplateEngine();
    private final MessageSource messages;

    TemplateRenderer(MessageSource messages) {
        this.messages = messages;
        engine.addTemplateResolver(resolver(".html", TemplateMode.HTML));
        engine.addTemplateResolver(resolver(".txt", TemplateMode.TEXT));
        engine.setTemplateEngineMessageSource(messages);
    }

    Rendered render(Content content) {
        return switch (content) {
            case Content.Text text -> new Rendered(text.title(), text.body(), null);
            case Content.Template template -> render(template);
        };
    }

    private Rendered render(Content.Template template) {
        var name = template.name();
        var loader = ClassUtils.getDefaultClassLoader();
        if (loader == null || loader.getResource(DIRECTORY + name + ".html") == null) {
            throw new IllegalArgumentException("Unknown notification template " + name);
        }
        var locale = Locale.forLanguageTag(template.locale());
        var subject = messages.getMessage("notification." + name + ".subject", null, locale);

        var context = new Context(locale, template.params());
        var layout = new Context(locale);
        layout.setVariable("title", subject);
        layout.setVariable("body", engine.process(name + ".html", context));
        return new Rendered(subject, engine.process(name + ".txt", context).strip(), engine.process(LAYOUT, layout));
    }

    private static ClassLoaderTemplateResolver resolver(String extension, TemplateMode mode) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(DIRECTORY);
        resolver.setResolvablePatterns(Set.of("*" + extension));
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return resolver;
    }
}
