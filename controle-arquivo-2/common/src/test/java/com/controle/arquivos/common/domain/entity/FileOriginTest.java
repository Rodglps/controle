package com.controle.arquivos.common.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade FileOrigin.
 * Valida campos obrigatórios e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class FileOriginTest {

    @Test
    void deveCriarFileOriginComCamposObrigatorios() {
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName("CIELO_20240115.txt")
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .build();

        assertThat(fileOrigin.getAcquirerId()).isEqualTo(1L);
        assertThat(fileOrigin.getFileName()).isEqualTo("CIELO_20240115.txt");
        assertThat(fileOrigin.getFileSize()).isEqualTo(1024L);
        assertThat(fileOrigin.getFileTimestamp()).isNotNull();
        assertThat(fileOrigin.getSeverPathsInOutId()).isEqualTo(1L);
    }

    @Test
    void devePermitirLayoutIdOpcional() {
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName("CIELO_20240115.txt")
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .layoutId(10L)
                .build();

        assertThat(fileOrigin.getLayoutId()).isEqualTo(10L);
    }

    @Test
    void devePermitirFileTypeOpcional() {
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName("CIELO_20240115.txt")
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .fileType("CSV")
                .build();

        assertThat(fileOrigin.getFileType()).isEqualTo("CSV");
    }

    @Test
    void devePermitirTransactionTypeOpcional() {
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName("CIELO_20240115.txt")
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .transactionType("CREDITO")
                .build();

        assertThat(fileOrigin.getTransactionType()).isEqualTo("CREDITO");
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName("CIELO_20240115.txt")
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .build();

        fileOrigin.onCreate();

        assertThat(fileOrigin.getActive()).isTrue();
        assertThat(fileOrigin.getCreatedAt()).isNotNull();
        assertThat(fileOrigin.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName("CIELO_20240115.txt")
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .active(false)
                .build();

        fileOrigin.onCreate();

        assertThat(fileOrigin.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName("CIELO_20240115.txt")
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .build();

        fileOrigin.onCreate();
        var createdAt = fileOrigin.getCreatedAt();
        var updatedAt = fileOrigin.getUpdatedAt();

        Thread.sleep(10);
        fileOrigin.onUpdate();

        assertThat(fileOrigin.getCreatedAt()).isEqualTo(createdAt);
        assertThat(fileOrigin.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveSuportarNomeArquivoLongo() {
        String nomeArquivoLongo = "A".repeat(500);
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName(nomeArquivoLongo)
                .fileSize(1024L)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .build();

        assertThat(fileOrigin.getFileName()).hasSize(500);
    }

    @Test
    void deveSuportarArquivosGrandes() {
        Long tamanhoGrande = 10_000_000_000L; // 10GB
        FileOrigin fileOrigin = FileOrigin.builder()
                .acquirerId(1L)
                .fileName("arquivo_grande.zip")
                .fileSize(tamanhoGrande)
                .fileTimestamp(Instant.now())
                .severPathsInOutId(1L)
                .build();

        assertThat(fileOrigin.getFileSize()).isEqualTo(tamanhoGrande);
    }
}
