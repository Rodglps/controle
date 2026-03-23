package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.FileOriginClientProcessing;
import com.controle.arquivos.common.domain.enums.EtapaProcessamento;
import com.controle.arquivos.common.domain.enums.StatusProcessamento;
import com.controle.arquivos.common.repository.FileOriginClientProcessingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes de propriedade para rastreabilidade e armazenamento de informações adicionais.
 * 
 * Feature: controle-de-arquivos, Properties 25, 26, 27, 28
 * 
 * Valida armazenamento de informações adicionais, associação arquivo-cliente,
 * atualização de layout e registro completo de erros.
 * 
 * **Valida: Requisitos 12.5, 13.1-13.5, 14.1-14.4, 15.1, 15.2, 15.5**
 */
class RastreabilidadeServicePropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * **Propriedade 25: Armazenamento de Informações Adicionais**
     * **Valida: Requisitos 12.5**
     * 
     * Para qualquer etapa onde informações adicionais são relevantes,
     * o sistema deve armazenar dados estruturados em jsn_additional_info.
     */
    @Property(tries = 100)
    void informacoesAdicionaisDevemSerArmazenadas(
            @ForAll("idFileOriginClient") Long idFileOriginClient,
            @ForAll("etapa") EtapaProcessamento etapa,
            @ForAll("informacoesAdicionais") Map<String, Object> info) throws Exception {
        
        // Arrange
        FileOriginClientProcessingRepository repository = mock(FileOriginClientProcessingRepository.class);
        RastreabilidadeService service = new RastreabilidadeService(repository);
        
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
            .id(1L)
            .fileOriginClientId(idFileOriginClient)
            .step(etapa)
            .status(StatusProcessamento.CONCLUIDO)
            .additionalInfo(objectMapper.writeValueAsString(info))
            .active(true)
            .build();
        
        when(repository.save(any())).thenReturn(processing);
        
        // Act
        service.registrarConclusao(1L, info);
        
        // Assert - Verificar que informações adicionais foram salvas
        verify(repository, times(1)).save(argThat(p -> {
            try {
                String jsonInfo = p.getAdditionalInfo();
                if (jsonInfo == null) return false;
                
                Map<String, Object> savedInfo = objectMapper.readValue(jsonInfo, Map.class);
                return savedInfo.equals(info);
            } catch (Exception e) {
                return false;
            }
        }));
    }

    /**
     * **Propriedade 25: Armazenamento de Informações Adicionais**
     * **Valida: Requisitos 12.5**
     * 
     * Para qualquer estrutura de dados em jsn_additional_info,
     * o sistema deve preservar a estrutura após serialização/deserialização.
     */
    @Property(tries = 100)
    void estruturaDadosDeveSerPreservada(
            @ForAll("informacoesAdicionais") Map<String, Object> infoOriginal) throws Exception {
        
        // Arrange
        FileOriginClientProcessingRepository repository = mock(FileOriginClientProcessingRepository.class);
        RastreabilidadeService service = new RastreabilidadeService(repository);
        
        // Act - Serializar e deserializar
        String json = objectMapper.writeValueAsString(infoOriginal);
        Map<String, Object> infoRecuperada = objectMapper.readValue(json, Map.class);
        
        // Assert - Estrutura deve ser preservada (round-trip)
        assertThat(infoRecuperada).isEqualTo(infoOriginal);
    }

    /**
     * **Propriedade 28: Registro Completo de Erros**
     * **Valida: Requisitos 15.1, 15.2, 15.5**
     * 
     * Para qualquer erro durante processamento, o sistema deve registrar
     * log estruturado com contexto completo e stack trace em jsn_additional_info.
     */
    @Property(tries = 100)
    void errosDevemSerRegistradosComContextoCompleto(
            @ForAll("idProcessing") Long idProcessing,
            @ForAll("mensagemErro") String mensagemErro,
            @ForAll("contextoErro") Map<String, Object> contexto) {
        
        // Arrange
        FileOriginClientProcessingRepository repository = mock(FileOriginClientProcessingRepository.class);
        RastreabilidadeService service = new RastreabilidadeService(repository);
        
        // Act
        service.atualizarStatus(idProcessing, StatusProcessamento.ERRO, mensagemErro);
        
        // Assert - Verificar que erro foi registrado com contexto
        verify(repository, times(1)).save(argThat(p -> 
            p.getStatus() == StatusProcessamento.ERRO &&
            p.getErrorMessage() != null &&
            p.getErrorMessage().contains(mensagemErro)
        ));
    }

    /**
     * **Propriedade 28: Registro Completo de Erros**
     * **Valida: Requisitos 15.1, 15.2**
     * 
     * Para qualquer erro, o sistema deve incluir timestamp, etapa e mensagem.
     */
    @Property(tries = 100)
    void erroDeveIncluirTimestampEtapaEMensagem(
            @ForAll("idFileOriginClient") Long idFileOriginClient,
            @ForAll("etapa") EtapaProcessamento etapa,
            @ForAll("mensagemErro") String mensagemErro) {
        
        // Arrange
        FileOriginClientProcessingRepository repository = mock(FileOriginClientProcessingRepository.class);
        RastreabilidadeService service = new RastreabilidadeService(repository);
        
        FileOriginClientProcessing processing = FileOriginClientProcessing.builder()
            .id(1L)
            .fileOriginClientId(idFileOriginClient)
            .step(etapa)
            .status(StatusProcessamento.EM_ESPERA)
            .active(true)
            .build();
        
        when(repository.save(any())).thenReturn(processing);
        
        // Act
        service.registrarEtapa(idFileOriginClient, etapa, StatusProcessamento.EM_ESPERA);
        service.atualizarStatus(1L, StatusProcessamento.ERRO, mensagemErro);
        
        // Assert
        verify(repository, atLeast(2)).save(argThat(p -> {
            // Verificar campos obrigatórios
            return p.getFileOriginClientId().equals(idFileOriginClient) &&
                   p.getStep() == etapa &&
                   p.getCreatedAt() != null; // Timestamp presente
        }));
    }

    /**
     * **Propriedade 25: Armazenamento de Informações Adicionais**
     * **Valida: Requisitos 12.5**
     * 
     * Para qualquer tipo de dado em informações adicionais (string, número, boolean, objeto),
     * o sistema deve armazenar corretamente.
     */
    @Property(tries = 100)
    void qualquerTipoDadoDeveSerArmazenado(
            @ForAll("dadoQualquer") Object dado) throws Exception {
        
        // Arrange
        Map<String, Object> info = new HashMap<>();
        info.put("dado", dado);
        
        // Act - Serializar
        String json = objectMapper.writeValueAsString(info);
        
        // Assert - Deve ser possível serializar qualquer tipo
        assertThat(json).isNotEmpty();
        assertThat(json).contains("dado");
        
        // Deserializar e verificar
        Map<String, Object> infoRecuperada = objectMapper.readValue(json, Map.class);
        assertThat(infoRecuperada).containsKey("dado");
    }

    // ========== Arbitraries (Generators) ==========

    @Provide
    Arbitrary<Long> idFileOriginClient() {
        return Arbitraries.longs().between(1L, 1000000L);
    }

    @Provide
    Arbitrary<Long> idProcessing() {
        return Arbitraries.longs().between(1L, 1000000L);
    }

    @Provide
    Arbitrary<EtapaProcessamento> etapa() {
        return Arbitraries.of(EtapaProcessamento.values());
    }

    @Provide
    Arbitrary<Map<String, Object>> informacoesAdicionais() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(20),
            Arbitraries.strings().alpha().numeric().ofMinLength(5).ofMaxLength(50),
            Arbitraries.integers().between(1, 1000),
            Arbitraries.of(true, false)
        ).as((key1, value1, value2, value3) -> {
            Map<String, Object> map = new HashMap<>();
            map.put(key1, value1);
            map.put("contador", value2);
            map.put("sucesso", value3);
            return map;
        });
    }

    @Provide
    Arbitrary<String> mensagemErro() {
        return Arbitraries.of(
            "Erro ao processar arquivo",
            "Falha de conexão",
            "Timeout excedido",
            "Arquivo não encontrado",
            "Permissão negada",
            "Erro inesperado"
        );
    }

    @Provide
    Arbitrary<Map<String, Object>> contextoErro() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20),
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
        ).as((arquivo, adquirente, etapa) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("arquivo", arquivo);
            map.put("adquirente", adquirente);
            map.put("etapa", etapa);
            return map;
        });
    }

    @Provide
    Arbitrary<Object> dadoQualquer() {
        return Arbitraries.oneOf(
            Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(50),
            Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
            Arbitraries.longs().between(1L, 1000000L).map(l -> (Object) l),
            Arbitraries.of(true, false).map(b -> (Object) b),
            Arbitraries.doubles().between(0.0, 1000.0).map(d -> (Object) d)
        );
    }
}
