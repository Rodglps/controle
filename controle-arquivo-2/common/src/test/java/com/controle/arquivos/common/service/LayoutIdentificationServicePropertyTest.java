package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.Layout;
import com.controle.arquivos.common.domain.entity.LayoutIdentificationRule;
import com.controle.arquivos.common.domain.enums.OrigemValor;
import com.controle.arquivos.common.domain.enums.TipoCriterio;
import com.controle.arquivos.common.repository.LayoutIdentificationRuleRepository;
import jakarta.persistence.EntityManager;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para LayoutIdentificationService.
 * 
 * Feature: controle-de-arquivos, Property 17: Aplicação de Regras de Identificação de Layout
 * 
 * Para qualquer arquivo após identificação de cliente e qualquer regra de layout_identification_rule,
 * o Processador deve aplicar a regra ao nome do arquivo (se des_value_origin = FILENAME) ou aos
 * primeiros 7000 bytes (se des_value_origin = HEADER), usando os critérios COMECA-COM, TERMINA-COM,
 * CONTEM ou IGUAL, e considerar o layout identificado apenas se TODAS as regras ativas retornarem true.
 * 
 * **Valida: Requisitos 9.1, 9.2, 9.3, 9.4, 9.5**
 */
class LayoutIdentificationServicePropertyTest {

    /**
     * Propriedade 17: Aplicação de Regras de Identificação de Layout
     * 
     * Para qualquer arquivo e conjunto de regras, o layout só deve ser identificado
     * se TODAS as regras retornarem true (AND lógico).
     */
    @Property(tries = 100)
    void propriedade17_identificar_deveAplicarTodasAsRegrasComANDLogico(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("headerContent") String headerContent,
        @ForAll("clientId") Long clientId,
        @ForAll("acquirerId") Long acquirerId,
        @ForAll("regrasQueDevemPassar") List<LayoutIdentificationRule> regrasQuePassam
    ) {
        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        Long layoutId = 100L;
        
        // Configurar regras para retornar as regras que devem passar
        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
            .thenReturn(regrasQuePassam);

        Layout layout = Layout.builder()
            .id(layoutId)
            .layoutName("Layout Teste")
            .layoutType("CSV")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(entityManager.find(Layout.class, layoutId))
            .thenReturn(layout);

        // Criar InputStream para header se necessário
        InputStream headerStream = null;
        boolean hasHeaderRules = regrasQuePassam.stream()
            .anyMatch(r -> r.getValueOrigin() == OrigemValor.HEADER);
        
        if (hasHeaderRules) {
            headerStream = new ByteArrayInputStream(headerContent.getBytes(StandardCharsets.UTF_8));
        }

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        // Verificar se todas as regras passam
        boolean todasRegrasPassam = true;
        for (LayoutIdentificationRule regra : regrasQuePassam) {
            String origem = regra.getValueOrigin() == OrigemValor.FILENAME ? nomeArquivo : headerContent;
            if (!service.aplicarRegra(regra, origem, headerContent)) {
                todasRegrasPassam = false;
                break;
            }
        }

        if (todasRegrasPassam && !regrasQuePassam.isEmpty()) {
            assertTrue(result.isPresent(), 
                "Layout deve ser identificado quando todas as regras passam");
            assertEquals(layoutId, result.get().getId());
        } else {
            assertFalse(result.isPresent(), 
                "Layout não deve ser identificado quando alguma regra falha ou não há regras");
        }
    }

    /**
     * Propriedade: Se pelo menos uma regra falha, o layout não deve ser identificado.
     */
    @Property(tries = 100)
    void identificar_naoDeveIdentificarQuandoUmaRegraFalha(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("headerContent") String headerContent,
        @ForAll("clientId") Long clientId,
        @ForAll("acquirerId") Long acquirerId,
        @ForAll("regrasComPeloMenosUmaFalha") List<LayoutIdentificationRule> regras
    ) {
        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
            .thenReturn(regras);

        // Criar InputStream para header se necessário
        InputStream headerStream = null;
        boolean hasHeaderRules = regras.stream()
            .anyMatch(r -> r.getValueOrigin() == OrigemValor.HEADER);
        
        if (hasHeaderRules) {
            headerStream = new ByteArrayInputStream(headerContent.getBytes(StandardCharsets.UTF_8));
        }

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        // Verificar que pelo menos uma regra falha
        boolean peloMenosUmaRegraFalha = false;
        for (LayoutIdentificationRule regra : regras) {
            String origem = regra.getValueOrigin() == OrigemValor.FILENAME ? nomeArquivo : headerContent;
            if (!service.aplicarRegra(regra, origem, headerContent)) {
                peloMenosUmaRegraFalha = true;
                break;
            }
        }

        if (peloMenosUmaRegraFalha) {
            assertFalse(result.isPresent(), 
                "Layout não deve ser identificado quando pelo menos uma regra falha");
        }
    }

    /**
     * Propriedade: Regras FILENAME devem ser aplicadas ao nome do arquivo.
     */
    @Property(tries = 100)
    void identificar_deveAplicarRegrasFilenameAoNomeDoArquivo(
        @ForAll("nomeArquivoComPrefixo") String nomeArquivo,
        @ForAll("clientId") Long clientId,
        @ForAll("acquirerId") Long acquirerId,
        @ForAll("criterio") TipoCriterio criterio
    ) {
        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        Long layoutId = 100L;
        
        // Criar regra FILENAME que deve passar
        LayoutIdentificationRule regra = LayoutIdentificationRule.builder()
            .id(1L)
            .layoutId(layoutId)
            .clientId(clientId)
            .acquirerId(acquirerId)
            .valueOrigin(OrigemValor.FILENAME)
            .criterionType(criterio.getValor())
            .value(getValorParaCriterio(nomeArquivo, criterio))
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
            .thenReturn(Collections.singletonList(regra));

        Layout layout = Layout.builder()
            .id(layoutId)
            .layoutName("Layout Teste")
            .layoutType("CSV")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(entityManager.find(Layout.class, layoutId))
            .thenReturn(layout);

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, null, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent(), 
            String.format("Layout deve ser identificado com regra FILENAME %s", criterio));
        assertEquals(layoutId, result.get().getId());
    }

    /**
     * Propriedade: Regras HEADER devem ser aplicadas ao conteúdo do header.
     */
    @Property(tries = 100)
    void identificar_deveAplicarRegrasHeaderAoConteudoDoHeader(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("headerContentComPrefixo") String headerContent,
        @ForAll("clientId") Long clientId,
        @ForAll("acquirerId") Long acquirerId,
        @ForAll("criterio") TipoCriterio criterio
    ) {
        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        Long layoutId = 100L;
        
        // Criar regra HEADER que deve passar
        LayoutIdentificationRule regra = LayoutIdentificationRule.builder()
            .id(1L)
            .layoutId(layoutId)
            .clientId(clientId)
            .acquirerId(acquirerId)
            .valueOrigin(OrigemValor.HEADER)
            .criterionType(criterio.getValor())
            .value(getValorParaCriterio(headerContent, criterio))
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
            .thenReturn(Collections.singletonList(regra));

        Layout layout = Layout.builder()
            .id(layoutId)
            .layoutName("Layout Teste")
            .layoutType("CSV")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(entityManager.find(Layout.class, layoutId))
            .thenReturn(layout);

        InputStream headerStream = new ByteArrayInputStream(headerContent.getBytes(StandardCharsets.UTF_8));

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent(), 
            String.format("Layout deve ser identificado com regra HEADER %s", criterio));
        assertEquals(layoutId, result.get().getId());
    }

    /**
     * Propriedade: Cada critério (COMECA-COM, TERMINA-COM, CONTEM, IGUAL) deve ser aplicado corretamente.
     */
    @Property(tries = 100)
    void aplicarRegra_deveAplicarCriteriosCorretamente(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("criterio") TipoCriterio criterio,
        @ForAll("valorParaTeste") String valor
    ) {
        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        LayoutIdentificationRule regra = LayoutIdentificationRule.builder()
            .id(1L)
            .layoutId(100L)
            .clientId(1L)
            .acquirerId(1L)
            .valueOrigin(OrigemValor.FILENAME)
            .criterionType(criterio.getValor())
            .value(valor)
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // Act
        boolean resultado = service.aplicarRegra(regra, nomeArquivo, nomeArquivo);

        // Assert
        boolean esperado = switch (criterio) {
            case COMECA_COM -> nomeArquivo.startsWith(valor);
            case TERMINA_COM -> nomeArquivo.endsWith(valor);
            case CONTEM -> nomeArquivo.contains(valor);
            case IGUAL -> nomeArquivo.equals(valor);
        };

        assertEquals(esperado, resultado, 
            String.format("Critério %s deve ser aplicado corretamente para '%s' e '%s'", 
                criterio, nomeArquivo, valor));
    }

    /**
     * Propriedade: Regras mistas (FILENAME e HEADER) devem ser aplicadas corretamente.
     */
    @Property(tries = 100)
    void identificar_deveAplicarRegrasMistasFilenameEHeader(
        @ForAll("nomeArquivoComPrefixo") String nomeArquivo,
        @ForAll("headerContentComPrefixo") String headerContent,
        @ForAll("clientId") Long clientId,
        @ForAll("acquirerId") Long acquirerId
    ) {
        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        Long layoutId = 100L;
        
        // Criar duas regras: uma FILENAME e uma HEADER, ambas devem passar
        LayoutIdentificationRule regraFilename = LayoutIdentificationRule.builder()
            .id(1L)
            .layoutId(layoutId)
            .clientId(clientId)
            .acquirerId(acquirerId)
            .valueOrigin(OrigemValor.FILENAME)
            .criterionType(TipoCriterio.COMECA_COM.getValor())
            .value("ARQUIVO_")
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        LayoutIdentificationRule regraHeader = LayoutIdentificationRule.builder()
            .id(2L)
            .layoutId(layoutId)
            .clientId(clientId)
            .acquirerId(acquirerId)
            .valueOrigin(OrigemValor.HEADER)
            .criterionType(TipoCriterio.COMECA_COM.getValor())
            .value("HEADER_")
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
            .thenReturn(Arrays.asList(regraFilename, regraHeader));

        Layout layout = Layout.builder()
            .id(layoutId)
            .layoutName("Layout Teste")
            .layoutType("CSV")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(entityManager.find(Layout.class, layoutId))
            .thenReturn(layout);

        InputStream headerStream = new ByteArrayInputStream(headerContent.getBytes(StandardCharsets.UTF_8));

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent(), 
            "Layout deve ser identificado quando regras FILENAME e HEADER passam");
        assertEquals(layoutId, result.get().getId());
    }

    /**
     * Propriedade: Extração de substring com posições deve funcionar corretamente.
     */
    @Property(tries = 100)
    void aplicarRegra_deveExtrairSubstringCorretamente(
        @ForAll("nomeArquivoLongo") String nomeArquivo,
        @ForAll @IntRange(min = 1, max = 20) int posicaoInicio,
        @ForAll @IntRange(min = 1, max = 30) int posicaoFim
    ) {
        Assume.that(posicaoInicio < posicaoFim);
        Assume.that(posicaoFim <= nomeArquivo.length());

        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        // Extrair substring esperada (1-indexed para 0-indexed)
        String substringEsperada = nomeArquivo.substring(posicaoInicio - 1, posicaoFim);

        LayoutIdentificationRule regra = LayoutIdentificationRule.builder()
            .id(1L)
            .layoutId(100L)
            .clientId(1L)
            .acquirerId(1L)
            .valueOrigin(OrigemValor.FILENAME)
            .criterionType(TipoCriterio.IGUAL.getValor())
            .value(substringEsperada)
            .startingPosition(posicaoInicio)
            .endingPosition(posicaoFim)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // Act
        boolean resultado = service.aplicarRegra(regra, nomeArquivo, nomeArquivo);

        // Assert
        assertTrue(resultado, 
            String.format("Substring extraída de '%s' nas posições %d-%d deve ser '%s'", 
                nomeArquivo, posicaoInicio, posicaoFim, substringEsperada));
    }

    /**
     * Propriedade: Regras com valores nulos ou vazios devem ser tratadas corretamente.
     */
    @Property(tries = 50)
    void aplicarRegra_deveTratarValoresNulosOuVazios(
        @ForAll("nomeArquivo") String nomeArquivo
    ) {
        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        // Regra com valor nulo
        LayoutIdentificationRule regraNula = LayoutIdentificationRule.builder()
            .id(1L)
            .layoutId(100L)
            .clientId(1L)
            .acquirerId(1L)
            .valueOrigin(OrigemValor.FILENAME)
            .criterionType(TipoCriterio.IGUAL.getValor())
            .value(null)
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // Act & Assert
        assertFalse(service.aplicarRegra(regraNula, nomeArquivo, nomeArquivo), 
            "Regra com valor nulo deve retornar false");
        assertFalse(service.aplicarRegra(null, nomeArquivo, nomeArquivo), 
            "Regra nula deve retornar false");
        assertFalse(service.aplicarRegra(regraNula, null, null), 
            "Conteúdo nulo deve retornar false");
    }

    /**
     * Propriedade: Posições inválidas devem ser tratadas sem lançar exceções.
     */
    @Property(tries = 100)
    void aplicarRegra_deveTratarPosicoesInvalidasSemExcecoes(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll @IntRange(min = 1, max = 1000) int posicaoInicio,
        @ForAll @IntRange(min = 1, max = 1000) int posicaoFim
    ) {
        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        LayoutIdentificationRule regra = LayoutIdentificationRule.builder()
            .id(1L)
            .layoutId(100L)
            .clientId(1L)
            .acquirerId(1L)
            .valueOrigin(OrigemValor.FILENAME)
            .criterionType(TipoCriterio.IGUAL.getValor())
            .value("TESTE")
            .startingPosition(posicaoInicio)
            .endingPosition(posicaoFim)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // Act & Assert - Não deve lançar exceção
        assertDoesNotThrow(() -> service.aplicarRegra(regra, nomeArquivo, nomeArquivo), 
            "Aplicar regra com posições inválidas não deve lançar exceção");
    }

    /**
     * Propriedade: Leitura de header deve respeitar limite de 7000 bytes.
     */
    @Property(tries = 50)
    void identificar_deveLerAte7000BytesDoHeader(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("headerContentGrande") String headerContent,
        @ForAll("clientId") Long clientId,
        @ForAll("acquirerId") Long acquirerId
    ) {
        Assume.that(headerContent.length() > 7000);

        // Arrange
        LayoutIdentificationRuleRepository ruleRepository = mock(LayoutIdentificationRuleRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        LayoutIdentificationService service = new LayoutIdentificationService(ruleRepository, entityManager);

        Long layoutId = 100L;
        
        // Criar regra que busca conteúdo dentro dos primeiros 7000 bytes
        String valorDentro7000 = headerContent.substring(0, Math.min(10, headerContent.length()));
        
        LayoutIdentificationRule regra = LayoutIdentificationRule.builder()
            .id(1L)
            .layoutId(layoutId)
            .clientId(clientId)
            .acquirerId(acquirerId)
            .valueOrigin(OrigemValor.HEADER)
            .criterionType(TipoCriterio.COMECA_COM.getValor())
            .value(valorDentro7000)
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(ruleRepository.findActiveByClientIdAndAcquirerId(clientId, acquirerId))
            .thenReturn(Collections.singletonList(regra));

        Layout layout = Layout.builder()
            .id(layoutId)
            .layoutName("Layout Teste")
            .layoutType("CSV")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(entityManager.find(Layout.class, layoutId))
            .thenReturn(layout);

        InputStream headerStream = new ByteArrayInputStream(headerContent.getBytes(StandardCharsets.UTF_8));

        // Act
        Optional<Layout> result = service.identificar(nomeArquivo, headerStream, clientId, acquirerId);

        // Assert
        assertTrue(result.isPresent(), 
            "Layout deve ser identificado quando valor está dentro dos primeiros 7000 bytes");
    }

    // ========== Helper Methods ==========

    private String getValorParaCriterio(String texto, TipoCriterio criterio) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        
        return switch (criterio) {
            case COMECA_COM -> texto.substring(0, Math.min(5, texto.length()));
            case TERMINA_COM -> texto.substring(Math.max(0, texto.length() - 5));
            case CONTEM -> texto.substring(Math.max(0, texto.length() / 2 - 2), 
                                          Math.min(texto.length(), texto.length() / 2 + 3));
            case IGUAL -> texto;
        };
    }

    // ========== Providers ==========

    @Provide
    Arbitrary<String> nomeArquivo() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-', '.')
            .ofMinLength(5)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> nomeArquivoComPrefixo() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-', '.')
            .ofMinLength(5)
            .ofMaxLength(30)
            .map(suffix -> "ARQUIVO_" + suffix);
    }

    @Provide
    Arbitrary<String> nomeArquivoLongo() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('_', '-', '.')
            .ofMinLength(30)
            .ofMaxLength(50);
    }

    @Provide
    Arbitrary<String> headerContent() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(' ', '\n', ';', ',', ':', '=', '{', '}', '[', ']', '<', '>', '/', '?', '"')
            .ofMinLength(10)
            .ofMaxLength(500);
    }

    @Provide
    Arbitrary<String> headerContentComPrefixo() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(' ', '\n', ';', ',', ':', '=')
            .ofMinLength(10)
            .ofMaxLength(300)
            .map(suffix -> "HEADER_" + suffix);
    }

    @Provide
    Arbitrary<String> headerContentGrande() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars(' ', '\n', ';', ',')
            .ofMinLength(8000)
            .ofMaxLength(10000);
    }

    @Provide
    Arbitrary<Long> clientId() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<Long> acquirerId() {
        return Arbitraries.longs().between(1L, 1000L);
    }

    @Provide
    Arbitrary<TipoCriterio> criterio() {
        return Arbitraries.of(TipoCriterio.values());
    }

    @Provide
    Arbitrary<String> valorParaTeste() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(1)
            .ofMaxLength(20);
    }

    @Provide
    Arbitrary<List<LayoutIdentificationRule>> regrasQueDevemPassar() {
        return Combinators.combine(
            Arbitraries.longs().between(1L, 1000L), // clientId
            Arbitraries.longs().between(1L, 1000L), // acquirerId
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10), // prefixo comum
            Arbitraries.of(OrigemValor.FILENAME, OrigemValor.HEADER)
        ).as((clientId, acquirerId, prefixo, origemValor) -> {
            Long layoutId = 100L;
            
            // Criar 1-3 regras que todas passam para o mesmo layout
            int numRegras = new Random().nextInt(3) + 1;
            List<LayoutIdentificationRule> regras = new ArrayList<>();
            
            for (int i = 0; i < numRegras; i++) {
                LayoutIdentificationRule regra = LayoutIdentificationRule.builder()
                    .id((long) i)
                    .layoutId(layoutId)
                    .clientId(clientId)
                    .acquirerId(acquirerId)
                    .valueOrigin(origemValor)
                    .criterionType(TipoCriterio.COMECA_COM.getValor())
                    .value(prefixo)
                    .startingPosition(null)
                    .endingPosition(null)
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
                regras.add(regra);
            }
            
            return regras;
        }).filter(regras -> !regras.isEmpty());
    }

    @Provide
    Arbitrary<List<LayoutIdentificationRule>> regrasComPeloMenosUmaFalha() {
        return Combinators.combine(
            Arbitraries.longs().between(1L, 1000L), // clientId
            Arbitraries.longs().between(1L, 1000L), // acquirerId
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10), // valor impossível
            Arbitraries.of(OrigemValor.FILENAME, OrigemValor.HEADER)
        ).as((clientId, acquirerId, valorImpossivel, origemValor) -> {
            Long layoutId = 100L;
            
            List<LayoutIdentificationRule> regras = new ArrayList<>();
            
            // Adicionar uma regra que sempre falha
            LayoutIdentificationRule regraQueFalha = LayoutIdentificationRule.builder()
                .id(1L)
                .layoutId(layoutId)
                .clientId(clientId)
                .acquirerId(acquirerId)
                .valueOrigin(origemValor)
                .criterionType(TipoCriterio.IGUAL.getValor())
                .value("VALOR_QUE_NUNCA_EXISTE_" + valorImpossivel + "_XYZ123")
                .startingPosition(null)
                .endingPosition(null)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            regras.add(regraQueFalha);
            
            // Adicionar 0-2 regras adicionais
            int numRegrasAdicionais = new Random().nextInt(3);
            for (int i = 0; i < numRegrasAdicionais; i++) {
                LayoutIdentificationRule regra = LayoutIdentificationRule.builder()
                    .id((long) (i + 2))
                    .layoutId(layoutId)
                    .clientId(clientId)
                    .acquirerId(acquirerId)
                    .valueOrigin(origemValor)
                    .criterionType(TipoCriterio.CONTEM.getValor())
                    .value("")
                    .startingPosition(null)
                    .endingPosition(null)
                    .active(true)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
                regras.add(regra);
            }
            
            return regras;
        });
    }
}
