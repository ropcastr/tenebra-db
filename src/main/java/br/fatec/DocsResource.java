package br.fatec;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import org.commonmark.Extension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.ext.gfm.tables.TablesExtension;

@Path("/docs")
public class DocsResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response showDocumentation() {
        InputStream resource = getClass().getResourceAsStream("/DOCUMENTACAO.MD");
        if (resource == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("<h3>Documentação não encontrada.</h3>" +
                            "<p>O arquivo DOCUMENTACAO.MD deve estar em src/main/resources/</p>")
                    .build();
        }

        String markdown;
        try (InputStream in = resource) {
            markdown = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Response.serverError()
                    .entity("<h3>Falha ao carregar documentação.</h3>")
                    .build();
        }

        List<Extension> extensions = List.of(TablesExtension.create());
        Parser parser = Parser.builder().extensions(extensions).build();
        Node document = parser.parse(markdown);
        HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(false).build();
        String htmlContent = renderer.render(document);

        // Transform mermaid blocks - precisa capturar antes do escape
        Pattern mermaidPattern = Pattern.compile(
            "<pre><code class=\"language-mermaid\">([\\s\\S]*?)</code></pre>",
            Pattern.CASE_INSENSITIVE
        );
        htmlContent = mermaidPattern.matcher(htmlContent)
            .replaceAll("<pre class=\"mermaid\">$1</pre>");

        String pageTemplate = """
            <!DOCTYPE html>
            <html lang="pt-BR"><head><meta charset="UTF-8">
            <title>Documentação - Quarkus AI Database Assistant</title>
            <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.2.0/github-markdown.min.css">
            <style>
                body { background:#f6f8fa;display:flex;justify-content:center;padding:40px;font-family:Arial,sans-serif; }
                .markdown-body { background:#fff;border-radius:12px;padding:32px;max-width:950px;width:80%;box-shadow:0 0 16px #0002; }
                pre, code { background:#f6f8fa;border-radius:6px;padding:4px 6px; }
                table { border-collapse:collapse;width:100%;margin:16px 0; }
                th, td { border:1px solid #ddd;padding:8px 12px;text-align:left; }
                th { background:#f3f3f3;font-weight:bold; }
                tr:nth-child(even) { background:#fafafa; }
                pre.mermaid { background:#fff;padding:20px;border-radius:8px;margin:20px 0;text-align:center;border:1px solid #e1e4e8;min-height:100px; }
            </style>
            </head><body>
            <article class="markdown-body">CONTENT_PLACEHOLDER</article>
            <script type="module">
                import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
                mermaid.initialize({ startOnLoad: true, theme: 'default', securityLevel: 'loose', fontSize: 14 });
            </script>
            </body></html>
            """;

        String page = pageTemplate.replace("CONTENT_PLACEHOLDER", htmlContent);

        return Response.ok(page).build();
    }
}
