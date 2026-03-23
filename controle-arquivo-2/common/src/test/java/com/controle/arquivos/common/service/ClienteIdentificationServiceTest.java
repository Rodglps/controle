package com.controle.arquivos.common.service;

import com.controle.arquivos.common.domain.entity.CustomerIdentification;
import com.controle.arquivos.common.domain.entity.CustomerIdentificationRule;
import com.controle.arquivos.common.domain.enums.TipoCriterio;
import com.controle.arquivos.common.repository.CustomerIdentificationRepository;
import com.controle.arquivos.common.repository.CustomerIdentificationRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ClienteIdentificationService.
 * 
 * **Valida: Requisitos 8.1, 8.2, 8.3, 8.4, 8.6**
 */
@ExtendWith(MockitoExtension.class)
class ClienteIdentificationServiceTest {

    @Mock
    private CustomerIdentificationRuleRepository ruleRepository;

    @Mock
    private CustomerIdentificationRepository customerRepository;

    private ClienteIdentificationService service;

    @BeforeEach
    void setUp() {
        service = new ClienteIdentificationService(ruleRepository, customerRepository);
    }

    // Helper methods to create test data
    private CustomerIdentificationRule createRule(Long id, Long customerId, Long acquirerId, 
                                                   TipoCriterio criterio, String value,
                                                   Integer startPos, Integer endPos) {
        return CustomerIdentificationRule.builder()
                .id(id)
                .customerIdentificationId(customerId)
                .acquirerId(acquirerId)
                .criterionType(criterio.getValor())
                .value(value)
                .startingPosition(startPos)
                .endingPosition(endPos)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private CustomerIdentification createCustomer(Long id, String name, Integer weight) {
        return CustomerIdentification.builder()
                .id(id)
                .customerName(name)
                .processingWeight(weight)
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // Test: COMECA-COM criterion
    @Test
    void identificar_deveIdentificarClienteComCriterioComecaCom() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_20240115.txt";
        
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", null, null);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
        assertEquals("Cliente CIELO", result.get().getCustomerName());
        
        verify(ruleRepository, times(1)).findActiveByAcquirerId(acquirerId);
        verify(customerRepository, times(1)).findActiveByIds(anyList());
    }

    // Test: TERMINA-COM criterion
    @Test
    void identificar_deveIdentificarClienteComCriterioTerminaCom() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "arquivo_REDE.txt";
        
        CustomerIdentificationRule rule = createRule(1L, 200L, acquirerId, 
                TipoCriterio.TERMINA_COM, "REDE.txt", null, null);
        
        CustomerIdentification customer = createCustomer(200L, "Cliente REDE", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(200L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(200L, result.get().getId());
        assertEquals("Cliente REDE", result.get().getCustomerName());
    }

    // Test: CONTEM criterion
    @Test
    void identificar_deveIdentificarClienteComCriterioContem() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "arquivo_STONE_20240115.txt";
        
        CustomerIdentificationRule rule = createRule(1L, 300L, acquirerId, 
                TipoCriterio.CONTEM, "STONE", null, null);
        
        CustomerIdentification customer = createCustomer(300L, "Cliente STONE", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(300L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(300L, result.get().getId());
        assertEquals("Cliente STONE", result.get().getCustomerName());
    }

    // Test: IGUAL criterion
    @Test
    void identificar_deveIdentificarClienteComCriterioIgual() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "GETNET";
        
        CustomerIdentificationRule rule = createRule(1L, 400L, acquirerId, 
                TipoCriterio.IGUAL, "GETNET", null, null);
        
        CustomerIdentification customer = createCustomer(400L, "Cliente GETNET", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(400L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(400L, result.get().getId());
        assertEquals("Cliente GETNET", result.get().getCustomerName());
    }

    // Test: Substring extraction with positions
    @Test
    void identificar_deveExtrairSubstringComPosicoesEspecificadas() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "ABCCIELO123.txt";
        // Extract positions 4-9 (1-indexed) = "CIELO"
        
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.IGUAL, "CIELO", 4, 9);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    @Test
    void identificar_deveExtrairSubstringComApenasPosiçãoInicial() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "PREFIXO_CIELO";
        // Extract from position 9 to end = "CIELO"
        
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.IGUAL, "CIELO", 9, null);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    @Test
    void identificar_deveExtrairSubstringComApenasPosiçãoFinal() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_SUFIXO";
        // Extract from start to position 5 = "CIELO"
        
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.IGUAL, "CIELO", null, 5);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    // Test: No client identified
    @Test
    void identificar_deveRetornarEmptyQuandoNenhumClienteIdentificado() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "ARQUIVO_DESCONHECIDO.txt";
        
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", null, null);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, times(1)).findActiveByAcquirerId(acquirerId);
        verify(customerRepository, never()).findActiveByIds(anyList());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoNenhumaRegraAtiva() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_20240115.txt";
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.emptyList());

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, times(1)).findActiveByAcquirerId(acquirerId);
        verify(customerRepository, never()).findActiveByIds(anyList());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoNomeArquivoVazio() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "";

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, never()).findActiveByAcquirerId(any());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoNomeArquivoNulo() {
        // Arrange
        Long acquirerId = 1L;

        // Act
        Optional<CustomerIdentification> result = service.identificar(null, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, never()).findActiveByAcquirerId(any());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoAdquirenteNulo() {
        // Arrange
        String nomeArquivo = "CIELO_20240115.txt";

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, null);

        // Assert
        assertFalse(result.isPresent());
        verify(ruleRepository, never()).findActiveByAcquirerId(any());
    }

    // Test: Multiple clients with tie-breaking by weight
    @Test
    void identificar_deveDesempatarPorPesoQuandoMultiplosClientesSatisfazemRegras() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_20240115.txt";
        
        // Two clients with rules that both match
        CustomerIdentificationRule rule1 = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", null, null);
        CustomerIdentificationRule rule2 = createRule(2L, 200L, acquirerId, 
                TipoCriterio.CONTEM, "CIELO", null, null);
        
        CustomerIdentification customer1 = createCustomer(100L, "Cliente CIELO 1", 5);
        CustomerIdentification customer2 = createCustomer(200L, "Cliente CIELO 2", 10); // Higher weight
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2));
        when(customerRepository.findActiveByIds(Arrays.asList(100L, 200L)))
                .thenReturn(Arrays.asList(customer1, customer2));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(200L, result.get().getId());
        assertEquals("Cliente CIELO 2", result.get().getCustomerName());
        assertEquals(10, result.get().getProcessingWeight());
    }

    @Test
    void identificar_deveDesempatarPorPesoQuandoMultiplosClientesComPesosDiferentes() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "ARQUIVO_TESTE.txt";
        
        // Three clients with different weights
        CustomerIdentificationRule rule1 = createRule(1L, 100L, acquirerId, 
                TipoCriterio.CONTEM, "ARQUIVO", null, null);
        CustomerIdentificationRule rule2 = createRule(2L, 200L, acquirerId, 
                TipoCriterio.CONTEM, "TESTE", null, null);
        CustomerIdentificationRule rule3 = createRule(3L, 300L, acquirerId, 
                TipoCriterio.COMECA_COM, "ARQUIVO", null, null);
        
        CustomerIdentification customer1 = createCustomer(100L, "Cliente 1", 3);
        CustomerIdentification customer2 = createCustomer(200L, "Cliente 2", 15); // Highest weight
        CustomerIdentification customer3 = createCustomer(300L, "Cliente 3", 7);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2, rule3));
        when(customerRepository.findActiveByIds(Arrays.asList(100L, 200L, 300L)))
                .thenReturn(Arrays.asList(customer1, customer2, customer3));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(200L, result.get().getId());
        assertEquals(15, result.get().getProcessingWeight());
    }

    // Test: Multiple rules for same client (AND logic)
    @Test
    void identificar_deveAplicarTodasAsRegrasDoCliente_ANDLogico() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_20240115.txt";
        
        // Client with TWO rules - both must match
        CustomerIdentificationRule rule1 = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", null, null);
        CustomerIdentificationRule rule2 = createRule(2L, 100L, acquirerId, 
                TipoCriterio.TERMINA_COM, ".txt", null, null);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    @Test
    void identificar_naoDeveIdentificarQuandoUmaRegraFalha_ANDLogico() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_20240115.csv"; // Ends with .csv, not .txt
        
        // Client with TWO rules - second rule will fail
        CustomerIdentificationRule rule1 = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", null, null);
        CustomerIdentificationRule rule2 = createRule(2L, 100L, acquirerId, 
                TipoCriterio.TERMINA_COM, ".txt", null, null); // This will fail
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertFalse(result.isPresent());
        verify(customerRepository, never()).findActiveByIds(anyList());
    }

    @Test
    void identificar_deveAplicarTresRegrasComANDLogico() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_VENDAS_20240115.txt";
        
        // Client with THREE rules - all must match
        CustomerIdentificationRule rule1 = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", null, null);
        CustomerIdentificationRule rule2 = createRule(2L, 100L, acquirerId, 
                TipoCriterio.CONTEM, "VENDAS", null, null);
        CustomerIdentificationRule rule3 = createRule(3L, 100L, acquirerId, 
                TipoCriterio.TERMINA_COM, ".txt", null, null);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Arrays.asList(rule1, rule2, rule3));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    // Test: Edge cases for substring extraction
    @Test
    void identificar_deveRetornarStringVaziaQuandoPosicaoInicialExcedeTamanho() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO";
        
        // Position 10 exceeds filename length (5)
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.IGUAL, "", 10, 15);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void identificar_deveAjustarPosicaoFinalQuandoExcedeTamanho() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO";
        
        // Position end 100 exceeds filename length, should be adjusted to 5
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.IGUAL, "CIELO", 1, 100);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    @Test
    void identificar_deveRetornarStringVaziaQuandoPosicaoInicialMaiorOuIgualFinal() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_20240115.txt";
        
        // Start position >= end position
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.IGUAL, "", 5, 5);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertFalse(result.isPresent());
    }

    // Test: aplicarRegra method directly
    @Test
    void aplicarRegra_deveRetornarFalseQuandoRegraNula() {
        // Act
        boolean result = service.aplicarRegra(null, "CIELO_20240115.txt");

        // Assert
        assertFalse(result);
    }

    @Test
    void aplicarRegra_deveRetornarFalseQuandoNomeArquivoNulo() {
        // Arrange
        CustomerIdentificationRule rule = createRule(1L, 100L, 1L, 
                TipoCriterio.COMECA_COM, "CIELO", null, null);

        // Act
        boolean result = service.aplicarRegra(rule, null);

        // Assert
        assertFalse(result);
    }

    @Test
    void aplicarRegra_deveRetornarFalseQuandoValorEsperadoNulo() {
        // Arrange
        CustomerIdentificationRule rule = createRule(1L, 100L, 1L, 
                TipoCriterio.COMECA_COM, null, null, null);

        // Act
        boolean result = service.aplicarRegra(rule, "CIELO_20240115.txt");

        // Assert
        assertFalse(result);
    }

    // Test: Complex scenarios
    @Test
    void identificar_deveIdentificarComSubstringECriterioComecaCom() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "PREFIX_CIELO_SUFFIX.txt";
        // Extract positions 8-13 (1-indexed) = "CIELO_"
        
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", 8, 13);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }

    @Test
    void identificar_deveRetornarEmptyQuandoClienteNaoEncontradoNoBanco() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_20240115.txt";
        
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", null, null);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.emptyList()); // Customer not found or inactive

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void identificar_deveTratarCaseSensitiveCorretamente() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "cielo_20240115.txt"; // lowercase
        
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.COMECA_COM, "CIELO", null, null); // uppercase
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertFalse(result.isPresent()); // Should not match due to case sensitivity
    }

    @Test
    void identificar_deveIdentificarComPosicaoZeroComoInicio() {
        // Arrange
        Long acquirerId = 1L;
        String nomeArquivo = "CIELO_20240115.txt";
        
        // Position 0 should be treated as position 1 (start of string)
        CustomerIdentificationRule rule = createRule(1L, 100L, acquirerId, 
                TipoCriterio.IGUAL, "CIELO", 0, 5);
        
        CustomerIdentification customer = createCustomer(100L, "Cliente CIELO", 10);
        
        when(ruleRepository.findActiveByAcquirerId(acquirerId))
                .thenReturn(Collections.singletonList(rule));
        when(customerRepository.findActiveByIds(Collections.singletonList(100L)))
                .thenReturn(Collections.singletonList(customer));

        // Act
        Optional<CustomerIdentification> result = service.identificar(nomeArquivo, acquirerId);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(100L, result.get().getId());
    }
}
