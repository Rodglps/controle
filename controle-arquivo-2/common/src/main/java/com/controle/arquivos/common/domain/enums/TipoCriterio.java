package com.controle.arquivos.common.domain.enums;

/**
 * Enum que representa os tipos de critério para identificação.
 */
public enum TipoCriterio {
    COMECA_COM("COMECA-COM"),
    TERMINA_COM("TERMINA-COM"),
    CONTEM("CONTEM"),
    IGUAL("IGUAL");

    private final String valor;

    TipoCriterio(String valor) {
        this.valor = valor;
    }

    public String getValor() {
        return valor;
    }

    public static TipoCriterio fromValor(String valor) {
        for (TipoCriterio tipo : values()) {
            if (tipo.valor.equals(valor)) {
                return tipo;
            }
        }
        throw new IllegalArgumentException("Tipo de critério inválido: " + valor);
    }
}
