package com.controle.arquivos.orchestrator.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para MensagemProcessamento
 * Valida serialização e deserialização (round-trip)
 */
class MensagemProcessamentoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deveSerializarEDeserializarCorretamente() throws Exception {
        // Given
        MensagemProcessamento original = MensagemProcessamento.builder()
            .idFileOrigin(123L)
            .nomeArquivo("CIELO_20240115.txt")
            .idMapeamentoOrigemDestino(456L)
            .correlationId("abc-123-def")
            .build();

        // When - Serializar para JSON
        String json = objectMapper.writeValueAsString(original);

        // Then - Verificar JSON contém campos esperados
        assertThat(json).contains("\"idt_file_origin\":123");
        assertThat(json).contains("\"des_file_name\":\"CIELO_20240115.txt\"");
        assertThat(json).contains("\"idt_sever_paths_in_out\":456");
        assertThat(json).contains("\"correlation_id\":\"abc-123-def\"");

        // When - Deserializar de volta para objeto
        MensagemProcessamento deserializada = objectMapper.readValue(json, MensagemProcessamento.class);

        // Then - Verificar todos os campos foram preservados (round-trip)
        assertThat(deserializada.getIdFileOrigin()).isEqualTo(original.getIdFileOrigin());
        assertThat(deserializada.getNomeArquivo()).isEqualTo(original.getNomeArquivo());
        assertThat(deserializada.getIdMapeamentoOrigemDestino()).isEqualTo(original.getIdMapeamentoOrigemDestino());
        assertThat(deserializada.getCorrelationId()).isEqualTo(original.getCorrelationId());
    }

    @Test
    void deveSerializarComCamposNulos() throws Exception {
        // Given
        MensagemProcessamento mensagem = MensagemProcessamento.builder()
            .idFileOrigin(1L)
            .build();

        // When
        String json = objectMapper.writeValueAsString(mensagem);
        MensagemProcessamento deserializada = objectMapper.readValue(json, MensagemProcessamento.class);

        // Then
        assertThat(deserializada.getIdFileOrigin()).isEqualTo(1L);
        assertThat(deserializada.getNomeArquivo()).isNull();
        assertThat(deserializada.getIdMapeamentoOrigemDestino()).isNull();
        assertThat(deserializada.getCorrelationId()).isNull();
    }

    @Test
    void deveDeserializarJsonComNomesSnakeCase() throws Exception {
        // Given
        String json = "{" +
            "\"idt_file_origin\":999," +
            "\"des_file_name\":\"TESTE.txt\"," +
            "\"idt_sever_paths_in_out\":888," +
            "\"correlation_id\":\"test-correlation-id\"" +
            "}";

        // When
        MensagemProcessamento mensagem = objectMapper.readValue(json, MensagemProcessamento.class);

        // Then
        assertThat(mensagem.getIdFileOrigin()).isEqualTo(999L);
        assertThat(mensagem.getNomeArquivo()).isEqualTo("TESTE.txt");
        assertThat(mensagem.getIdMapeamentoOrigemDestino()).isEqualTo(888L);
        assertThat(mensagem.getCorrelationId()).isEqualTo("test-correlation-id");
    }

    @Test
    void devePreservarTodosOsCamposEmRoundTrip() throws Exception {
        // Given - Mensagem com todos os campos preenchidos
        MensagemProcessamento original = MensagemProcessamento.builder()
            .idFileOrigin(100L)
            .nomeArquivo("ARQUIVO_COMPLETO.txt")
            .idMapeamentoOrigemDestino(200L)
            .correlationId("full-correlation-id-12345")
            .build();

        // When - Round-trip: serializar e deserializar
        String json = objectMapper.writeValueAsString(original);
        MensagemProcessamento roundTrip = objectMapper.readValue(json, MensagemProcessamento.class);

        // Then - Todos os campos devem ser idênticos
        assertThat(roundTrip).isEqualTo(original);
        assertThat(roundTrip.getIdFileOrigin()).isEqualTo(original.getIdFileOrigin());
        assertThat(roundTrip.getNomeArquivo()).isEqualTo(original.getNomeArquivo());
        assertThat(roundTrip.getIdMapeamentoOrigemDestino()).isEqualTo(original.getIdMapeamentoOrigemDestino());
        assertThat(roundTrip.getCorrelationId()).isEqualTo(original.getCorrelationId());
    }
}
