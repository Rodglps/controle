package com.controle.arquivos.common.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade FileOriginClient.
 * Valida campos obrigatórios, relacionamentos JPA e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class FileOriginClientTest {

    @Test
    void deveCriarFileOriginClientComCamposObrigatorios() {
        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .fileOriginId(1L)
                .clientId(2L)
                .build();

        assertThat(fileOriginClient.getFileOriginId()).isEqualTo(1L);
        assertThat(fileOriginClient.getClientId()).isEqualTo(2L);
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .fileOriginId(1L)
                .clientId(2L)
                .build();

        fileOriginClient.onCreate();

        assertThat(fileOriginClient.getActive()).isTrue();
        assertThat(fileOriginClient.getCreatedAt()).isNotNull();
        assertThat(fileOriginClient.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .fileOriginId(1L)
                .clientId(2L)
                .active(false)
                .build();

        fileOriginClient.onCreate();

        assertThat(fileOriginClient.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .fileOriginId(1L)
                .clientId(2L)
                .build();

        fileOriginClient.onCreate();
        var createdAt = fileOriginClient.getCreatedAt();
        var updatedAt = fileOriginClient.getUpdatedAt();

        Thread.sleep(10);
        fileOriginClient.onUpdate();

        assertThat(fileOriginClient.getCreatedAt()).isEqualTo(createdAt);
        assertThat(fileOriginClient.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveRepresentarRelacionamentoEntreFileOriginEClient() {
        Long fileOriginId = 100L;
        Long clientId = 200L;

        FileOriginClient fileOriginClient = FileOriginClient.builder()
                .fileOriginId(fileOriginId)
                .clientId(clientId)
                .build();

        assertThat(fileOriginClient.getFileOriginId()).isEqualTo(fileOriginId);
        assertThat(fileOriginClient.getClientId()).isEqualTo(clientId);
    }

    @Test
    void devePermitirMultiplasAssociacoesParaMesmoFileOrigin() {
        Long fileOriginId = 100L;

        FileOriginClient associacao1 = FileOriginClient.builder()
                .fileOriginId(fileOriginId)
                .clientId(1L)
                .build();

        FileOriginClient associacao2 = FileOriginClient.builder()
                .fileOriginId(fileOriginId)
                .clientId(2L)
                .build();

        assertThat(associacao1.getFileOriginId()).isEqualTo(associacao2.getFileOriginId());
        assertThat(associacao1.getClientId()).isNotEqualTo(associacao2.getClientId());
    }
}
