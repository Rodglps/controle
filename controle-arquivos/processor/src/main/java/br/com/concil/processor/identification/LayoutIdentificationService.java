package br.com.concil.processor.identification;

import br.com.concil.common.entity.Layout;
import br.com.concil.common.entity.LayoutIdentificationRule;
import br.com.concil.common.repository.LayoutRepository;
import br.com.concil.common.service.RuleMatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LayoutIdentificationService {

    private static final int HEADER_BYTES = 7000;

    private final LayoutRepository layoutRepository;
    private final RuleMatcherService ruleMatcher;

    /**
     * Identifica o layout do arquivo.
     * Regras com des_value_origin=FILENAME usam o nome do arquivo.
     * Regras com des_value_origin=HEADER usam os primeiros 7000 bytes (lidos via streaming).
     *
     * @param fileName    nome do arquivo
     * @param idtAcquirer adquirente
     * @param inputStream stream do arquivo (pode ser null se não houver regras HEADER)
     */
    public Optional<Layout> identify(String fileName, Long idtAcquirer, InputStream inputStream) {
        List<Layout> candidates = layoutRepository.findActiveByAcquirer(idtAcquirer);

        String headerContent = null; // lido sob demanda, apenas uma vez

        for (Layout layout : candidates) {
            boolean needsHeader = layout.getIdentificationRules().stream()
                    .anyMatch(r -> "HEADER".equals(r.getDesValueOrigin()) && r.getFlgActive() == 1);

            if (needsHeader && headerContent == null && inputStream != null) {
                headerContent = readHeader(inputStream);
            }

            if (allRulesMatch(layout.getIdentificationRules(), fileName, headerContent)) {
                log.info("Layout identificado: codLayout={} para arquivo={}", layout.getCodLayout(), fileName);
                return Optional.of(layout);
            }
        }

        log.warn("Nenhum layout identificado para arquivo={} acquirer={}", fileName, idtAcquirer);
        return Optional.empty();
    }

    private boolean allRulesMatch(List<LayoutIdentificationRule> rules, String fileName, String headerContent) {
        if (rules == null || rules.isEmpty()) return false;
        return rules.stream()
                .filter(r -> r.getFlgActive() != null && r.getFlgActive() == 1)
                .allMatch(rule -> {
                    String subject = switch (rule.getDesValueOrigin()) {
                        case "FILENAME" -> fileName;
                        case "HEADER"   -> headerContent;
                        default         -> null;
                    };
                    return ruleMatcher.matches(
                            rule.getDesCriterionType(),
                            subject,
                            rule.getDesValue(),
                            rule.getNumStartingPosition(),
                            rule.getNumEndingPosition());
                });
    }

    private String readHeader(InputStream inputStream) {
        try {
            byte[] buffer = new byte[HEADER_BYTES];
            int read = inputStream.read(buffer, 0, HEADER_BYTES);
            if (read <= 0) return "";
            return new String(buffer, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Erro ao ler header do arquivo: {}", e.getMessage(), e);
            return "";
        }
    }
}
