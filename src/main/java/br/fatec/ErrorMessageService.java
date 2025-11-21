package br.fatec;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.TemplateLocator;
import io.quarkus.qute.Variant;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ErrorMessageService {

    private static final Logger LOG = Logger.getLogger(ErrorMessageService.class);

    private static class ErrorPattern {
        final Pattern pattern;
        final String friendly;
        ErrorPattern(Pattern pattern, String friendly) {
            this.pattern = pattern;
            this.friendly = friendly;
        }
    }

    private final List<ErrorPattern> patterns = new ArrayList<>();

    @Inject
    public ErrorMessageService() {
        loadPatterns();
    }

    private void loadPatterns() {
        try (InputStream in = getClass().getResourceAsStream("/templates/error-messages.txt")) {
            if (in == null) {
                LOG.warn("Arquivo de mensagens amigáveis não encontrado, seguindo apenas com mensagens padrão.");
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.lines()
                      .map(String::trim)
                      .filter(line -> !line.isBlank() && !line.startsWith("#"))
                      .forEach(line -> {
                          String[] parts = line.split("\\|", 3);
                          if (parts.length == 3) {
                              patterns.add(new ErrorPattern(Pattern.compile(parts[1], Pattern.CASE_INSENSITIVE), parts[2]));
                          }
                      });
            }
        } catch (IOException e) {
            LOG.error("Não foi possível carregar mensagens amigáveis.", e);
        }
    }

    public String humanize(String rawError) {
        if (rawError == null || rawError.isBlank()) return "Erro desconhecido.";
        String trimmed = rawError.trim();
        for (ErrorPattern pattern : patterns) {
            Matcher matcher = pattern.pattern.matcher(trimmed);
            if (matcher.find()) {
                String friendly = pattern.friendly;
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    friendly = friendly.replace("$" + i, Optional.ofNullable(matcher.group(i)).orElse(""));
                }
                return friendly;
            }
        }
        return trimmed;
    }
}

