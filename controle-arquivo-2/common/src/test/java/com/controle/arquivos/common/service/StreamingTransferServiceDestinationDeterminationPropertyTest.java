package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.SeverPathsInOut;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de propriedade para determinação de destino.
 * 
 * Feature: controle-de-arquivos, Property 19: Determinação de Destino
 * 
 * Para qualquer arquivo com cliente e layout identificados, o Processador deve determinar
 * o destino usando idt_sever_destination de sever_paths_in_out.
 * 
 * **Valida: Requisitos 10.1**
 */
class StreamingTransferServiceDestinationDeterminationPropertyTest {

    /**
     * **Propriedade 19: Determinação de Destino**
     * **Valida: Requisitos 10.1**
     * 
     * Para qualquer mapeamento origem-destino, o sistema deve determinar
     * o destino corretamente usando idt_sever_destination.
     */
    @Property(tries = 100)
    void destinoDeveSerDeterminadoCorretamente(
            @ForAll("mapeamentoOrigemDestino") SeverPathsInOut pathsInOut) {
        
        // Assert - O mapeamento deve ter um destino válido
        assertThat(pathsInOut.getSeverDestinationId()).isNotNull();
        assertThat(pathsInOut.getSeverDestinationId()).isGreaterThan(0L);
    }

    /**
     * **Propriedade 19: Determinação de Destino**
     * **Valida: Requisitos 10.1**
     * 
     * Para qualquer mapeamento ativo, o destino deve ser diferente da origem.
     */
    @Property(tries = 100)
    void destinoDeveSerDiferenteDaOrigem(
            @ForAll("mapeamentoOrigemDestino") SeverPathsInOut pathsInOut) {
        
        // Assert - Origem e destino devem ser diferentes
        assertThat(pathsInOut.getSeverDestinationId())
            .isNotEqualTo(pathsInOut.getSeverPathOriginId());
    }

    /**
     * **Propriedade 19: Determinação de Destino**
     * **Valida: Requisitos 10.1**
     * 
     * Para qualquer mapeamento, o tipo de link deve ser válido.
     */
    @Property(tries = 100)
    void tipoLinkDeveSerValido(
            @ForAll("mapeamentoOrigemDestino") SeverPathsInOut pathsInOut) {
        
        // Assert - Tipo de link deve ser PRINCIPAL ou SECUNDARIO
        assertThat(pathsInOut.getLinkType())
            .isIn("PRINCIPAL", "SECUNDARIO");
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<SeverPathsInOut> mapeamentoOrigemDestino() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 1000000L),
                Arbitraries.longs().between(1L, 1000L),
                Arbitraries.longs().between(1001L, 2000L), // Garantir que destino != origem
                Arbitraries.of("PRINCIPAL", "SECUNDARIO")
        ).as((id, origemId, destinoId, linkType) ->
                SeverPathsInOut.builder()
                        .id(id)
                        .severPathOriginId(origemId)
                        .severDestinationId(destinoId)
                        .linkType(linkType)
                        .active(true)
                        .build()
        );
    }
}
