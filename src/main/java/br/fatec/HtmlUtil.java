package br.fatec;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal HTML utilities for escaping and safely rendering AI output.
 * We allow: <br>, <table>/<thead>/<tbody>/<tr>/<th>/<td> only when
 * produced by the backend (SqlExecutor). For AI content, we escape
 * everything and re-render ```sql fenced blocks as <pre><code>.
 */
public final class HtmlUtil {

    private static final Pattern CODE_BLOCK = Pattern.compile("(?s)```sql\n?(.*?)```", Pattern.CASE_INSENSITIVE);

    private HtmlUtil() {}

    public static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Build safe HTML from an AI message string.
     * - Escapes all text
     * - Converts newlines to <br>
     * - Renders fenced ```sql blocks as <pre><code class="language-sql">...</code></pre>
     */
    public static String renderAiMessageSafely(String raw) {
        if (raw == null || raw.isBlank()) return "";

        StringBuilder out = new StringBuilder();
        int last = 0;
        Matcher m = CODE_BLOCK.matcher(raw);
        int blockIndex = 0;
        while (m.find()) {
            String before = raw.substring(last, m.start());
            if (!before.isEmpty()) {
                out.append(escape(before).replace("\n", "<br>"));
            }
            String code = m.group(1);
            String escapedCode = escape(code);
            String copyId = "sql-block-" + (++blockIndex);
            out.append("<div class=\"sql-block\" data-copy-target=\"").append(copyId).append("\">")
               .append("<button class=\"copy-sql\" type=\"button\" data-target=\"").append(copyId).append("\">Copiar SQL</button>")
               .append("<pre><code id=\"").append(copyId).append("\" class=\"language-sql\">")
               .append(escapedCode)
               .append("</code></pre></div>");
            last = m.end();
        }
        if (last < raw.length()) {
            out.append(escape(raw.substring(last)).replace("\n", "<br>"));
        }
        return out.toString();
    }
}
