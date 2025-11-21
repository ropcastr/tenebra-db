package br.fatec;

import jakarta.enterprise.context.ApplicationScoped;

import java.text.Normalizer;
import java.util.regex.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.List;

/**
 * Detecta nomes de bancos de dados a partir de linguagem natural MUITO informal.
 * Projetado para entender usuários leigos que nunca viram SQL.
 */
@ApplicationScoped
public class DatabaseDetector {

    //Verbos/ações que indicam seleção de banco
    private static final List<String> ACOES_USAR = List.of(
            "usar", "use", "trocar", "troque", "mudar", "mude",
            "selecionar", "seleciona", "abrir", "abra", "conectar", "conecta",
            "colocar", "coloca", "puxar", "puxa", "ir", "vai", "vou",
            "trabalhar", "mexer", "acessar", "entrar", "escolher", "escolhe"
    );

    //Verbos que indicam criação de banco
    private static final List<String> ACOES_CRIAR = List.of(
            "criar", "cria", "crie", "fazer", "faz", "faça",
            "montar", "monte", "construir", "construa",
            "iniciar", "inicie", "comecar", "comece", "começar", "começe",
            "preparar", "prepare", "configurar", "configure"
    );

    // Sinônimos informais para "banco"
    private static final List<String> SINONIMOS_BANCO = List.of(
            "banco", "database", "bd", "db", "base",
            "negocio", "negócio", "parada", "coisa", "bagulho",
            "sistema", "arquivo", "repositorio", "repositório",
            "lugar", "pasta", "area", "área"
    );

    //Palavras que NUNCA podem ser nomes de banco
    private static final String[] PALAVRAS_INVALIDAS = {
        // Conectivos e artigos
        "de", "da", "do", "das", "dos", "para", "pra", "pro", "com", "no", "na",
        "o", "a", "os", "as", "um", "uma", "uns", "umas", "em", "ao", "aos",

        //Sinônimos de banco (não podem ser o nome EM SI)
        "banco", "database", "bd", "db", "base", "dados", "negocio", "negócio",
        "parada", "coisa", "bagulho", "sistema", "arquivo", "lugar", "pasta",

        //Palavras SQL/técnicas
        "sql", "sqlite", "select", "insert", "update", "delete", "create",
        "drop", "alter", "table", "tabela", "coluna", "column", "from", "where",

        //Qualificadores genéricos
        "chamado", "chamada", "tipo", "estilo", "exemplo", "teste", "temp",

        //Palavras comuns que podem aparecer
        "quero", "preciso", "fazer", "criar", "usar", "novo", "nova",
        "meu", "minha", "esse", "essa", "este", "esta", "email", "telefone", "nome"
    };

    /**
     * Padrões regex organizados por prioridade (mais específico → mais genérico)
     */
    private final List<Pattern> padroes = List.of(
        //1. "criar/fazer banco CHAMADO nome" ou "criar/fazer banco DE nome"
        Pattern.compile(
            "(?:" + String.join("|", ACOES_CRIAR) + ")\\s+(?:" +
            String.join("|", SINONIMOS_BANCO) + ")\\s+(?:chamado|chamada|de|pra|para)\\s+['\"]?([a-zA-Z0-9_\\-]{2,50})['\"]?",
            Pattern.CASE_INSENSITIVE
        ),

        //2. "usar/abrir banco nome" (sem conectivo)
        Pattern.compile(
            "(?:" + String.join("|", ACOES_USAR) + ")\\s+(?:o\\s+)?(?:" +
            String.join("|", SINONIMOS_BANCO) + ")\\s+['\"]?([a-zA-Z0-9_\\-]{2,50})['\"]?",
            Pattern.CASE_INSENSITIVE
        ),

        //3. "banco chamado nome" ou "banco de nome"
        Pattern.compile(
            "(?:" + String.join("|", SINONIMOS_BANCO) + ")\\s+(?:chamado|chamada|de|pra|para)\\s+['\"]?([a-zA-Z0-9_\\-]{2,50})['\"]?",
            Pattern.CASE_INSENSITIVE
        ),

        //4. "criar/fazer um NOME" (quando nome está logo após ação + artigo)
        Pattern.compile(
            "(?:" + String.join("|", ACOES_CRIAR) + ")\\s+(?:um|uma)\\s+['\"]?([a-zA-Z0-9_\\-]{2,50})['\"]?",
            Pattern.CASE_INSENSITIVE
        ),

        //5. "vamos usar NOME" (pattern mais solto)
        Pattern.compile(
            "(?:vamos|vou)\\s+(?:" + String.join("|", ACOES_USAR) + ")\\s+(?:o\\s+)?(?:" +
            String.join("|", SINONIMOS_BANCO) + "\\s+)?['\"]?([a-zA-Z0-9_\\-]{2,50})['\"]?",
            Pattern.CASE_INSENSITIVE
        )
    );

    public String detectaNomeBanco(String mensagem, String bancoAtual) {
        if (mensagem == null || mensagem.isBlank()) {
            return bancoAtual;
        }

        //Normaliza: remove acentos, lowercase
        String normalizado = normalizar(mensagem);

        //Tenta cada padrão em ordem de prioridade
        for (Pattern padrao : padroes) {
            Matcher matcher = padrao.matcher(normalizado);

            while (matcher.find()) {
                String candidato = matcher.group(1).trim();

                // Valida o candidato
                if (validarNome(candidato)) {
                    return candidato + ".db";
                }
            }
        }

        //Se não encontrou nada, mantém o banco atual
        return bancoAtual;
    }

    /**
     * Normaliza texto: remove acentos, converte para lowercase
     */
    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Valida se um nome de banco é aceitável
     */
    private boolean validarNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return false;
        }

        //Deve ter pelo menos 2 caracteres
        if (nome.length() < 2) {
            return false;
        }

        //Não pode ter caracteres perigosos
        if (nome.contains(".") || nome.contains("/") || nome.contains("\\") ||
            nome.contains("..") || nome.contains("~")) {
            return false;
        }

        //Não pode estar na lista de palavras inválidas
        String nomeNormalizado = normalizar(nome);
        for (String invalida : PALAVRAS_INVALIDAS) {
            if (nomeNormalizado.equals(invalida)) {
                return false;
            }
        }

        //Passou em todas as validações
        return true;
    }
}

