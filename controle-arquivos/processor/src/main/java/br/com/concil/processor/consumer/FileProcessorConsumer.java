package br.com.concil.processor.consumer;

import br.com.concil.common.dto.FileMessage;
import br.com.concil.common.entity.*;
import br.com.concil.common.repository.*;
import br.com.concil.processor.config.RabbitConfig;
import br.com.concil.processor.identification.CustomerIdentificationService;
import br.com.concil.processor.identification.LayoutIdentificationService;
import br.com.concil.processor.service.TraceabilityService;
import br.com.concil.processor.transfer.S3UploadService;
import br.com.concil.processor.transfer.SftpTransferService;
import br.com.concil.processor.vault.VaultCredentialService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileProcessorConsumer {

    private final SeverPathInOutRepository severPathInOutRepository;
    private final FileOriginRepository fileOriginRepository;
    private final FileOriginClientRepository fileOriginClientRepository;
    private final CustomerIdentificationService customerIdentificationService;
    private final LayoutIdentificationService layoutIdentificationService;
    private final TraceabilityService traceabilityService;
    private final VaultCredentialService vaultCredentialService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @Transactional
    public void process(FileMessage message) {
        log.info("Mensagem recebida: {} arquivos para processar", message.getFiles().size());

        SeverPathInOut mapping = severPathInOutRepository.findById(message.getIdtSeverPathsInOut())
                .orElseThrow(() -> new IllegalStateException("Mapeamento não encontrado: " + message.getIdtSeverPathsInOut()));

        Server originServer = mapping.getOrigin().getServer();
        Map<String, String> originCreds = vaultCredentialService.getCredentials(
                originServer.getCodVault(), originServer.getDesVaultSecret());

        for (FileMessage.FileEntry entry : message.getFiles()) {
            try {
                processFile(entry, message.getIdtAcquirer(), mapping, originCreds);
            } catch (Exception e) {
                log.error("Erro ao processar arquivo {}: {}", entry.getFileName(), e.getMessage(), e);
            }
        }
    }

    private void processFile(FileMessage.FileEntry entry, Long idtAcquirer,
                              SeverPathInOut mapping, Map<String, String> originCreds) throws Exception {

        FileOrigin fileOrigin = fileOriginRepository.findById(entry.getIdtFileOrigin())
                .orElseThrow(() -> new IllegalStateException("FileOrigin não encontrado: " + entry.getIdtFileOrigin()));

        // 1. Criar registro de cliente (sem cliente ainda)
        FileOriginClient fileOriginClient = createFileOriginClient(fileOrigin.getId(), null);

        // 2. Iniciar step COLETA
        FileOriginClientProcessing proc = traceabilityService.startStep(fileOriginClient.getId(), "COLETA");

        try {
            // 3. Conectar ao SFTP de origem e baixar o arquivo via streaming
            JSch jsch = new JSch();
            Session session = jsch.getSession(originCreds.get("username"), originCreds.get("host"),
                    Integer.parseInt(originCreds.getOrDefault("port", "22")));
            session.setPassword(originCreds.get("password"));
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30_000);
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect(10_000);

            try (InputStream fileStream = sftpChannel.get(entry.getRemotePath())) {

                // 4. Identificar layout (pode usar os primeiros 7000 bytes do stream)
                Optional<Layout> layout = layoutIdentificationService.identify(
                        entry.getFileName(), idtAcquirer, fileStream);

                layout.ifPresent(l -> {
                    fileOrigin.setIdtLayout(l.getId());
                    fileOrigin.setDesTransactionType(l.getDesTransactionType());
                    fileOrigin.setDesFileType(l.getDesFileType());
                    fileOrigin.setDatUpdate(LocalDate.now());
                    fileOriginRepository.save(fileOrigin);
                });

                // 5. Identificar cliente pelo nome do arquivo
                Optional<CustomerIdentification> customer = customerIdentificationService.identify(
                        entry.getFileName(), idtAcquirer);

                customer.ifPresent(c -> {
                    fileOriginClient.setIdtClient(c.getIdtClient());
                    fileOriginClient.setDatUpdate(LocalDate.now());
                    fileOriginClientRepository.save(fileOriginClient);
                });

                // 6. Fazer upload para o destino (S3 ou SFTP)
                Server destServer = mapping.getDestination().getServer();
                Map<String, String> destCreds = vaultCredentialService.getCredentials(
                        destServer.getCodVault(), destServer.getDesVaultSecret());

                String destPath = mapping.getDestination().getDesPath() + "/" + entry.getFileName();

                if ("S3".equals(destServer.getDesServerType())) {
                    uploadToS3(fileStream, destPath, destCreds, fileOrigin.getNumFileSize());
                } else if ("SFTP".equals(destServer.getDesServerType())) {
                    uploadToSftp(fileStream, destPath, destCreds);
                } else {
                    throw new UnsupportedOperationException("Tipo de destino não suportado: " + destServer.getDesServerType());
                }

                traceabilityService.completeStep(proc.getId(),
                        objectMapper.writeValueAsString(Map.of(
                                "destino", destPath,
                                "tipoDestino", destServer.getDesServerType(),
                                "layout", layout.map(Layout::getCodLayout).orElse("NAO_IDENTIFICADO"),
                                "cliente", customer.map(c -> c.getIdtClient().toString()).orElse("NAO_IDENTIFICADO")
                        )));

            } finally {
                sftpChannel.disconnect();
                session.disconnect();
            }

        } catch (Exception e) {
            traceabilityService.failStep(proc.getId(), e.getMessage());
            throw e;
        }
    }

    private FileOriginClient createFileOriginClient(Long idtFileOrigin, Long idtClient) {
        FileOriginClient client = new FileOriginClient();
        client.setIdtFileOrigin(idtFileOrigin);
        client.setIdtClient(idtClient);
        client.setDatCreation(LocalDate.now());
        client.setFlgActive(1);
        return fileOriginClientRepository.save(client);
    }

    private void uploadToS3(InputStream stream, String key, Map<String, String> creds, Long size) {
        try (S3UploadService s3 = new S3UploadService(creds)) {
            s3.upload(stream, key, size != null ? size : -1);
        }
    }

    private void uploadToSftp(InputStream stream, String remotePath, Map<String, String> creds) throws Exception {
        try (SftpTransferService sftp = new SftpTransferService(creds)) {
            sftp.upload(stream, remotePath);
        }
    }
}
