package br.fatec;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class NaturalLanguageDictionary {

    public enum Intent {
        HELP,
        LIST_DATABASES,
        LIST_TABLES,
        SHOW_CURRENT_DB,
        CONFIRM_SQL_DELETE,
        DELETE_DATABASE,
        CONFIRM_DATABASE_DELETE,
        DOWNLOAD_DATABASE
    }

    private final Map<Intent, Set<String>> keywords = new EnumMap<>(Intent.class);

    @ConfigProperty(name = "tenebra.synonyms.file", defaultValue = "data/synonyms-extra.txt")
    String synonymsFile;

    @PostConstruct
    void init() {
        registerDefaults();
        loadCustomSynonyms();
    }

    public boolean matches(String message, Intent intent) {
        if (message == null || intent == null) return false;
        String normalized = normalize(message);
        return keywords.getOrDefault(intent, Set.of()).stream()
                .anyMatch(normalized::contains);
    }

    private void registerDefaults() {
        keywords.put(Intent.HELP, setOf("ajuda", "me ajuda", "dica", "dicas", "socorro"));

        keywords.put(Intent.LIST_DATABASES, setOf(
                "liste bancos", "mostre bancos", "mostrar bancos", "quais bancos",
                "listar bancos", "procura bancos", "ver bancos"));

        keywords.put(Intent.LIST_TABLES, setOf(
                "liste tabelas", "mostre tabelas", "listar tabelas", "quais tabelas",
                "mostrar tabelas", "procura tabelas", "ver tabelas", "lista de tabelas"));

        keywords.put(Intent.SHOW_CURRENT_DB, setOf(
                "banco atual", "qual banco", "qual o banco", "que banco"));

        keywords.put(Intent.CONFIRM_SQL_DELETE, setOf(
                "confirmar delete", "confirmar delecao", "pode deletar", "pode apagar",
                "permitir delete", "autorizar delete", "liberar delete"));

        keywords.put(Intent.DELETE_DATABASE, setOf(
                "apague o banco", "apagar banco", "delete o banco", "deleta o banco",
                "remova o banco", "remover banco", "tirar o banco", "excluir banco"));

        keywords.put(Intent.CONFIRM_DATABASE_DELETE, setOf(
                "confirmar apagar", "confirmar exclusao", "confirmar remocao",
                "confirmar delete do banco", "confirmar deletar banco"));

        keywords.put(Intent.DOWNLOAD_DATABASE, setOf(
                "baixar banco", "download banco", "download do banco",
                "me deixe baixar o banco", "quero baixar o banco",
                "baixar arquivo do banco", "exportar banco"));
    }

    private void loadCustomSynonyms() {
        // Try file system
        try {
            Path path = Paths.get(synonymsFile);
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                applySynonymsLines(lines);
                return;
            }
        } catch (IOException ignored) { }
        // Fallback: try classpath resource
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(synonymsFile)) {
            if (in != null) {
                List<String> lines = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n"));
                applySynonymsLines(lines);
                return;
            }
        } catch (IOException ignored) { }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("synonyms-extra.txt")) {
            if (in != null) {
                List<String> lines = Arrays.asList(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\r?\n"));
                applySynonymsLines(lines);
            }
        } catch (IOException ignored) { }
    }

    private void applySynonymsLines(List<String> lines) {
        for (String line : lines) {
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            String[] parts = line.split(":", 2);
            if (parts.length != 2) continue;
            Intent intent = parseIntent(parts[0].trim());
            if (intent == null) continue;
            Set<String> entries = Arrays.stream(parts[1].split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(this::normalize)
                    .collect(Collectors.toSet());
            keywords.computeIfAbsent(intent, key -> new HashSet<>()).addAll(entries);
        }
    }

    private Intent parseIntent(String raw) {
        try {
            return Intent.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String normalize(String text) {
        String lower = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        // Strip punctuation to improve matching with natural language
        lower = lower.replaceAll("[\\p{Punct}]", " ");
        return lower.replaceAll("\\s+", " ").trim();
    }

    private Set<String> setOf(String... values) {
        return Arrays.stream(values)
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
