package br.fatec;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class HistoryManager {

    private final Map<String, List<String>> historyMap = new ConcurrentHashMap<>();
    private final Map<String, String> bancoMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> destrutivoConfirmadoMap = new ConcurrentHashMap<>();
    private final Map<String, String> bancoPendendoConfirmacaoMap = new ConcurrentHashMap<>();
    private final Map<String, List<StructuredMessage>> structuredHistoryMap = new ConcurrentHashMap<>();
    private final int maxHistory;

    public HistoryManager(@ConfigProperty(name = "tenebra.history.max-size", defaultValue = "30") int maxHistory) {
        this.maxHistory = Math.max(5, maxHistory);
    }

    public List<String> getHistory(String sessionId) {
        historyMap.computeIfAbsent(sessionId, id -> Collections.synchronizedList(new ArrayList<>()));
        return historyMap.get(sessionId);
    }

    public void addMessage(String sessionId, String msg) {
        List<String> h = getHistory(sessionId);
        synchronized (h) {
            h.add(msg);
            while (h.size() > maxHistory) h.remove(0);
        }
    }

    public void setBanco(String sessionId, String banco) {
        bancoMap.put(sessionId, banco);
    }

    public String getBancoAtual(String sessionId) {
        return bancoMap.get(sessionId);
    }

    public void removerBanco(String sessionId) {
        bancoMap.remove(sessionId);
    }

    public boolean isDestrutivoConfirmado(String sessionId) {
        return destrutivoConfirmadoMap.getOrDefault(sessionId, false);
    }

    public void marcarDestrutivoConfirmado(String sessionId) {
        destrutivoConfirmadoMap.put(sessionId, true);
    }

    public void consumirConfirmacaoDestrutiva(String sessionId) {
        destrutivoConfirmadoMap.remove(sessionId);
    }

    public void registrarBancoParaApagar(String sessionId, String nomeBanco) {
        if (nomeBanco == null) return;
        bancoPendendoConfirmacaoMap.put(sessionId, nomeBanco);
    }

    public String getBancoPendenteConfirmacao(String sessionId) {
        return bancoPendendoConfirmacaoMap.get(sessionId);
    }

    public void consumirBancoPendente(String sessionId) {
        bancoPendendoConfirmacaoMap.remove(sessionId);
    }

    public void addStructuredMessage(String sessionId, String explicacao, String sql) {
        if (explicacao != null && !explicacao.isBlank()) {
            addMessage(sessionId, explicacao);
        }
        if (sql != null && !sql.isBlank()) {
            addMessage(sessionId, "```sql\n" + sql.trim() + "\n```");
        }
        structuredHistoryMap.computeIfAbsent(sessionId, id -> Collections.synchronizedList(new ArrayList<>()))
                .add(new StructuredMessage(explicacao, sql));
    }

    public List<StructuredMessage> getStructuredHistory(String sessionId) {
        return structuredHistoryMap.getOrDefault(sessionId, Collections.emptyList());
    }

    public static class StructuredMessage {
        public final String explicacao;
        public final String sql;

        public StructuredMessage(String explicacao, String sql) {
            this.explicacao = explicacao;
            this.sql = sql;
        }
    }
}
