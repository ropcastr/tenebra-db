package br.fatec;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.QueryParam;

import java.nio.file.Files;
import java.nio.file.Path;

@jakarta.ws.rs.Path("/db")
public class DatabaseDownloadResource {

    @Inject
    DatabaseUtil databaseUtil;

    @GET
    @jakarta.ws.rs.Path("/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@QueryParam("db") String dbName) {
        if (dbName == null || dbName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Informe o parâmetro db com o nome do banco.")
                    .build();
        }
        try {
            Path arquivoBanco = databaseUtil.getBancoPath(dbName);
            if (!Files.exists(arquivoBanco)) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("Banco não encontrado: " + dbName)
                        .build();
            }
            return Response.ok(Files.newInputStream(arquivoBanco))
                    .header("Content-Disposition", "attachment; filename=\"" + arquivoBanco.getFileName() + "\"")
                    .build();
        } catch (IllegalArgumentException iae) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Nome de banco inválido.")
                    .build();
        } catch (Exception e) {
            return Response.serverError().entity("Erro ao preparar download: " + e.getMessage()).build();
        }
    }
}
