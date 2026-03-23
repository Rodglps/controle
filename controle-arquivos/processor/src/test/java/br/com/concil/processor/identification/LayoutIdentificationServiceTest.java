package br.com.concil.processor.identification;

import br.com.concil.common.entity.Layout;
import br.com.concil.common.entity.LayoutIdentificationRule;
import br.com.concil.common.repository.LayoutRepository;
import br.com.concil.common.service.RuleMatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LayoutIdentificationServiceTest {

    @Mock
    private LayoutRepository layoutRepository;

    @Spy
    private RuleMatcherService ruleMatcher;

    @InjectMocks
    private LayoutIdentificationService service;

    private Layout buildLayout(String codLayout, String valueOrigin, String criterion, String value) {
        LayoutIdentificationRule rule = new LayoutIdentificationRule();
        rule.setDesValueOrigin(valueOrigin);
        rule.setDesCriterionType(criterion);
        rule.setDesValue(value);
        rule.setFlgActive(1);
        rule.setDesRule("Regra de teste");
        rule.setNamChangeAgent("test");

        Layout layout = new Layout();
        layout.setId(1L);
        layout.setCodLayout(codLayout);
        layout.setIdtAcquirer(10L);
        layout.setFlgActive(1);
        layout.setDesTransactionType("COMPLETO");
        layout.setDesFileType("TXT");
        layout.setDesDistributionType("DIARIO");
        layout.setNamChangeAgent("test");
        layout.setIdentificationRules(List.of(rule));
        return layout;
    }

    @Test
    void deveIdentificarLayoutPorNomeDoArquivo() {
        Layout layout = buildLayout("CIELO_TXT_01", "FILENAME", "COMECA-COM", "CIELO");
        when(layoutRepository.findActiveByAcquirer(10L)).thenReturn(List.of(layout));

        Optional<Layout> result = service.identify("CIELO_EDI_20240101.txt", 10L, null);

        assertThat(result).isPresent();
        assertThat(result.get().getCodLayout()).isEqualTo("CIELO_TXT_01");
    }

    @Test
    void deveIdentificarLayoutPorHeader() {
        Layout layout = buildLayout("CIELO_HEADER_01", "HEADER", "COMECA-COM", "HEADER_CIELO");
        when(layoutRepository.findActiveByAcquirer(10L)).thenReturn(List.of(layout));

        String headerContent = "HEADER_CIELO_V2_20240101";
        ByteArrayInputStream stream = new ByteArrayInputStream(headerContent.getBytes(StandardCharsets.UTF_8));

        Optional<Layout> result = service.identify("arquivo.txt", 10L, stream);

        assertThat(result).isPresent();
        assertThat(result.get().getCodLayout()).isEqualTo("CIELO_HEADER_01");
    }

    @Test
    void naoDeveIdentificarLayoutQuandoRegraFalha() {
        Layout layout = buildLayout("REDE_TXT_01", "FILENAME", "COMECA-COM", "REDE");
        when(layoutRepository.findActiveByAcquirer(10L)).thenReturn(List.of(layout));

        Optional<Layout> result = service.identify("CIELO_EDI_20240101.txt", 10L, null);

        assertThat(result).isEmpty();
    }
}
