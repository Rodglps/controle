package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.CustomerIdentification;
import com.controle.arquivos.common.domain.entity.CustomerIdentificationRule;
import com.controle.arquivos.common.domain.enums.TipoCriterio;
import com.controle.arquivos.common.repository.CustomerIdentificationRepository;
import com.controle.arquivos.common.repository.CustomerIdentificationRuleRepository;
import net.jqwik.api.*;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Testes baseados em propriedades para ClienteIdentificationService.
 * 
 * Feature: controle-de-arquivos, Property 14: Aplicação de Regras de Identificação de Cliente
 * 
 * Para qualquer arquivo processado e qualquer regra de customer_identification_rule,
 * o Processador deve aplicar o critério (COMECA-COM, TERMINA-COM, CONTEM, IGUAL) à substring
 * extraída do nome do arquivo usando num_starting_position e num_ending_position, e considerar
 * o cliente identificado apenas se TODAS as regras ativas retornarem true.
 * 
 * **Valida: Requisitos 8.1, 8.2, 8.3, 8.4**
 */
class ClienteIdentificationServicePropertyTest {

    /**
     * Propriedade 14: Aplicação de Regras de Identificação de Cliente
     * 
     * Para qualquer arquivo e conjunto de regras, o cliente só deve ser identificado
     * se TODAS as regras retornarem true (AND lógico).
     */
    @Property(tries = 100)
    void identificar_deveAplicarTodasAsRegrasComANDLogico(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("acquirerId") Long acquirerId,
        @ForAll("regrasQueDevemPassar") List<CustomerIdentificationRule> regrasQuePassam
    ) {
        // Arrange
        CustomerIdentificationRuleRepository ruleRepository = mock(CustomerIdentificationRuleRepository.class);
        CustomerIdentificationRepository customerRepository = mock(CustomerIdentificationRepository.class);
        ClienteIdentificationService service = new ClienteIdentificationService(ruleRepository, customerRepository);

        Long customerId = 100L;
        
        // Configurar regras para retornar as regras que devem passar
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
            .thenReturn(regrasQuePassam);

        CustomerIdentification customer = CustomerIdentification.builder()
            .id(customerId)
            .customerName("Cliente Teste")
            .processingWeight(10)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        when(customerRepository.findActiveByIds(Collections.singletonList(customerId)))
            .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        // Se todas as regras passam, o cliente deve ser identificado
        boolean todasRegrasPassam = regrasQuePassam.stream()
            .allMatch(regra -> service.aplicarRegra(regra, nomeArquivo));

        if (todasRegrasPassam && !regrasQuePassam.isEmpty()) {
            assertTrue(result.isPresent(), 
                "Cliente deve ser identificado quando todas as regras passam");
            assertEquals(customerId, result.get().getId());
        } else {
            assertFalse(result.isPresent(), 
                "Cliente não deve ser identificado quando alguma regra falha ou não há regras");
        }
    }

    /**
     * Propriedade: Se pelo menos uma regra falha, o cliente não deve ser identificado.
     */
    @Property(tries = 100)
    void identificar_naoDeveIdentificarQuandoUmaRegraFalha(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("acquirerId") Long acquirerId,
        @ForAll("regrasComPeloMenosUmaFalha") List<CustomerIdentificationRule> regras
    ) {
        // Arrange
        CustomerIdentificationRuleRepository ruleRepository = mock(CustomerIdentificationRuleRepository.class);
        CustomerIdentificationRepository customerRepository = mock(CustomerIdentificationRepository.class);
        ClienteIdentificationService service = new ClienteIdentificationService(ruleRepository, customerRepository);

        when(ruleRepository.findActiveByAcquirerId(acquirerId))
            .thenReturn(regras);

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        // Verificar que pelo menos uma regra falha
        boolean peloMenosUmaRegraFalha = regras.stream()
            .anyMatch(regra -> !service.aplicarRegra(regra, nomeArquivo));

        if (peloMenosUmaRegraFalha) {
            assertFalse(result.isPresent(), 
                "Cliente não deve ser identificado quando pelo menos uma regra falha");
        }
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
        CustomerIdentificationRuleRepository ruleRepository = mock(CustomerIdentificationRuleRepository.class);
        CustomerIdentificationRepository customerRepository = mock(CustomerIdentificationRepository.class);
        ClienteIdentificationService service = new ClienteIdentificationService(ruleRepository, customerRepository);

        CustomerIdentificationRule regra = CustomerIdentificationRule.builder()
            .id(1L)
            .customerIdentificationId(100L)
            .acquirerId(1L)
            .criterionType(criterio.getValor())
            .value(valor)
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // Act
        boolean resultado = service.aplicarRegra(regra, nomeArquivo);

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
        CustomerIdentificationRuleRepository ruleRepository = mock(CustomerIdentificationRuleRepository.class);
        CustomerIdentificationRepository customerRepository = mock(CustomerIdentificationRepository.class);
        ClienteIdentificationService service = new ClienteIdentificationService(ruleRepository, customerRepository);

        // Extrair substring esperada (1-indexed para 0-indexed)
        String substringEsperada = nomeArquivo.substring(posicaoInicio - 1, posicaoFim);

        CustomerIdentificationRule regra = CustomerIdentificationRule.builder()
            .id(1L)
            .customerIdentificationId(100L)
            .acquirerId(1L)
            .criterionType(TipoCriterio.IGUAL.getValor())
            .value(substringEsperada)
            .startingPosition(posicaoInicio)
            .endingPosition(posicaoFim)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // Act
        boolean resultado = service.aplicarRegra(regra, nomeArquivo);

        // Assert
        assertTrue(resultado, 
            String.format("Substring extraída de '%s' nas posições %d-%d deve ser '%s'", 
                nomeArquivo, posicaoInicio, posicaoFim, substringEsperada));
    }

    /**
     * Propriedade: Múltiplos clientes com regras satisfeitas devem ser desempatados por peso.
     */
    @Property(tries = 100)
    void identificar_deveDesempatarPorPesoQuandoMultiplosClientesSatisfazemRegras(
        @ForAll("nomeArquivo") String nomeArquivo,
        @ForAll("acquirerId") Long acquirerId,
        @ForAll @IntRange(min = 2, max = 5) int numeroClientes
    ) {
        // Arrange
        CustomerIdentificationRuleRepository ruleRepository = mock(CustomerIdentificationRuleRepository.class);
        CustomerIdentificationRepository customerRepository = mock(CustomerIdentificationRepository.class);
        ClienteIdentificationService service = new ClienteIdentificationService(ruleRepository, customerRepository);

        // Criar regras que todas passam (usando CONTEM com substring vazia sempre passa)
        List<CustomerIdentificationRule> regras = new ArrayList<>();
        List<Long> customerIds = new ArrayList<>();
        List<CustomerIdentification> customers = new ArrayList<>();
        
        int pesoMaximo = 0;
        Long customerIdComMaiorPeso = null;

        for (int i = 0; i < numeroClientes; i++) {
            Long customerId = 100L + i;
            customerIds.add(customerId);
            
            // Criar regra que sempre passa (CONTEM com string vazia)
            CustomerIdentificationRule regra = CustomerIdentificationRule.builder()
                .id((long) i)
                .customerIdentificationId(customerId)
                .acquirerId(acquirerId)
                .criterionType(TipoCriterio.CONTEM.getValor())
                .value("")
                .startingPosition(null)
                .endingPosition(null)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            regras.add(regra);

            // Criar cliente com peso variado
            int peso = (i + 1) * 5;
            if (peso > pesoMaximo) {
                pesoMaximo = peso;
                customerIdComMaiorPeso = customerId;
            }

            CustomerIdentification customer = CustomerIdentification.builder()
                .id(customerId)
                .customerName("Cliente " + i)
                .processingWeight(peso)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            customers.add(customer);
        }

        when(ruleRepository.findActiveByAcquirerId(acquirerId))
            .thenReturn(regras);
        when(customerRepository.findActiveByIds(anyList()))
            .thenReturn(customers);

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent(), 
            "Cliente deve ser identificado quando múltiplos clientes satisfazem regras");
        assertEquals(customerIdComMaiorPeso, result.get().getId(), 
            "Cliente com maior peso deve ser selecionado");
        assertEquals(pesoMaximo, result.get().getProcessingWeight(), 
            "Peso do cliente selecionado deve ser o maior");
    }

    /**
     * Propriedade 16: Desempate de Múltiplos Clientes
     * 
     * **Valida: Requisitos 8.6**
     * 
     * Para qualquer arquivo onde múltiplos clientes satisfazem as regras,
     * o Processador deve selecionar o cliente com maior num_processing_weight.
     * 
     * Este teste gera múltiplos clientes com pesos diferentes, todos satisfazendo
     * as mesmas regras, e verifica que o cliente com maior peso é sempre selecionado.
     */
    @Property(tries = 100)
    void propriedade16_desempatePorPesoQuandoMultiplosClientesSatisfazemRegras(
        @ForAll("nomeArquivoComPrefixo") String nomeArquivo,
        @ForAll("acquirerId") Long acquirerId,
        @ForAll("multiplosClientesComPesosDiferentes") List<CustomerIdentification> clientes
    ) {
        Assume.that(clientes.size() >= 2);

        // Arrange
        CustomerIdentificationRuleRepository ruleRepository = mock(CustomerIdentificationRuleRepository.class);
        CustomerIdentificationRepository customerRepository = mock(CustomerIdentificationRepository.class);
        ClienteIdentificationService service = new ClienteIdentificationService(ruleRepository, customerRepository);

        // Criar regras que todas passam para todos os clientes
        // Usando COMECA_COM com o prefixo que sabemos que existe no nome do arquivo
        List<CustomerIdentificationRule> regras = new ArrayList<>();
        for (CustomerIdentification cliente : clientes) {
            CustomerIdentificationRule regra = CustomerIdentificationRule.builder()
                .id(cliente.getId())
                .customerIdentificationId(cliente.getId())
                .acquirerId(acquirerId)
                .criterionType(TipoCriterio.COMECA_COM.getValor())
                .value("ARQUIVO_")
                .startingPosition(null)
                .endingPosition(null)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
            regras.add(regra);
        }

        when(ruleRepository.findActiveByAcquirerId(acquirerId))
            .thenReturn(regras);
        when(customerRepository.findActiveByIds(anyList()))
            .thenReturn(clientes);

        // Encontrar cliente com maior peso
        CustomerIdentification clienteComMaiorPeso = clientes.stream()
            .max(Comparator.comparing(CustomerIdentification::getProcessingWeight))
            .orElseThrow();

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent(), 
            "Cliente deve ser identificado quando múltiplos clientes satisfazem todas as regras");
        
        assertEquals(clienteComMaiorPeso.getId(), result.get().getId(), 
            String.format("Cliente com maior peso (%d) deve ser selecionado. Clientes disponíveis: %s",
                clienteComMaiorPeso.getProcessingWeight(),
                clientes.stream()
                    .map(c -> String.format("ID=%d,Peso=%d", c.getId(), c.getProcessingWeight()))
                    .collect(Collectors.joining(", "))));
        
        assertEquals(clienteComMaiorPeso.getProcessingWeight(), result.get().getProcessingWeight(), 
            "Peso do cliente selecionado deve ser o maior entre todos os candidatos");
        
        // Verificar que o peso selecionado é realmente o máximo
        int pesoMaximo = clientes.stream()
            .mapToInt(CustomerIdentification::getProcessingWeight)
            .max()
            .orElse(0);
        
        assertEquals(pesoMaximo, result.get().getProcessingWeight(),
            "Peso do cliente selecionado deve ser igual ao peso máximo entre todos os clientes");
    }

    /**
     * Propriedade: Regras com valores nulos ou vazios devem ser tratadas corretamente.
     */
    @Property(tries = 50)
    void aplicarRegra_deveTratarValoresNulosOuVazios(
        @ForAll("nomeArquivo") String nomeArquivo
    ) {
        // Arrange
        CustomerIdentificationRuleRepository ruleRepository = mock(CustomerIdentificationRuleRepository.class);
        CustomerIdentificationRepository customerRepository = mock(CustomerIdentificationRepository.class);
        ClienteIdentificationService service = new ClienteIdentificationService(ruleRepository, customerRepository);

        // Regra com valor nulo
        CustomerIdentificationRule regraNula = CustomerIdentificationRule.builder()
            .id(1L)
            .customerIdentificationId(100L)
            .acquirerId(1L)
            .criterionType(TipoCriterio.IGUAL.getValor())
            .value(null)
            .startingPosition(null)
            .endingPosition(null)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // Act & Assert
        assertFalse(service.aplicarRegra(regraNula, nomeArquivo), 
            "Regra com valor nulo deve retornar false");
        assertFalse(service.aplicarRegra(null, nomeArquivo), 
            "Regra nula deve retornar false");
        assertFalse(service.aplicarRegra(regraNula, null), 
            "Nome de arquivo nulo deve retornar false");
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
        CustomerIdentificationRuleRepository ruleRepository = mock(CustomerIdentificationRuleRepository.class);
        CustomerIdentificationRepository customerRepository = mock(CustomerIdentificationRepository.class);
        ClienteIdentificationService service = new ClienteIdentificationService(ruleRepository, customerRepository);

        CustomerIdentificationRule regra = CustomerIdentificationRule.builder()
            .id(1L)
            .customerIdentificationId(100L)
            .acquirerId(1L)
            .criterionType(TipoCriterio.IGUAL.getValor())
            .value("TESTE")
            .startingPosition(posicaoInicio)
            .endingPosition(posicaoFim)
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // Act & Assert - Não deve lançar exceção
        assertDoesNotThrow(() -> service.aplicarRegra(regra, nomeArquivo), 
            "Aplicar regra com posições inválidas não deve lançar exceção");
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
    Arbitrary<List<CustomerIdentificationRule>> regrasQueDevemPassar() {
        return Combinators.combine(
            Arbitraries.longs().between(1L, 1000L), // acquirerId
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10) // prefixo comum
        ).as((acquirerId, prefixo) -> {
            Long customerId = 100L;
            
            // Criar 1-3 regras que todas passam para o mesmo cliente
            int numRegras = new Random().nextInt(3) + 1;
            List<CustomerIdentificationRule> regras = new ArrayList<>();
            
            for (int i = 0; i < numRegras; i++) {
                CustomerIdentificationRule regra = CustomerIdentificationRule.builder()
                    .id((long) i)
                    .customerIdentificationId(customerId)
                    .acquirerId(acquirerId)
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
    Arbitrary<List<CustomerIdentificationRule>> regrasComPeloMenosUmaFalha() {
        return Combinators.combine(
            Arbitraries.longs().between(1L, 1000L), // acquirerId
            Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10) // valor impossível
        ).as((acquirerId, valorImpossivel) -> {
            Long customerId = 100L;
            
            List<CustomerIdentificationRule> regras = new ArrayList<>();
            
            // Adicionar uma regra que sempre falha
            CustomerIdentificationRule regraQueFalha = CustomerIdentificationRule.builder()
                .id(1L)
                .customerIdentificationId(customerId)
                .acquirerId(acquirerId)
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
                CustomerIdentificationRule regra = CustomerIdentificationRule.builder()
                    .id((long) (i + 2))
                    .customerIdentificationId(customerId)
                    .acquirerId(acquirerId)
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

    @Provide
    Arbitrary<List<CustomerIdentification>> multiplosClientesComPesosDiferentes() {
        return Arbitraries.integers().between(2, 5).flatMap(numClientes -> {
            // Gerar lista de pesos únicos e diferentes
            return Arbitraries.shuffle(
                Arbitraries.integers().between(1, 100).list().ofSize(numClientes)
            ).map(pesos -> {
                List<CustomerIdentification> clientes = new ArrayList<>();
                for (int i = 0; i < numClientes; i++) {
                    CustomerIdentification cliente = CustomerIdentification.builder()
                        .id(100L + i)
                        .customerName("Cliente " + i)
                        .processingWeight(pesos.get(i))
                        .active(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                    clientes.add(cliente);
                }
                return clientes;
            });
        });
    }
}
