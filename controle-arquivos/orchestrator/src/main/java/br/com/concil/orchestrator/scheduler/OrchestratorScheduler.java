package br.com.concil.orchestrator.scheduler;

import br.com.concil.common.dto.FileMessage;
import br.com.concil.common.entity.*;
import br.com.concil.common.repository.*;
import br.com.concil.orchestrator.config.RabbitConfig;
import br.com.concil.orchestrator.sftp.SftpClient;
import br.com.concil.orchestrator.sftp.SftpFileEntry;
import br.com.concil.orchestrator.vault.VaultCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorScheduler {

    private static final String JOB_NAME = "ORCHESTRATOR-FILE-COLLECTOR";
    private static final int BATCH_SIZE  = 50;

    private final SeverPathInOutRepository severPathInOutRepository;
    private final FileOriginRepository fileOriginRepository;
    private final JobConcurrencyControlRepository jobConcurrencyControlRepository;
    private final VaultCredentialService vaultCredentialService;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelayString = "${orchestrator.schedule.delay-ms:60000}")
    @Transactional
    public void collectFiles() {
        if (!acquireLock()) {
            log.info("Job {} já está em execução, pulando ciclo", JOB_NAME);
            return;
        }
        try {
            log.info("Iniciando coleta de arquivos SFTP");
            List<SeverPathInOut> paths = severPathInOutRepository.findActiveOriginSftpPaths();
            log.info("Encontrados {} mapeamentos SFTP ativos", paths.size());

            for (SeverPathInOut mapping : paths) {
                processMapping(mapping);
            }
        } catch (Exception e) {
            log.error("Erro durante coleta de arquivos: {}", e.getMessage(), e);
        } finally {
            releaseLock();
        }
    }

    private void processMapping(SeverPathInOut mapping) {
        SeverPath origin = mapping.getOrigin();
        Server server = origin.getServer();

        Map<String, String> creds = vaultCredentialService.getCredentials(
                server.getCodVault(), server.getDesVaultSecret());

        try (SftpClient sftp = new SftpClient(
                creds.get("host"),
                Integer.parseInt(creds.get("port")),
                creds.get("username"),
                creds.get("password"))) {

            List<SftpFileEntry> files = sftp.listFiles(origin.getDesPath());
            log.info("Encontrados {} arquivos em {}", files.size(), origin.getDesPath());

            List<FileMessage.FileEntry> batch = new ArrayList<>();

            for (SftpFileEntry file : files) {
                Optional<FileOrigin> existing = fileOriginRepository
                        .findByDesFileNameAndIdtAcquirerAndDatTimestampFileAndFlgActive(
                                file.getFileName(),
                                origin.getIdtAcquirer(),
                                toLocalDateTime(file.getLastModifiedMillis()),
                                1);

                if (existing.isPresent()) {
                    log.debug("Arquivo já registrado, ignorando: {}", file.getFileName());
                    continue;
                }

                FileOrigin fileOrigin = registerFileOrigin(file, origin, mapping);
                batch.add(FileMessage.FileEntry.builder()
                        .idtFileOrigin(fileOrigin.getId())
                        .fileName(file.getFileName())
                        .remotePath(file.getRemotePath())
                        .build());

                if (batch.size() >= BATCH_SIZE) {
                    publishBatch(mapping, origin, batch);
                    batch = new ArrayList<>();
                }
            }

            if (!batch.isEmpty()) {
                publishBatch(mapping, origin, batch);
            }

        } catch (Exception e) {
            log.error("Erro ao processar SFTP {}:{} - {}", server.getCodServer(), origin.getDesPath(), e.getMessage(), e);
        }
    }

    private FileOrigin registerFileOrigin(SftpFileEntry file, SeverPath origin, SeverPathInOut mapping) {
        FileOrigin fo = new FileOrigin();
        fo.setDesFileName(file.getFileName());
        fo.setIdtAcquirer(origin.getIdtAcquirer());
        fo.setNumFileSize(file.getSizeBytes());
        fo.setDatTimestampFile(toLocalDateTime(file.getLastModifiedMillis()));
        fo.setIdtSeverPathsInOut(mapping.getId());
        fo.setDesTransactionType("COMPLETO"); // será atualizado pelo processador após identificação
        fo.setDatCreation(LocalDate.now());
        fo.setFlgActive(1);
        return fileOriginRepository.save(fo);
    }

    private void publishBatch(SeverPathInOut mapping, SeverPath origin, List<FileMessage.FileEntry> batch) {
        FileMessage message = FileMessage.builder()
                .idtSeverPathsInOut(mapping.getId())
                .idtAcquirer(origin.getIdtAcquirer())
                .files(batch)
                .build();

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        log.info("Publicado batch com {} arquivos na fila", batch.size());
    }

    private boolean acquireLock() {
        Optional<JobConcurrencyControl> job = jobConcurrencyControlRepository
                .findByDesJobNameAndFlgActive(JOB_NAME, 1);

        if (job.isPresent() && "RUNNING".equals(job.get().getDesStatus())) {
            return false;
        }
        jobConcurrencyControlRepository.updateStatus(JOB_NAME, "RUNNING");
        return true;
    }

    private void releaseLock() {
        jobConcurrencyControlRepository.updateStatus(JOB_NAME, "COMPLETED");
    }

    private LocalDateTime toLocalDateTime(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
