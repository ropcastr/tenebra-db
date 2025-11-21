package br.fatec;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.ws.rs.BadRequestException;
import java.util.*;

@Path("/chat")
public class ChatResource {

    @ConfigProperty(name = "quarkus.langchain4j.ollama.chat-model.model-id")
    String modelId;

    @Inject
    AiModeService aiModeService;

    @Inject
    TenebraAIService tenebraAiService;

    @Inject
    HistoryManager historyManager;

    @Inject
    DatabaseDetector databaseDetector;

    @Inject
    DatabaseUtil databaseUtil;

    @Inject
    SqlExecutor sqlExecutor;

    @Inject
    NaturalLanguageDictionary dictionary;

    private static final Logger LOG = Logger.getLogger(ChatResource.class);

    public static class ChatRequest {
        public String message;
        public String sessionId;
    }

    public static class ChatResponse {
        public String response;
        public ChatResponse(String r) { this.response = r; }
    }

    public static class SessionInfo {
        public String sessionId;
        public String bancoAtual;
        public int historico;
        public boolean destrutivoConfirmado;
    }

    public static class BancoRequest {
        public String sessionId;
        public String banco;
    }

    public static class ModeToggle {
        public boolean structured;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ChatResponse chat(ChatRequest request) {

        String sessionId = request.sessionId != null ? request.sessionId : "default";
        boolean structuredMode = aiModeService.isStructuredEnabled();

        //Recupera histórico
        List<String> history = historyManager.getHistory(sessionId);

        //Saudação inicial
        if (history.isEmpty()) {
            historyManager.addMessage(sessionId, "Olá! Sou Tenebra, sua assistente para bancos SQLite. Digite 'ajuda' se quiser algumas dicas! 🙂");
        }

        //Adiciona entrada do usuário
        if (request.message == null || request.message.isBlank()) {
            return new ChatResponse("Preciso de uma mensagem para ajudar. Tente algo como 'crie tabela clientes'.");
        }
        historyManager.addMessage(sessionId, request.message);

        //Detecta banco
        String bancoAtual = historyManager.getBancoAtual(sessionId);
        String nomeBanco = databaseDetector.detectaNomeBanco(request.message, bancoAtual);
        if (nomeBanco != null) {
            historyManager.setBanco(sessionId, nomeBanco);
        }
        String bancoEmUso = historyManager.getBancoAtual(sessionId);

        String lower = request.message.toLowerCase(Locale.ROOT).trim();
        String resposta;

        if (dictionary.matches(lower, NaturalLanguageDictionary.Intent.HELP)) {
            resposta = """
                <b>Dicas de uso:</b><br>
                • Criar banco: <code>Crie um banco chamado Vendas</code><br>
                • Criar tabela: <code>Crie tabela Clientes</code><br>
                • Inserir dados: <code>Adicione Nome: Ana</code><br>
                • Listar: <code>Liste tabelas</code><br>
                • Buscar: <code>Procure por João</code><br>
                • Baixar banco: <code>Me deixe baixar o banco atual</code><br>
                • Fale naturalmente: "bota isso", "remove aquele", "mostra tudo".<br>
                """;

        } else if (dictionary.matches(lower, NaturalLanguageDictionary.Intent.LIST_DATABASES)) {
            resposta = HtmlUtil.escape(databaseUtil.listarBancosDisponiveis());

        } else if (dictionary.matches(lower, NaturalLanguageDictionary.Intent.DOWNLOAD_DATABASE)) {
            if (bancoEmUso == null) {
                resposta = "Escolha um banco primeiro (ex.: 'usar o banco vendas') para poder baixar.";
            } else {
                resposta = "Use <a href=\"/db/download?db=" + HtmlUtil.escape(bancoEmUso) + "\" target=\"_blank\">/db/download?db=" + HtmlUtil.escape(bancoEmUso) + "</a> para baixar o arquivo.";
            }

        } else if (dictionary.matches(lower, NaturalLanguageDictionary.Intent.CONFIRM_SQL_DELETE)) {
            historyManager.marcarDestrutivoConfirmado(sessionId);
            resposta = "Operações destrutivas liberadas para a próxima instrução. Faça sua pergunta agora.";

        } else if (dictionary.matches(lower, NaturalLanguageDictionary.Intent.SHOW_CURRENT_DB)) {
            resposta = bancoEmUso != null ? ("Estamos usando o banco: " + HtmlUtil.escape(bancoEmUso)) : "Nenhum banco selecionado ainda.";

        } else if (dictionary.matches(lower, NaturalLanguageDictionary.Intent.DELETE_DATABASE)) {
            boolean confirmado = dictionary.matches(lower, NaturalLanguageDictionary.Intent.CONFIRM_DATABASE_DELETE);
            if (!confirmado) {
                historyManager.registrarBancoParaApagar(sessionId, bancoEmUso);
            }
            String pendente = historyManager.getBancoPendenteConfirmacao(sessionId);
            boolean autorizouMesmoBanco = confirmado && pendente != null && (bancoEmUso != null && bancoEmUso.equals(pendente));
            resposta = HtmlUtil.escape(databaseUtil.apagarBanco(bancoEmUso, historyManager, sessionId, autorizouMesmoBanco));
            if (autorizouMesmoBanco) {
                historyManager.consumirBancoPendente(sessionId);
            }

        } else if (dictionary.matches(lower, NaturalLanguageDictionary.Intent.CONFIRM_DATABASE_DELETE)) {
            String pendente = historyManager.getBancoPendenteConfirmacao(sessionId);
            if (pendente == null) {
                resposta = "Nenhum pedido de exclusão pendente. Diga 'apague o banco NOME' primeiro.";
            } else {
                resposta = HtmlUtil.escape(databaseUtil.apagarBanco(pendente, historyManager, sessionId, true));
                historyManager.consumirBancoPendente(sessionId);
            }

        } else if (dictionary.matches(lower, NaturalLanguageDictionary.Intent.LIST_TABLES)) {
            resposta = HtmlUtil.escape(databaseUtil.listarTabelas(bancoEmUso));

        } else {

            if (historyManager.getBancoAtual(sessionId) == null) {
                return new ChatResponse("Antes disso, qual nome do banco você deseja usar? Ex.: 'usar o banco vendas'.");
            }

            String prompt = String.join("\n", history);
            if (structuredMode) {
                TenebraStructuredResponse structured = null;
                try {
                    structured = tenebraAiService.structured(prompt);
                } catch (Exception ex) {
                    LOG.errorf(ex, "Falha ao obter resposta estruturada (sessão %s). Voltando ao modo tradicional.", sessionId);
                }
                if (structured == null) {
                    structuredMode = false;
                    LOG.warnf("Modo estruturado indisponível, usando fallback. Sessão %s", sessionId);
                } else {
                    String explicacao = structured.explicacao != null ? structured.explicacao.trim() : "Resposta vazia.";
                    String sqlConteudo = structured.sql != null ? structured.sql.trim() : "";
                    if (sqlConteudo.isEmpty()) {
                        LOG.debugf("Structured response sem SQL (sessão %s). Conteúdo: %s", sessionId, explicacao);
                    }
                    historyManager.addStructuredMessage(sessionId, explicacao, sqlConteudo);
                    StringBuilder safe = new StringBuilder(HtmlUtil.escape(explicacao).replace("\n", "<br>"));
                    String sqlResult = "";
                    if (!sqlConteudo.isEmpty()) {
                        sqlResult = sqlExecutor.executarComandosSQL("```sql\n" + sqlConteudo + "\n```", historyManager.getBancoAtual(sessionId), sessionId);
                        safe.append("<br><br><pre><code class=\"language-sql\">")
                            .append(HtmlUtil.escape(sqlConteudo))
                            .append("</code></pre>");
                    }

                    if (!sqlResult.isEmpty()) {
                        safe.append("<br><br>[Banco: ")
                            .append(HtmlUtil.escape(historyManager.getBancoAtual(sessionId)))
                            .append("]<br>")
                            .append(sqlResult);
                    }
                    return new ChatResponse(safe.toString());
                }
            }

            String rawAi = tenebraAiService.input(prompt);
            if (rawAi == null) rawAi = "";
            rawAi = rawAi.replaceAll("<think>[\\s\\S]*?</think>", "").trim();

            historyManager.addMessage(sessionId, rawAi);

            String sqlResult = sqlExecutor.executarComandosSQL(rawAi, historyManager.getBancoAtual(sessionId), sessionId);
            String safeMessage = HtmlUtil.renderAiMessageSafely(rawAi);
            if (!sqlResult.isEmpty()) {
                safeMessage += "<br><br>[Banco: " + HtmlUtil.escape(historyManager.getBancoAtual(sessionId)) + "]<br>" + sqlResult;
            }
            return new ChatResponse(safeMessage);
        }

        return new ChatResponse(resposta);
    }

    private SessionInfo buildSessionInfo(String sessionId) {
        SessionInfo info = new SessionInfo();
        info.sessionId = sessionId;
        if (sessionId != null && !sessionId.isBlank()) {
            info.bancoAtual = historyManager.getBancoAtual(sessionId);
            info.historico = historyManager.getHistory(sessionId).size();
            info.destrutivoConfirmado = historyManager.isDestrutivoConfirmado(sessionId);
        }
        return info;
    }

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String info() {
        return "Use POST /chat com JSON.";
    }

    @GET
    @Path("/welcome")
    @Produces(MediaType.APPLICATION_JSON)
    public ChatResponse welcome() {
        return new ChatResponse("IA: Olá! Sou Tenebra, sua assistente para bancos SQLite. Digite 'ajuda' se quiser algumas dicas! 🙂");
    }

    @GET
    @Path("/model")
    @Produces(MediaType.TEXT_PLAIN)
    public String getModel() {
        return aiModeService.isStructuredEnabled() ? modelId + " (structured)" : modelId;
    }

    @GET
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON)
    public SessionInfo sessionInfo(@QueryParam("sessionId") String sessionId) {
        SessionInfo info = buildSessionInfo(sessionId);
        if (info != null) {
            info.destrutivoConfirmado = historyManager.isDestrutivoConfirmado(sessionId);
        }
        return info;
    }

    @GET
    @Path("/mode")
    @Produces(MediaType.APPLICATION_JSON)
    public ModeToggle getMode() {
        ModeToggle toggle = new ModeToggle();
        toggle.structured = aiModeService.isStructuredEnabled();
        return toggle;
    }

    @POST
    @Path("/mode")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public ModeToggle setMode(ModeToggle request) {
        if (request == null) {
            throw new BadRequestException("Corpo inválido.");
        }
        boolean novoValor = aiModeService.setStructuredEnabled(request.structured);
        LOG.infof("Modo estruturado %s", novoValor ? "ativado" : "desativado");
        ModeToggle response = new ModeToggle();
        response.structured = novoValor;
        return response;
    }

    @POST
    @Path("/session/banco")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public SessionInfo atualizarBanco(BancoRequest request) {
        if (request == null || request.sessionId == null || request.sessionId.isBlank()) {
            throw new BadRequestException("sessionId é obrigatório.");
        }
        if (request.banco == null || request.banco.isBlank()) {
            historyManager.removerBanco(request.sessionId);
            return buildSessionInfo(request.sessionId);
        }
        try {
            databaseUtil.getBancoPath(request.banco);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Nome de banco inválido.");
        }
        historyManager.setBanco(request.sessionId, request.banco);
        return buildSessionInfo(request.sessionId);
    }
}
