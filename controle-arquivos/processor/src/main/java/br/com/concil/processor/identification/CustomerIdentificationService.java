package br.com.concil.processor.identification;

import br.com.concil.common.entity.CustomerIdentification;
import br.com.concil.common.entity.CustomerIdentificationRule;
import br.com.concil.common.repository.CustomerIdentificationRepository;
import br.com.concil.common.service.RuleMatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerIdentificationService {

    private final CustomerIdentificationRepository repository;
    private final RuleMatcherService ruleMatcher;

    /**
     * Identifica o cliente pelo nome do arquivo.
     * Retorna o primeiro CustomerIdentification onde TODAS as regras ativas são satisfeitas.
     * Candidatos são ordenados por num_processing_weight DESC.
     */
    public Optional<CustomerIdentification> identify(String fileName, Long idtAcquirer) {
        List<CustomerIdentification> candidates = repository.findActiveByAcquirer(idtAcquirer);

        for (CustomerIdentification candidate : candidates) {
            if (allRulesMatch(candidate.getRules(), fileName)) {
                log.info("Cliente identificado: idtClient={} para arquivo={}", candidate.getIdtClient(), fileName);
                return Optional.of(candidate);
            }
        }

        log.warn("Nenhum cliente identificado para arquivo={} acquirer={}", fileName, idtAcquirer);
        return Optional.empty();
    }

    private boolean allRulesMatch(List<CustomerIdentificationRule> rules, String fileName) {
        if (rules == null || rules.isEmpty()) return false;
        return rules.stream()
                .filter(r -> r.getFlgActive() != null && r.getFlgActive() == 1)
                .allMatch(rule -> ruleMatcher.matches(
                        rule.getDesCriterionType(),
                        fileName,
                        rule.getDesValue(),
                        rule.getNumStartingPosition(),
                        rule.getNumEndingPosition()));
    }
}
