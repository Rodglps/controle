package com.controle.arquivos.common.domain.entity;

import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para a entidade FileOriginClientProcessing.
 * Valida campos obrigatórios, relacionamentos JPA e comportamento de lifecycle callbacks.
 * 
 * Valida: Requisitos 1.1, 3.1
 */
class FileOriginClientProcessingTest {

    @Test
    void deveCriarFileOriginClientProcessingComCamposObrigatorios() {
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.EM_ESPERA)
                .build();

        assertThat(processing.getFileOriginClientId()).isEqualTo(1L);
        assertThat(processing.getStep()).isEqualTo(EtapaProcessamento.COLETA);
        assertThat(processing.getStatus()).isEqualTo(StatusProcessamento.EM_ESPERA);
    }

    @Test
    void devePermitirMessageErrorOpcional() {
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.RAW)
                .status(StatusProcessamento.ERRO)
                .messageError("Erro ao baixar arquivo")
                .build();

        assertThat(processing.getMessageError()).isEqualTo("Erro ao baixar arquivo");
    }

    @Test
    void devePermitirMessageAlertOpcional() {
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.STAGING)
                .status(StatusProcessamento.PROCESSAMENTO)
                .messageAlert("Arquivo grande, processamento pode demorar")
                .build();

        assertThat(processing.getMessageAlert()).isEqualTo("Arquivo grande, processamento pode demorar");
    }

    @Test
    void devePermitirStepStartOpcional() {
        Instant now = Instant.now();
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.PROCESSING)
                .status(StatusProcessamento.PROCESSAMENTO)
                .stepStart(now)
                .build();

        assertThat(processing.getStepStart()).isEqualTo(now);
    }

    @Test
    void devePermitirStepEndOpcional() {
        Instant now = Instant.now();
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.PROCESSED)
                .status(StatusProcessamento.CONCLUIDO)
                .stepEnd(now)
                .build();

        assertThat(processing.getStepEnd()).isEqualTo(now);
    }

    @Test
    void devePermitirAdditionalInfoOpcional() {
        String jsonInfo = "{\"bucket\":\"my-bucket\",\"key\":\"file.txt\"}";
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.PROCESSED)
                .status(StatusProcessamento.CONCLUIDO)
                .additionalInfo(jsonInfo)
                .build();

        assertThat(processing.getAdditionalInfo()).isEqualTo(jsonInfo);
    }

    @Test
    void deveSuportarTodasEtapasProcessamento() {
        for (EtapaProcessamento etapa : EtapaProcessamento.values()) {
            FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                    .fileOriginClientId(1L)
                    .step(etapa)
                    .status(StatusProcessamento.EM_ESPERA)
                    .build();

            assertThat(processing.getStep()).isEqualTo(etapa);
        }
    }

    @Test
    void deveSuportarTodosStatusProcessamento() {
        for (StatusProcessamento status : StatusProcessamento.values()) {
            FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                    .fileOriginClientId(1L)
                    .step(EtapaProcessamento.COLETA)
                    .status(status)
                    .build();

            assertThat(processing.getStatus()).isEqualTo(status);
        }
    }

    @Test
    void deveInicializarActiveTrueNoPrePersist() {
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.EM_ESPERA)
                .build();

        processing.onCreate();

        assertThat(processing.getActive()).isTrue();
        assertThat(processing.getCreatedAt()).isNotNull();
        assertThat(processing.getUpdatedAt()).isNotNull();
    }

    @Test
    void deveManterActiveSeJaDefinido() {
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.EM_ESPERA)
                .active(false)
                .build();

        processing.onCreate();

        assertThat(processing.getActive()).isFalse();
    }

    @Test
    void deveAtualizarUpdatedAtNoPreUpdate() throws InterruptedException {
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.EM_ESPERA)
                .build();

        processing.onCreate();
        var createdAt = processing.getCreatedAt();
        var updatedAt = processing.getUpdatedAt();

        Thread.sleep(10);
        processing.onUpdate();

        assertThat(processing.getCreatedAt()).isEqualTo(createdAt);
        assertThat(processing.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    void deveRepresentarFluxoProcessamento() {
        Long fileOriginClientId = 100L;

        FileOriginClientProcessing coleta = FileOriginClientProcessing.builder()
                .fileOriginClientId(fileOriginClientId)
                .step(EtapaProcessamento.COLETA)
                .status(StatusProcessamento.CONCLUIDO)
                .build();

        FileOriginClientProcessing raw = FileOriginClientProcessing.builder()
                .fileOriginClientId(fileOriginClientId)
                .step(EtapaProcessamento.RAW)
                .status(StatusProcessamento.PROCESSAMENTO)
                .build();

        assertThat(coleta.getFileOriginClientId()).isEqualTo(raw.getFileOriginClientId());
        assertThat(coleta.getStep()).isNotEqualTo(raw.getStep());
    }

    @Test
    void deveSuportarMensagemErroLonga() {
        String mensagemLonga = "A".repeat(4000);
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.RAW)
                .status(StatusProcessamento.ERRO)
                .messageError(mensagemLonga)
                .build();

        assertThat(processing.getMessageError()).hasSize(4000);
    }

    @Test
    void deveSuportarJsonAdditionalInfoComplexo() {
        String jsonComplexo = "{\"bucket\":\"my-bucket\",\"key\":\"file.txt\",\"metadata\":{\"size\":1024,\"type\":\"CSV\"}}";
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
                .fileOriginClientId(1L)
                .step(EtapaProcessamento.PROCESSED)
                .status(StatusProcessamento.CONCLUIDO)
                .additionalInfo(jsonComplexo)
                .build();

        assertThat(processing.getAdditionalInfo()).contains("bucket");
        assertThat(processing.getAdditionalInfo()).contains("metadata");
    }
}
