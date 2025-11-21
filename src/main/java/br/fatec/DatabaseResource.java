package br.fatec;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import java.sql.*;
import java.util.*;

@Path("/db")
public class DatabaseResource {

    @Inject
    DatabaseUtil databaseUtil;

    @GET
    @Path("/query")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, String>> queryDb(@QueryParam("db") String dbName, @QueryParam("sql") String sql) {
        if (dbName == null || dbName.isBlank() || sql == null || sql.isBlank()) {
            throw new BadRequestException("Informe db e sql");
        }

        String sanitizedSql = sql.trim();
        if (!sanitizedSql.toLowerCase().startsWith("select")) {
            throw new BadRequestException("Apenas SELECT é permitido.");
        }
        if (sanitizedSql.contains(";")) {
            throw new BadRequestException("Envie apenas uma consulta por vez.");
        }

        try (Connection conn = databaseUtil.abrirConexao(dbName);
             PreparedStatement stmt = conn.prepareStatement(sanitizedSql);
             ResultSet rs = stmt.executeQuery()) {

            ResultSetMetaData meta = rs.getMetaData();
            int cols = meta.getColumnCount();
            List<Map<String, String>> results = new ArrayList<>();

            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 1; i <= cols; i++) {
                    String value = rs.getString(i);
                    row.put(meta.getColumnName(i), value);
                }
                results.add(row);
            }
            return results;
        } catch (Exception e) {
            throw new BadRequestException("Erro ao executar consulta: " + e.getMessage(), e);
        }
    }

    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Map<String, String>> listarBancos() {
        List<String> nomes = databaseUtil.listarBancos();
        List<Map<String, String>> payload = new ArrayList<>();
        for (String nome : nomes) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", nome);
            payload.add(item);
        }
        return payload;
    }

}