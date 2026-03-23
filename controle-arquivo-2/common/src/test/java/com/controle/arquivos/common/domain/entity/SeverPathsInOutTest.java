package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.TipoLink;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade SeverPathsInOut.
 * Valida campos obrigatórios, relacionamentos JPA e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class SeverPathsInOutTest {

    @Test
    void deveCriarSeverPathsInOutComCamposObrigatorios() {
        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
                .severPathOriginId(1L)
                .severDestinationId(2L)
                .linkType(TipoLink.PRINCIPAL)
                .build();

        assertThat(pathsInOut.getSeverPathOriginId()).isEqualTo(1L);
        assertThat(pathsInOut.getSeverDestinationId()).isEqualTo(2L);
        assertThat(pathsInOut.getLinkType()).isEqualTo(TipoLink.PRINCIPAL);
    }

    @Test
    void deveSuportarTodosTiposLink() {
        for (TipoLink tipo : TipoLink.values()) {
            SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
                    .severPathOriginId(1L)
                    .severDestinationId(2L)
                    .linkType(tipo)
                    .build();

            assertThat(pathsInOut.getLinkType()).isEqualTo(tipo);
        }
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
                .severPathOriginId(1L)
                .severDestinationId(2L)
                .linkType(TipoLink.PRINCIPAL)
                .build();

        pathsInOut.onCreate();

        assertThat(pathsInOut.getActive()).isTrue();
        assertThat(pathsInOut.getCreatedAt()).isNotNull();
        assertThat(pathsInOut.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
                .severPathOriginId(1L)
                .severDestinationId(2L)
                .linkType(TipoLink.PRINCIPAL)
                .active(false)
                .build();

        pathsInOut.onCreate();

        assertThat(pathsInOut.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
                .severPathOriginId(1L)
                .severDestinationId(2L)
                .linkType(TipoLink.PRINCIPAL)
                .build();

        pathsInOut.onCreate();
        var createdAt = pathsInOut.getCreatedAt();
        var updatedAt = pathsInOut.getUpdatedAt();

        Thread.sleep(10);
        pathsInOut.onUpdate();

        assertThat(pathsInOut.getCreatedAt()).isEqualTo(createdAt);
        assertThat(pathsInOut.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveRepresentarMapeamentoOrigemDestino() {
        Long originId = 100L;
        Long destinationId = 200L;

        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
                .severPathOriginId(originId)
                .severDestinationId(destinationId)
                .linkType(TipoLink.PRINCIPAL)
                .build();

        assertThat(pathsInOut.getSeverPathOriginId()).isEqualTo(originId);
        assertThat(pathsInOut.getSeverDestinationId()).isEqualTo(destinationId);
    }

    @Test
    void devePermitirMultiplosDestinosParaMesmaOrigem() {
        Long originId = 100L;

        SeverPathsInOut principal = SeverPathsInOut.builder()
                .severPathOriginId(originId)
                .severDestinationId(200L)
                .linkType(TipoLink.PRINCIPAL)
                .build();

        SeverPathsInOut secundario = SeverPathsInOut.builder()
                .severPathOriginId(originId)
                .severDestinationId(300L)
                .linkType(TipoLink.SECUNDARIO)
                .build();

        assertThat(principal.getSeverPathOriginId()).isEqualTo(secundario.getSeverPathOriginId());
        assertThat(principal.getSeverDestinationId()).isNotEqualTo(secundario.getSeverDestinationId());
        assertThat(principal.getLinkType()).isNotEqualTo(secundario.getLinkType());
    }

    @Test
    void deveDistinguirLinkPrincipalDeSecundario() {
        SeverPathsInOut principal = SeverPathsInOut.builder()
                .severPathOriginId(1L)
                .severDestinationId(2L)
                .linkType(TipoLink.PRINCIPAL)
                .build();

        SeverPathsInOut secundario = SeverPathsInOut.builder()
                .severPathOriginId(1L)
                .severDestinationId(3L)
                .linkType(TipoLink.SECUNDARIO)
                .build();

        assertThat(principal.getLinkType()).isEqualTo(TipoLink.PRINCIPAL);
        assertThat(secundario.getLinkType()).isEqualTo(TipoLink.SECUNDARIO);
    }

    @Test
    void devePermitirOrigemEDestinoIguais() {
        // Caso de uso: cópia dentro do mesmo servidor
        SeverPathsInOut pathsInOut = SeverPathsInOut.builder()
                .severPathOriginId(1L)
                .severDestinationId(1L)
                .linkType(TipoLink.PRINCIPAL)
                .build();

        assertThat(pathsInOut.getSeverPathOriginId()).isEqualTo(pathsInOut.getSeverDestinationId());
    }
}
