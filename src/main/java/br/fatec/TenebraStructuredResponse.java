package br.fatec;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class TenebraStructuredResponse {

    public String explicacao;
    public String sql;
    public boolean perigoso;

    public TenebraStructuredResponse() {}

    public TenebraStructuredResponse(String explicacao, String sql, boolean perigoso) {
        this.explicacao = explicacao;
        this.sql = sql;
        this.perigoso = perigoso;
    }
}

