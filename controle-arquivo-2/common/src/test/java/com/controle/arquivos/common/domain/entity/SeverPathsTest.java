package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.TipoCaminho;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade SeverPaths.
 * Valida campos obrigatórios, relacionamentos JPA e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class SeverPathsTest {

    @Test
    void deveCriarSeverPathsComCamposObrigatorios() {
        SeverPaths severPaths = SeverPaths.builder()
                .serverId(1L)
                .acquirerId(2L)
                .path("/sftp/cielo/input")
                .pathType(TipoCaminho.ORIGIN)
                .build();

        assertThat(severPaths.getServerId()).isEqualTo(1L);
        assertThat(severPaths.getAcquirerId()).isEqualTo(2L);
        assertThat(severPaths.getPath()).isEqualTo("/sftp/cielo/input");
        assertThat(severPaths.getPathType()).isEqualTo(TipoCaminho.ORIGIN);
    }

    @Test
    void deveSuportarTodosTiposCaminho() {
        for (TipoCaminho tipo : TipoCaminho.values()) {
            SeverPaths severPaths = SeverPaths.builder()
                    .serverId(1L)
                    .acquirerId(2L)
                    .path("/test/path")
                    .pathType(tipo)
                    .build();

            assertThat(severPaths.getPathType()).isEqualTo(tipo);
        }
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        SeverPaths severPaths = SeverPaths.builder()
                .serverId(1L)
                .acquirerId(2L)
                .path("/sftp/cielo/input")
                .pathType(TipoCaminho.ORIGIN)
                .build();

        severPaths.onCreate();

        assertThat(severPaths.getActive()).isTrue();
        assertThat(severPaths.getCreatedAt()).isNotNull();
        assertThat(severPaths.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        SeverPaths severPaths = SeverPaths.builder()
                .serverId(1L)
                .acquirerId(2L)
                .path("/sftp/cielo/input")
                .pathType(TipoCaminho.ORIGIN)
                .active(false)
                .build();

        severPaths.onCreate();

        assertThat(severPaths.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        SeverPaths severPaths = SeverPaths.builder()
                .serverId(1L)
                .acquirerId(2L)
                .path("/sftp/cielo/input")
                .pathType(TipoCaminho.ORIGIN)
                .build();

        severPaths.onCreate();
        var createdAt = severPaths.getCreatedAt();
        var updatedAt = severPaths.getUpdatedAt();

        Thread.sleep(10);
        severPaths.onUpdate();

        assertThat(severPaths.getCreatedAt()).isEqualTo(createdAt);
        assertThat(severPaths.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveSuportarCaminhoLongo() {
        String caminhoLongo = "/sftp/" + "A".repeat(490);
        SeverPaths severPaths = SeverPaths.builder()
                .serverId(1L)
                .acquirerId(2L)
                .path(caminhoLongo)
                .pathType(TipoCaminho.ORIGIN)
                .build();

        assertThat(severPaths.getPath()).hasSize(496);
    }

    @Test
    void deveRepresentarRelacionamentoComServer() {
        Long serverId = 100L;
        
        SeverPaths path1 = SeverPaths.builder()
                .serverId(serverId)
                .acquirerId(1L)
                .path("/path1")
                .pathType(TipoCaminho.ORIGIN)
                .build();

        SeverPaths path2 = SeverPaths.builder()
                .serverId(serverId)
                .acquirerId(2L)
                .path("/path2")
                .pathType(TipoCaminho.DESTINATION)
                .build();

        assertThat(path1.getServerId()).isEqualTo(path2.getServerId());
        assertThat(path1.getPath()).isNotEqualTo(path2.getPath());
    }

    @Test
    void devePermitirMultiplosCaminhosParaMesmoAcquirer() {
        Long acquirerId = 1L;

        SeverPaths origin = SeverPaths.builder()
                .serverId(1L)
                .acquirerId(acquirerId)
                .path("/sftp/input")
                .pathType(TipoCaminho.ORIGIN)
                .build();

        SeverPaths destination = SeverPaths.builder()
                .serverId(2L)
                .acquirerId(acquirerId)
                .path("/s3/output")
                .pathType(TipoCaminho.DESTINATION)
                .build();

        assertThat(origin.getAcquirerId()).isEqualTo(destination.getAcquirerId());
        assertThat(origin.getPathType()).isNotEqualTo(destination.getPathType());
    }
}
