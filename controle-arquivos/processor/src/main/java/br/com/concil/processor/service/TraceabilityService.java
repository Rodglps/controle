package br.com.concil.processor.service;

import br.com.concil.common.entity.FileOriginClientProcessing;
import br.com.concil.common.repository.FileOriginClientProcessingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceabilityService {

    private final FileOriginClientProcessingRepository processingRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FileOriginClientProcessing startStep(Long idtFileOriginClient, String step) {
        FileOriginClientProcessing proc = new FileOriginClientProcessing();
        proc.setIdtFileOriginClient(idtFileOriginClient);
        proc.setDesStep(step);
        proc.setDesStatus("PROCESSAMENTO");
        proc.setDatStepStart(LocalDate.now());
        proc.setDatCreation(LocalDate.now());
        proc.setFlgActive(1);
        return processingRepository.save(proc);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeStep(Long processingId, String additionalInfo) {
        processingRepository.findById(processingId).ifPresent(proc -> {
            proc.setDesStatus("CONCLUIDO");
            proc.setDatStepEnd(LocalDate.now());
            proc.setJsnAdditionalInfo(additionalInfo);
            proc.setDatUpdate(LocalDate.now());
            processingRepository.save(proc);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failStep(Long processingId, String errorMessage) {
        processingRepository.findById(processingId).ifPresent(proc -> {
            proc.setDesStatus("ERRO");
            proc.setDatStepEnd(LocalDate.now());
            proc.setDesMessageError(truncate(errorMessage, 4000));
            proc.setDatUpdate(LocalDate.now());
            processingRepository.save(proc);
        });
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
