package br.com.concil.processor.identification;

import br.com.concil.common.entity.CustomerIdentification;
import br.com.concil.common.entity.CustomerIdentificationRule;
import br.com.concil.common.repository.CustomerIdentificationRepository;
import br.com.concil.common.service.RuleMatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerIdentificationServiceTest {

    @Mock
    private CustomerIdentificationRepository repository;

    @Spy
    private RuleMatcherService ruleMatcher;

    @InjectMocks
    private CustomerIdentificationService service;

    private CustomerIdentification buildCustomer(Long clientId, String criterion, String value) {
        CustomerIdentificationRule rule = new CustomerIdentificationRule();
        rule.setDesCriterionType(criterion);
        rule.setDesValue(value);
        rule.setFlgActive(1);
        rule.setDesRule("Regra de teste");
        rule.setNamChangeAgent("test");

        CustomerIdentification ci = new CustomerIdentification();
        ci.setId(1L);
        ci.setIdtClient(clientId);
        ci.setIdtAcquirer(10L);
        ci.setFlgActive(1);
        ci.setRules(List.of(rule));
        return ci;
    }

    @Test
    void deveIdentificarClienteQuandoTodasAsRegrasPassam() {
        CustomerIdentification ci = buildCustomer(42L, "COMECA-COM", "CIELO");
        when(repository.findActiveByAcquirer(10L)).thenReturn(List.of(ci));

        Optional<CustomerIdentification> result = service.identify("CIELO_EDI_20240101.txt", 10L);

        assertThat(result).isPresent();
        assertThat(result.get().getIdtClient()).isEqualTo(42L);
    }

    @Test
    void naoDeveIdentificarClienteQuandoUmaRegraFalha() {
        CustomerIdentification ci = buildCustomer(42L, "COMECA-COM", "REDE");
        when(repository.findActiveByAcquirer(10L)).thenReturn(List.of(ci));

        Optional<CustomerIdentification> result = service.identify("CIELO_EDI_20240101.txt", 10L);

        assertThat(result).isEmpty();
    }

    @Test
    void deveRetornarVazioQuandoNenhumCandidato() {
        when(repository.findActiveByAcquirer(10L)).thenReturn(List.of());

        Optional<CustomerIdentification> result = service.identify("CIELO_EDI_20240101.txt", 10L);

        assertThat(result).isEmpty();
    }

    @Test
    void deveIdentificarComMultiplasRegrasTodasPassando() {
        CustomerIdentificationRule rule1 = new CustomerIdentificationRule();
        rule1.setDesCriterionType("COMECA-COM");
        rule1.setDesValue("CIELO");
        rule1.setFlgActive(1);
        rule1.setDesRule("Começa com CIELO");
        rule1.setNamChangeAgent("test");

        CustomerIdentificationRule rule2 = new CustomerIdentificationRule();
        rule2.setDesCriterionType("TERMINA-COM");
        rule2.setDesValue(".txt");
        rule2.setFlgActive(1);
        rule2.setDesRule("Termina com .txt");
        rule2.setNamChangeAgent("test");

        CustomerIdentification ci = new CustomerIdentification();
        ci.setId(1L);
        ci.setIdtClient(99L);
        ci.setIdtAcquirer(10L);
        ci.setFlgActive(1);
        ci.setRules(List.of(rule1, rule2));

        when(repository.findActiveByAcquirer(10L)).thenReturn(List.of(ci));

        Optional<CustomerIdentification> result = service.identify("CIELO_EDI_20240101.txt", 10L);

        assertThat(result).isPresent();
        assertThat(result.get().getIdtClient()).isEqualTo(99L);
    }
}
