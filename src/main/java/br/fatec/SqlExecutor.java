package br.fatec;

import java.sql.*;
import java.util.regex.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SqlExecutor {

    private static final Pattern SQL_BLOCK = Pattern.compile("(?s)```sql\\s*(.*?)```",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERIC_BLOCK = Pattern.compile("(?s)```\\s*(.*?)```",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DESTRUCTIVE = Pattern.compile("(?i)\\b(drop|truncate|alter\\s+table\\s+drop|delete\\s+from)\\b");
    private static final Pattern FORBIDDEN = Pattern.compile("(?i)\\b(attach|detach|pragma)\\b");

    private final DatabaseUtil db;
    private final HistoryManager historyManager;
    private final ErrorMessageService errorMessageService;

    @Inject
    public SqlExecutor(DatabaseUtil db, HistoryManager historyManager, ErrorMessageService errorMessageService) {
        this.db = db;
        this.historyManager = historyManager;
        this.errorMessageService = errorMessageService;
    }

    public String executarComandosSQL(String texto, String nomeBanco, String sessionId) {
        if (sessionId != null && texto.toLowerCase().contains("confirmar delete")) {
            historyManager.marcarDestrutivoConfirmado(sessionId);
        }

        if (nomeBanco == null) return "";

        db.garantirBancoFisico(nomeBanco);

        StringBuilder saida = new StringBuilder();

        List<String> blocosSql = extrairBlocosSql(texto);
        for (String bloco : blocosSql) {
            String sql = normalizarCreate(bloco);
            saida.append(executaSQL(sql, nomeBanco, sessionId));
        }

        String lower = texto.toLowerCase(Locale.ROOT).trim();

        if (blocosSql.isEmpty() && iniciaComSql(lower)) {
            String sql = normalizarCreate(texto.trim());
            saida.append(executaSQL(sql, nomeBanco, sessionId));
        }

        if (saida.isEmpty()) {
            saida.append("Nenhum SQL encontrado.");
        }

        return saida.toString();
    }

    private boolean iniciaComSql(String lower) {
        return lower.startsWith("create table") ||
               lower.startsWith("insert") ||
               lower.startsWith("select") ||
               lower.startsWith("update") ||
               lower.startsWith("delete") ||
               lower.startsWith("drop");
    }

    private List<String> extrairBlocosSql(String texto) {
        List<String> blocos = new ArrayList<>();
        Matcher mSql = SQL_BLOCK.matcher(texto);
        while (mSql.find()) {
            String bloco = mSql.group(1).trim();
            if (!bloco.isEmpty()) {
                blocos.add(bloco);
            }
        }
        if (!blocos.isEmpty()) {
            return blocos;
        }
        Matcher generico = GENERIC_BLOCK.matcher(texto);
        while (generico.find()) {
            String bloco = generico.group(1).trim();
            if (pareceSql(bloco)) {
                blocos.add(bloco);
            }
        }
        return blocos;
    }

    private boolean pareceSql(String bloco) {
        if (bloco.isEmpty()) return false;
        String lower = bloco.toLowerCase(Locale.ROOT).trim();
        if (iniciaComSql(lower)) return true;
        return lower.contains(" select ") || lower.contains(" insert ") || lower.contains(" update ") || lower.contains(" delete ") || lower.contains(" create table ");
    }

    private String normalizarCreate(String sql) {
        if (sql.toLowerCase().startsWith("create table") &&
            !sql.toLowerCase().contains("if not exists")) {

            return sql.replaceFirst("(?i)create table", "CREATE TABLE IF NOT EXISTS");
        }
        return sql;
    }

    private String executaSQL(String sql, String nomeBanco, String sessionId) {

        if (FORBIDDEN.matcher(sql).find()) {
            return "Operação bloqueada: comandos ATTACH/DETACH/PRAGMA não são permitidos.";
        }

        boolean precisaConfirmar = isDestructive(sql);
        if (precisaConfirmar && (sessionId == null || !historyManager.isDestrutivoConfirmado(sessionId))) {
            return "Operação bloqueada: confirme digitando 'confirmar delete' antes de excluir ou alterar tabelas.";
        }

        StringBuilder out = new StringBuilder();

        try (Connection c = db.abrirConexao(nomeBanco)) {

            c.setAutoCommit(false);
            sql = limparComandosInvalidos(sql);
            List<String> statements = dividirStatements(sql);

            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (trimmed.isEmpty()) continue;

                try (Statement st = c.createStatement()) {
                    try { st.setQueryTimeout(30); } catch (Throwable ignored) {}
                    if (trimmed.toLowerCase().startsWith("select")) {
                        String safeSelect = aplicarLimitSeNecessario(trimmed);
                        out.append(executarSelect(safeSelect, st));
                    } else {
                        int count = st.executeUpdate(trimmed);
                        out.append("Comando executado: ")
                           .append(escapeHtml(trimmed))
                           .append("<br>Linhas afetadas: ")
                           .append(count)
                           .append("<br>");
                    }
                }
            }

            c.commit();
            if (precisaConfirmar && sessionId != null) {
                historyManager.consumirConfirmacaoDestrutiva(sessionId);
            }

        } catch (Exception e) {
            return "[Erro SQL] " + escapeHtml(errorMessageService.humanize(e.getMessage()));
        }

        return out.toString();
    }

    private List<String> dividirStatements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (char ch : sql.toCharArray()) {
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
            }

            if (ch == ';' && !inSingle && !inDouble) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result;
    }

    private boolean isDestructive(String sql) {
        return DESTRUCTIVE.matcher(sql).find();
    }

    private String limparComandosInvalidos(String sql) {
        StringBuilder sb = new StringBuilder();
        for (String line : sql.split("\n")) {
            String l = line.trim().toLowerCase();
            if (l.startsWith("create database") || l.startsWith("use ")) continue;
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private String aplicarLimitSeNecessario(String sql) {
        String lower = sql.toLowerCase();
        // Não reescreve se já houver LIMIT
        if (lower.contains(" limit ")) return sql;
        // Evita impactar selects de contagem exata, mas ainda assim protege
        return sql + " LIMIT 1000";
    }

    private String executarSelect(String sql, Statement st) throws Exception {

        try (ResultSet rs = st.executeQuery(sql)) {
            StringBuilder html = new StringBuilder("<table border='1'><thead><tr>");

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            for (int i = 1; i <= colCount; i++) {
                html.append("<th>").append(escapeHtml(meta.getColumnName(i))).append("</th>");
            }
            html.append("</tr></thead><tbody>");

            while (rs.next()) {
                html.append("<tr>");
                for (int i = 1; i <= colCount; i++) {
                    String val = rs.getString(i);
                    html.append("<td>").append(escapeHtml(val)).append("</td>");
                }
                html.append("</tr>");
            }

            html.append("</tbody></table>");
            return html.toString();
        }
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}
