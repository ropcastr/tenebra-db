package br.fatec;

public class TestDatabaseDetector {
    public static void main(String[] args) {
        DatabaseDetector detector = new DatabaseDetector();

        String[] testes = {
            // Casos formais (já funcionavam)
            "Vamos usar o banco vendas",
            "usar o banco vendas",
            "banco vendas",
            "banco chamado vendas",
            "usar banco vendas",
            "use o banco clientes",
            "trocar para o banco produtos",

            // NOVOS: Casos informais (linguagem natural)
            "quero fazer um negócio de clientes",
            "preciso de um banco pra loja",
            "criar uma parada de estoque",
            "vou mexer no banco compras",
            "fazer um sistema de vendas",
            "abrir aquele banco de produtos",
            "quero trabalhar com vendas",

            // Casos com sinônimos
            "vou usar aquela base de dados de clientes",
            "trocar pra bd vendas",
            "fazer um repositório de produtos",

            // Casos que devem ser ignorados
            "usar o banco banco",  // "banco" é inválida
            "criar um banco de dados",  // "dados" é inválida
            "fazer uma tabela",  // não menciona banco
            "select * from clientes"  // SQL puro
        };

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║      TESTE DE DETECÇÃO DE NOMES EM LINGUAGEM NATURAL         ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        int sucessos = 0;
        int total = 0;

        for (String teste : testes) {
            String resultado = detector.detectaNomeBanco(teste, null);
            boolean detectou = resultado != null && !resultado.equals("null");

            System.out.println("📝 Entrada: \"" + teste + "\"");
            System.out.println("   " + (detectou ? "✅" : "❌") + " Resultado: " + (resultado != null ? resultado : "(não detectou)"));
            System.out.println();

            total++;
            if (detectou) sucessos++;
        }

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║  RESULTADO: " + sucessos + "/" + total + " detecções bem-sucedidas");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
    }
}

