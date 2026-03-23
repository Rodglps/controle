package com.controle.arquivos.orchestrator.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes baseados em propriedades para MensagemProcessamento.
 * 
 * Feature: controle-de-arquivos, Property 8: Serialização de Mensagens RabbitMQ
 * 
 * Para qualquer mensagem publicada no RabbitMQ, a mensagem deve conter idt_file_origin,
 * des_file_name e idt_sever_paths_in_out, e ao ser consumida, esses campos devem ser
 * extraídos corretamente (round-trip).
 * 
 * **Valida: Requisitos 4.2, 6.2**
 */
class MensagemProcessamentoPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Propriedade 8: Serialização de Mensagens RabbitMQ
     * 
     * Para qualquer mensagem publicada no RabbitMQ, a mensagem deve conter idt_file_origin,
     * des_file_name e idt_sever_paths_in_out, e ao ser consumida, esses campos devem ser
     * extraídos corretamente (round-trip).
     * 
     * Este teste verifica que:
     * 1. Serialização para JSON preserva todos os campos obrigatórios
     * 2. Deserialização de JSON restaura todos os campos corretamente
     * 3. Round-trip (serializar → deserializar) preserva a mensagem original
     * 4. Campos JSON usam snake_case conforme especificação
     */
    @Property(tries = 200)
    void propriedade8_serializacaoDeserializacaoPreservaTodosCampos(
        @ForAll("idFileOrigin") Long idFileOrigin,
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("idMapeamentoOrigemDestino") Long idMapeamentoOrigemDestino,
        @ForAll("correlationId") String correlationId
    ) throws Exception {
        // Arrange - Criar mensagem original
        MensagemProcessamento original = MensagemProcessamento.builder()
            .idFileOrigin(idFileOrigin)
            .nomeArquivo(nomeArquivo)
            .idMapeamentoOrigemDestino(idMapeamentoOrigemDestino)
            .correlationId(correlationId)
            .build();

        // Act - Serializar para JSON
        String json = objectMapper.writeValueAsString(original);

        // Assert - Verificar que JSON contém campos em snake_case
        assertThat(json).contains("\"idt_file_origin\":" + idFileOrigin);
        assertThat(json).contains("\"des_file_name\":\"" + nomeArquivo + "\"");
        assertThat(json).contains("\"idt_sever_paths_in_out\":" + idMapeamentoOrigemDestino);
        assertThat(json).contains("\"correlation_id\":\"" + correlationId + "\"");

        // Act - Deserializar de volta para objeto
        MensagemProcessamento deserializada = objectMapper.readValue(json, MensagemProcessamento.class);

        // Assert - Verificar que todos os campos foram preservados (round-trip)
        assertThat(deserializada.getIdFileOrigin())
            .as("idFileOrigin deve ser preservado no round-trip")
            .isEqualTo(original.getIdFileOrigin());
        
        assertThat(deserializada.getNomeArquivo())
            .as("nomeArquivo deve ser preservado no round-trip")
            .isEqualTo(original.getNomeArquivo());
        
        assertThat(deserializada.getIdMapeamentoOrigemDestino())
            .as("idMapeamentoOrigemDestino deve ser preservado no round-trip")
            .isEqualTo(original.getIdMapeamentoOrigemDestino());
        
        assertThat(deserializada.getCorrelationId())
            .as("correlationId deve ser preservado no round-trip")
            .isEqualTo(original.getCorrelationId());

        // Assert - Verificar igualdade completa do objeto
        assertThat(deserializada)
            .as("Objeto deserializado deve ser igual ao original")
            .isEqualTo(original);
    }

    /**
     * Propriedade: Mensagens com campos nulos devem ser serializadas e deserializadas corretamente.
     * 
     * Apenas idFileOrigin é obrigatório, outros campos podem ser nulos.
     */
    @Property(tries = 100)
    void deveSerializarMensagensComCamposNulos(
        @ForAll("idFileOrigin") Long idFileOrigin,
        @ForAll("camposOpcionaisNulos") CamposOpcionais camposNulos
    ) throws Exception {
        // Arrange - Criar mensagem com alguns campos nulos
        MensagemProcessamento original = MensagemProcessamento.builder()
            .idFileOrigin(idFileOrigin)
            .nomeArquivo(camposNulos.nomeArquivo)
            .idMapeamentoOrigemDestino(camposNulos.idMapeamentoOrigemDestino)
            .correlationId(camposNulos.correlationId)
            .build();

        // Act - Round-trip
        String json = objectMapper.writeValueAsString(original);
        MensagemProcessamento deserializada = objectMapper.readValue(json, MensagemProcessamento.class);

        // Assert - Verificar que campos nulos são preservados
        assertThat(deserializada.getIdFileOrigin()).isEqualTo(idFileOrigin);
        assertThat(deserializada.getNomeArquivo()).isEqualTo(camposNulos.nomeArquivo);
        assertThat(deserializada.getIdMapeamentoOrigemDestino()).isEqualTo(camposNulos.idMapeamentoOrigemDestino);
        assertThat(deserializada.getCorrelationId()).isEqualTo(camposNulos.correlationId);
        assertThat(deserializada).isEqualTo(original);
    }

    /**
     * Propriedade: Deserialização de JSON com snake_case deve funcionar corretamente.
     * 
     * Simula mensagens recebidas do RabbitMQ com campos em snake_case.
     */
    @Property(tries = 200)
    void deveDeserializarJsonComSnakeCase(
        @ForAll("idFileOrigin") Long idFileOrigin,
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("idMapeamentoOrigemDestino") Long idMapeamentoOrigemDestino,
        @ForAll("correlationId") String correlationId
    ) throws Exception {
        // Arrange - Criar JSON manualmente com snake_case (simula mensagem do RabbitMQ)
        String json = String.format(
            "{\"idt_file_origin\":%d,\"des_file_name\":\"%s\",\"idt_sever_paths_in_out\":%d,\"correlation_id\":\"%s\"}",
            idFileOrigin,
            nomeArquivo.replace("\"", "\\\""), // Escapar aspas no nome do arquivo
            idMapeamentoOrigemDestino,
            correlationId.replace("\"", "\\\"")  // Escapar aspas no correlation ID
        );

        // Act - Deserializar
        MensagemProcessamento mensagem = objectMapper.readValue(json, MensagemProcessamento.class);

        // Assert - Verificar que todos os campos foram extraídos corretamente
        assertThat(mensagem.getIdFileOrigin()).isEqualTo(idFileOrigin);
        assertThat(mensagem.getNomeArquivo()).isEqualTo(nomeArquivo);
        assertThat(mensagem.getIdMapeamentoOrigemDestino()).isEqualTo(idMapeamentoOrigemDestino);
        assertThat(mensagem.getCorrelationId()).isEqualTo(correlationId);
    }

    /**
     * Propriedade: Múltiplos round-trips devem preservar a mensagem.
     * 
     * Verifica que serializar e deserializar múltiplas vezes não corrompe os dados.
     */
    @Property(tries = 100)
    void devePreservarMensagemEmMultiplosRoundTrips(
        @ForAll("idFileOrigin") Long idFileOrigin,
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("idMapeamentoOrigemDestino") Long idMapeamentoOrigemDestino,
        @ForAll("correlationId") String correlationId,
        @ForAll @IntRange(min = 2, max = 5) int numeroRoundTrips
    ) throws Exception {
        // Arrange
        MensagemProcessamento original = MensagemProcessamento.builder()
            .idFileOrigin(idFileOrigin)
            .nomeArquivo(nomeArquivo)
            .idMapeamentoOrigemDestino(idMapeamentoOrigemDestino)
            .correlationId(correlationId)
            .build();

        MensagemProcessamento atual = original;

        // Act - Realizar múltiplos round-trips
        for (int i = 0; i < numeroRoundTrips; i++) {
            String json = objectMapper.writeValueAsString(atual);
            atual = objectMapper.readValue(json, MensagemProcessamento.class);
        }

        // Assert - Mensagem final deve ser igual à original
        assertThat(atual).isEqualTo(original);
        assertThat(atual.getIdFileOrigin()).isEqualTo(original.getIdFileOrigin());
        assertThat(atual.getNomeArquivo()).isEqualTo(original.getNomeArquivo());
        assertThat(atual.getIdMapeamentoOrigemDestino()).isEqualTo(original.getIdMapeamentoOrigemDestino());
        assertThat(atual.getCorrelationId()).isEqualTo(original.getCorrelationId());
    }

    /**
     * Propriedade: Mensagens com caracteres especiais devem ser serializadas corretamente.
     * 
     * Testa nomes de arquivo e correlation IDs com caracteres especiais, unicode, etc.
     */
    @Property(tries = 100)
    void deveSerializarMensagensComCaracteresEspeciais(
        @ForAll("idFileOrigin") Long idFileOrigin,
        @ForAll("nomeArquivoComEspeciais") String nomeArquivo,
        @ForAll("idMapeamentoOrigemDestino") Long idMapeamentoOrigemDestino,
        @ForAll("correlationIdComEspeciais") String correlationId
    ) throws Exception {
        // Arrange
        MensagemProcessamento original = MensagemProcessamento.builder()
            .idFileOrigin(idFileOrigin)
            .nomeArquivo(nomeArquivo)
            .idMapeamentoOrigemDestino(idMapeamentoOrigemDestino)
            .correlationId(correlationId)
            .build();

        // Act - Round-trip
        String json = objectMapper.writeValueAsString(original);
        MensagemProcessamento deserializada = objectMapper.readValue(json, MensagemProcessamento.class);

        // Assert - Caracteres especiais devem ser preservados
        assertThat(deserializada.getNomeArquivo()).isEqualTo(nomeArquivo);
        assertThat(deserializada.getCorrelationId()).isEqualTo(correlationId);
        assertThat(deserializada).isEqualTo(original);
    }

    /**
     * Propriedade: Mensagens com valores extremos devem ser serializadas corretamente.
     * 
     * Testa valores mínimos e máximos para campos Long.
     */
    @Property(tries = 50)
    void deveSerializarMensagensComValoresExtremos(
        @ForAll("idFileOriginExtremo") Long idFileOrigin,
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("idMapeamentoOrigemDestinoExtremo") Long idMapeamentoOrigemDestino,
        @ForAll("correlationId") String correlationId
    ) throws Exception {
        // Arrange
        MensagemProcessamento original = MensagemProcessamento.builder()
            .idFileOrigin(idFileOrigin)
            .nomeArquivo(nomeArquivo)
            .idMapeamentoOrigemDestino(idMapeamentoOrigemDestino)
            .correlationId(correlationId)
            .build();

        // Act - Round-trip
        String json = objectMapper.writeValueAsString(original);
        MensagemProcessamento deserializada = objectMapper.readValue(json, MensagemProcessamento.class);

        // Assert - Valores extremos devem ser preservados
        assertThat(deserializada.getIdFileOrigin()).isEqualTo(idFileOrigin);
        assertThat(deserializada.getIdMapeamentoOrigemDestino()).isEqualTo(idMapeamentoOrigemDestino);
        assertThat(deserializada).isEqualTo(original);
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<Long> idFileOrigin() {
        return Arbitraries.longs().between(1L, 999999999L);
    }

    @Provide
    Arbitrary<Long> idFileOriginExtremo() {
        return Arbitraries.of(1L, Long.MAX_VALUE, Long.MAX_VALUE - 1, 100L, 999999999L);
    }

    @Provide
    Arbitrary<String> nomeArquivo() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-', '.', ' ')
            .ofMinLength(5)
            .ofMaxLength(100);
    }

    @Provide
    Arbitrary<String> nomeArquivoComEspeciais() {
        return Arbitraries.strings()
            .all()
            .ofMinLength(1)
            .ofMaxLength(50)
            .filter(s -> !s.contains("\"") && !s.contains("\\")) // Evitar problemas com JSON
            .filter(s -> s.trim().length() > 0);
    }

    @Provide
    Arbitrary<Long> idMapeamentoOrigemDestino() {
        return Arbitraries.longs().between(1L, 999999999L);
    }

    @Provide
    Arbitrary<Long> idMapeamentoOrigemDestinoExtremo() {
        return Arbitraries.of(1L, Long.MAX_VALUE, Long.MAX_VALUE - 1, 100L, 999999999L);
    }

    @Provide
    Arbitrary<String> correlationId() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('-', '_')
            .ofMinLength(10)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> correlationIdComEspeciais() {
        return Arbitraries.strings()
            .all()
            .ofMinLength(5)
            .ofMaxLength(50)
            .filter(s -> !s.contains("\"") && !s.contains("\\")) // Evitar problemas com JSON
            .filter(s -> s.trim().length() > 0);
    }

    @Provide
    Arbitrary<CamposOpcionais> camposOpcionaisNulos() {
        return Combinators.combine(
            Arbitraries.strings().alpha().numeric().withChars('_', '-', '.').ofMinLength(5).ofMaxLength(50).injectNull(0.3),
            Arbitraries.longs().between(1L, 999999999L).injectNull(0.3),
            Arbitraries.strings().alpha().numeric().withChars('-', '_').ofMinLength(10).ofMaxLength(50).injectNull(0.3)
        ).as(CamposOpcionais::new);
    }

    /**
     * Classe auxiliar para representar campos opcionais que podem ser nulos.
     */
    private static class CamposOpcionais {
        final String nomeArquivo;
        final Long idMapeamentoOrigemDestino;
        final String correlationId;

        CamposOpcionais(String nomeArquivo, Long idMapeamentoOrigemDestino, String correlationId) {
            this.nomeArquivo = nomeArquivo;
            this.idMapeamentoOrigemDestino = idMapeamentoOrigemDestino;
            this.correlationId = correlationId;
        }
    }
}
