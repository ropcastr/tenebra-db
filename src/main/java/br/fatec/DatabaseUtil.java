package br.fatec;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DatabaseUtil {

    private final Path baseDir;

    public DatabaseUtil() {
        String dir = System.getProperty("tenebra.db.dir", System.getenv().getOrDefault("TENEBRA_DB_DIR", "data"));
        this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível preparar diretório de bancos: " + baseDir, e);
        }
    }

    private String sanitizeNomeBanco(String nomeBanco) {
        if (nomeBanco == null || nomeBanco.isBlank()) return null;
        String semExt = nomeBanco.endsWith(".db") ? nomeBanco.substring(0, nomeBanco.length() - 3) : nomeBanco;
        if (!semExt.matches("[A-Za-z0-9_-]{3,50}")) {
            return null;
        }
        return semExt + ".db";
    }

    private Path resolveBanco(String nomeBanco) {
        String sanitized = sanitizeNomeBanco(nomeBanco);
        if (sanitized == null) return null;
        Path target = baseDir.resolve(sanitized).normalize();
        if (!target.startsWith(baseDir)) {
            return null;
        }
        return target;
    }

    public Path getBancoPath(String nomeBanco) {
        Path bancoPath = resolveBanco(nomeBanco);
        if (bancoPath == null) {
            throw new IllegalArgumentException("Nome de banco inválido: " + nomeBanco);
        }
        return bancoPath;
    }

    public void garantirBancoFisico(String nomeBanco) {
        Path bancoPath = getBancoPath(nomeBanco);
        if (Files.exists(bancoPath)) return;
        try (Connection c = abrirConexao(nomeBanco)) {
            c.setAutoCommit(true);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível criar banco: " + bancoPath, e);
        }
    }

    public String listarBancosDisponiveis() {
        List<String> bancos = listarBancos();
        if (bancos.isEmpty()) return "Nenhum banco .db encontrado.";
        String lista = String.join(", ", bancos);
        return "Bancos disponíveis: " + lista;
    }

    public List<String> listarBancos() {
        File[] arquivos = baseDir.toFile().listFiles((dir, name) -> name.endsWith(".db"));
        if (arquivos == null || arquivos.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(arquivos)
                .map(File::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public String listarTabelas(String nomeBanco) {
        Path bancoPath = resolveBanco(nomeBanco);
        if (bancoPath == null) return "Nenhum banco selecionado.";

        try (Connection c = abrirConexao(nomeBanco);
             Statement st = c.createStatement()) {

            ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;");

            StringBuilder sb = new StringBuilder("<b>Tabelas:</b><br>");
            boolean achou = false;

            while (rs.next()) {
                achou = true;
                String name = rs.getString("name");
                long count = contarLinhas(c, name);
                String schema = obterSchemaTabela(c, name);
                sb.append("- ").append(escapeHtml(name))
                  .append(" (linhas: ").append(count).append(")")
                  .append("<br><small>").append(schema).append("</small><br>");
            }

            return achou ? sb.toString() : "Nenhuma tabela encontrada.";

        } catch (Exception e) {
            return "Erro ao listar tabelas: " + escapeHtml(e.getMessage());
        }
    }

    private long contarLinhas(Connection c, String tabela) {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM \"" + tabela + "\"")) {
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            return -1;
        }
    }

    private String obterSchemaTabela(Connection c, String tabela) {
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info('" + tabela + "')")) {
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder schema = new StringBuilder();
                while (rs.next()) {
                    schema.append(rs.getString("name"))
                          .append(" ")
                          .append(rs.getString("type"));
                    if (rs.getInt("pk") == 1) schema.append(" PRIMARY KEY");
                    if (rs.getInt("notnull") == 1) schema.append(" NOT NULL");
                    String dflt = rs.getString("dflt_value");
                    if (dflt != null) schema.append(" DEFAULT ").append(dflt);
                    schema.append(", ");
                }
                if (schema.length() >= 2) schema.setLength(schema.length() - 2);
                return escapeHtml(schema.toString());
            }
        } catch (SQLException e) {
            return "schema indisponível";
        }
    }

    public String apagarBanco(String nomeBanco, HistoryManager hm, String sessionId, boolean confirmado) {
        Path bancoPath = resolveBanco(nomeBanco);
        if (bancoPath == null) {
            return "Diga qual banco apagar, ex: 'Apague o banco Teste'.";
        }

        if (!Files.exists(bancoPath)) return "Banco " + escapeHtml(bancoPath.getFileName().toString()) + " não existe.";

        String nomeSemExt = bancoPath.getFileName().toString().replace(".db", "");
        if (!confirmado) {
            return "Confirme digitando: 'confirmar apagar " + escapeHtml(nomeSemExt) + "'.";
        }

        try {
            Files.delete(bancoPath);
            hm.removerBanco(sessionId);
            return "Banco " + escapeHtml(nomeSemExt) + " removido. Pode escolher outro banco para continuar.";
        } catch (Exception e) {
            return "Erro ao excluir banco: " + escapeHtml(e.getMessage());
        }
    }

    public Connection abrirConexao(String nomeBanco) throws SQLException {
        Path bancoPath = getBancoPath(nomeBanco);
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + bancoPath);
        habilitarForeignKeys(conn);
        return conn;
    }

    private void habilitarForeignKeys(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível habilitar FOREIGN KEYs", e);
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
