package com.concil.edi.consumer;

import com.concil.edi.commons.dto.FileTransferMessageDTO;
import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.entity.Server;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.ServerType;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.TransactionType;
import com.concil.edi.commons.repository.FileOriginClientsRepository;
import com.concil.edi.commons.repository.FileOriginRepository;
import com.concil.edi.commons.repository.ServerPathRepository;
import com.concil.edi.consumer.listener.FileTransferListener;
import com.concil.edi.consumer.service.CustomerIdentificationService;
import com.concil.edi.consumer.service.FileDownloadService;
import com.concil.edi.consumer.service.FileUploadService;
import com.concil.edi.consumer.service.LayoutIdentificationService;
import com.concil.edi.consumer.service.RemoveOriginService;
import com.concil.edi.consumer.service.StatusUpdateService;
import com.concil.edi.consumer.service.ProcessingSplitService;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for the Remove File Origin feature.
 *
 * Validates correctness properties defined in design.md:
 * - Propriedade 1: Validação de integridade determina o fluxo
 * - Propriedade 2: Remoção bem-sucedida resulta em CONCLUIDO
 * - Propriedade 3: Falha na remoção registra erro com marcador
 * - Propriedade 4: Detecção de remoção pendente pula a transferência
 * - Propriedade 5: Limite de tentativas encerra o processamento
 */
public class RemoveFileOriginPropertyTest {

    private FileDownloadService fileDownloadService;
    private FileUploadService fileUploadService;
    private StatusUpdateService statusUpdateService;
    private FileOriginRepository fileOriginRepository;
    private ServerPathRepository serverPathRepository;
    private LayoutIdentificationService layoutIdentificationService;
    private CustomerIdentificationService customerIdentificationService;
    private FileOriginClientsRepository fileOriginClientsRepository;
    private RemoveOriginService removeOriginService;
    private ProcessingSplitService processingSplitService;
    private FileTransferListener listener;

    @BeforeTry
    void setup() {
        fileDownloadService = mock(FileDownloadService.class);
        fileUploadService = mock(FileUploadService.class);
        statusUpdateService = mock(StatusUpdateService.class);
        fileOriginRepository = mock(FileOriginRepository.class);
        serverPathRepository = mock(ServerPathRepository.class);
        layoutIdentificationService = mock(LayoutIdentificationService.class);
        customerIdentificationService = mock(CustomerIdentificationService.class);
        fileOriginClientsRepository = mock(FileOriginClientsRepository.class);
        removeOriginService = mock(RemoveOriginService.class);
        processingSplitService = mock(ProcessingSplitService.class);
        listener = new FileTransferListener(
                fileDownloadService, fileUploadService, statusUpdateService,
                fileOriginRepository, serverPathRepository,
                layoutIdentificationService, customerIdentificationService,
                fileOriginClientsRepository, removeOriginService, processingSplitService);
    }

    // -------------------------------------------------------------------------
    // Propriedade 1: Validação de integridade determina o fluxo
    // Feature: remove-file-origin, Propriedade 1: Validação de integridade determina o fluxo
    // Valida: Requisitos 1.1, 1.2, 1.3, 1.4
    // -------------------------------------------------------------------------
    @Property(tries = 100)
    void integridadeDeterminaFluxo(
            @ForAll @LongRange(min = 1, max = 100_000) long tamanhoDestino,
            @ForAll @LongRange(min = 1, max = 100_000) long numFileSize
    ) throws Exception {
        FileOrigin fileOrigin = createFileOrigin(1L, "arquivo.csv", 0, 5, Status.EM_ESPERA, null);
        FileTransferMessageDTO message = new FileTransferMessageDTO(1L, "arquivo.csv", 10L, 20L, numFileSize);

        ServerPath originPath = createServerPath(10L, createServer(1L, "sftp-origin", ServerType.SFTP), "/origin");
        ServerPath destPath = createServerPath(20L, createServer(2L, "bucket/prefix", ServerType.S3), "bucket/prefix");

        lenient().when(fileOriginRepository.findById(anyLong())).thenReturn(Optional.of(fileOrigin));
        lenient().when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        lenient().when(serverPathRepository.findWithServerByIdtSeverPath(20L)).thenReturn(Optional.of(destPath));
        lenient().when(fileDownloadService.openInputStream(anyLong(), anyString()))
                .thenAnswer(inv -> new ByteArrayInputStream("conteudo".getBytes()));
        lenient().when(layoutIdentificationService.identifyLayout(any(InputStream.class), anyString(), anyLong()))
                .thenReturn(1L);
        lenient().when(customerIdentificationService.identifyCustomers(any(byte[].class), anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());
        lenient().when(fileUploadService.getS3ObjectSize(anyString(), anyString())).thenReturn(tamanhoDestino);

        // Act
        listener.handleFileTransfer(message);

        if (tamanhoDestino == numFileSize) {
            // Propriedade 1: tamanhos iguais → removeFile deve ser chamado
            verify(removeOriginService, atLeastOnce()).removeFile(anyLong(), anyString());
            verify(statusUpdateService, never()).updateStatusWithError(eq(1L), eq(Status.ERRO),
                    eq("Erro de integridade: tamanho do arquivo no destino difere do esperado"));
        } else {
            // Propriedade 1: tamanhos divergem → removeFile nunca deve ser chamado
            verify(removeOriginService, never()).removeFile(anyLong(), anyString());
            verify(statusUpdateService).updateStatusWithError(eq(1L), eq(Status.ERRO),
                    eq("Erro de integridade: tamanho do arquivo no destino difere do esperado"));
        }
    }

    // -------------------------------------------------------------------------
    // Propriedade 2: Remoção bem-sucedida resulta em CONCLUIDO
    // Feature: remove-file-origin, Propriedade 2: Remoção bem-sucedida resulta em CONCLUIDO
    // Valida: Requisitos 2.1, 2.2
    // -------------------------------------------------------------------------
    @Property(tries = 100)
    void remocaoBemSucedidaResultaEmConcluido(
            @ForAll @LongRange(min = 1, max = 100_000) long fileSize,
            @ForAll @IntRange(min = 0, max = 3) int numRetry
    ) throws Exception {
        // numRetry < maxRetry=5, tamanhos iguais, SFTP sem exceção
        FileOrigin fileOrigin = createFileOrigin(1L, "arquivo.csv", numRetry, 5, Status.EM_ESPERA, null);
        FileTransferMessageDTO message = new FileTransferMessageDTO(1L, "arquivo.csv", 10L, 20L, fileSize);

        ServerPath originPath = createServerPath(10L, createServer(1L, "sftp-origin", ServerType.SFTP), "/origin");
        ServerPath destPath = createServerPath(20L, createServer(2L, "bucket/prefix", ServerType.S3), "bucket/prefix");

        lenient().when(fileOriginRepository.findById(anyLong())).thenReturn(Optional.of(fileOrigin));
        lenient().when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        lenient().when(serverPathRepository.findWithServerByIdtSeverPath(20L)).thenReturn(Optional.of(destPath));
        lenient().when(fileDownloadService.openInputStream(anyLong(), anyString()))
                .thenAnswer(inv -> new ByteArrayInputStream("conteudo".getBytes()));
        lenient().when(layoutIdentificationService.identifyLayout(any(InputStream.class), anyString(), anyLong()))
                .thenReturn(1L);
        lenient().when(customerIdentificationService.identifyCustomers(any(byte[].class), anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());
        lenient().when(fileUploadService.getS3ObjectSize(anyString(), anyString())).thenReturn(fileSize);
        doNothing().when(removeOriginService).removeFile(anyLong(), anyString());

        // Act
        listener.handleFileTransfer(message);

        // Propriedade 2: status final deve ser CONCLUIDO
        verify(statusUpdateService).updateStatus(1L, Status.CONCLUIDO);
        verify(statusUpdateService, never()).updateStatusWithError(eq(1L), eq(Status.ERRO),
                argThat(msg -> msg != null && msg.contains("REMOVE_ORIGIN_FILE_ERROR")));
    }

    // -------------------------------------------------------------------------
    // Propriedade 3: Falha na remoção registra erro com marcador
    // Feature: remove-file-origin, Propriedade 3: Falha na remoção registra erro com marcador
    // Valida: Requisitos 2.3, 2.4, 5.4
    // -------------------------------------------------------------------------
    @Property(tries = 100)
    void falhaRemocaoRegistraErroComMarcador(
            @ForAll("mensagensDeErro") String mensagemErro,
            @ForAll @LongRange(min = 1, max = 100_000) long fileSize,
            @ForAll @IntRange(min = 0, max = 3) int numRetry
    ) throws Exception {
        // numRetry < maxRetry=5 → NACK esperado após falha
        FileOrigin fileOrigin = createFileOrigin(1L, "arquivo.csv", numRetry, 5, Status.EM_ESPERA, null);
        FileTransferMessageDTO message = new FileTransferMessageDTO(1L, "arquivo.csv", 10L, 20L, fileSize);

        ServerPath originPath = createServerPath(10L, createServer(1L, "sftp-origin", ServerType.SFTP), "/origin");
        ServerPath destPath = createServerPath(20L, createServer(2L, "bucket/prefix", ServerType.S3), "bucket/prefix");

        lenient().when(fileOriginRepository.findById(anyLong())).thenReturn(Optional.of(fileOrigin));
        lenient().when(serverPathRepository.findWithServerByIdtSeverPath(10L)).thenReturn(Optional.of(originPath));
        lenient().when(serverPathRepository.findWithServerByIdtSeverPath(20L)).thenReturn(Optional.of(destPath));
        lenient().when(fileDownloadService.openInputStream(anyLong(), anyString()))
                .thenAnswer(inv -> new ByteArrayInputStream("conteudo".getBytes()));
        lenient().when(layoutIdentificationService.identifyLayout(any(InputStream.class), anyString(), anyLong()))
                .thenReturn(1L);
        lenient().when(customerIdentificationService.identifyCustomers(any(byte[].class), anyString(), anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());
        lenient().when(fileUploadService.getS3ObjectSize(anyString(), anyString())).thenReturn(fileSize);
        doThrow(new RuntimeException(mensagemErro)).when(removeOriginService).removeFile(anyLong(), anyString());

        // Act: deve lançar ListenerExecutionFailedException (NACK) pois numRetry < maxRetry
        assertThatThrownBy(() -> listener.handleFileTransfer(message))
                .isInstanceOf(ListenerExecutionFailedException.class);

        // Propriedade 3: des_message_error deve conter REMOVE_ORIGIN_FILE_ERROR
        verify(statusUpdateService).updateStatusWithError(
                eq(1L),
                eq(Status.ERRO),
                argThat(msg -> msg != null && msg.contains("REMOVE_ORIGIN_FILE_ERROR"))
        );
        // desStep deve ser atualizado para COLETA via fileOriginRepository.save
        verify(fileOriginRepository, atLeastOnce()).save(argThat(fo -> fo.getDesStep() == Step.COLETA));
    }

    // -------------------------------------------------------------------------
    // Propriedade 4: Detecção de remoção pendente pula a transferência
    // Feature: remove-file-origin, Propriedade 4: Detecção de remoção pendente pula a transferência
    // Valida: Requisitos 3.1, 3.2, 3.3
    // -------------------------------------------------------------------------
    @Property(tries = 100)
    void remocaoPendentePulaTransferencia(
            @ForAll("sufixosDeErro") String sufixoErro,
            @ForAll @IntRange(min = 0, max = 3) int numRetry
    ) throws Exception {
        // FileOrigin com status=ERRO e desMessageError contendo REMOVE_ORIGIN_FILE_ERROR
        String errorMsg = "REMOVE_ORIGIN_FILE_ERROR" + sufixoErro;
        FileOrigin fileOrigin = createFileOrigin(1L, "arquivo.csv", numRetry, 5, Status.ERRO, errorMsg);
        FileTransferMessageDTO message = new FileTransferMessageDTO(1L, "arquivo.csv", 10L, 20L, 1024L);

        lenient().when(fileOriginRepository.findById(anyLong())).thenReturn(Optional.of(fileOrigin));
        doNothing().when(removeOriginService).removeFile(anyLong(), anyString());

        // Act
        listener.handleFileTransfer(message);

        // Propriedade 4: serviços de transferência nunca devem ser invocados
        verify(fileDownloadService, never()).openInputStream(anyLong(), anyString());
        verify(layoutIdentificationService, never()).identifyLayout(any(), anyString(), anyLong());
        verify(customerIdentificationService, never()).identifyCustomers(any(), anyString(), anyLong(), anyLong());
        verify(fileUploadService, never()).uploadToS3(any(), anyString(), anyString(), anyLong());
        verify(fileUploadService, never()).uploadToSftp(any(), any(), anyString());
    }

    // -------------------------------------------------------------------------
    // Propriedade 5: Limite de tentativas encerra o processamento
    // Feature: remove-file-origin, Propriedade 5: Limite de tentativas encerra o processamento
    // Valida: Requisitos 4.2, 4.3, 4.4
    // -------------------------------------------------------------------------
    @Property(tries = 100)
    void limiteTentativasEncerraSemRemover(
            @ForAll @IntRange(min = 0, max = 10) int numRetry,
            @ForAll @IntRange(min = 0, max = 10) int maxRetry
    ) throws Exception {
        Assume.that(numRetry >= maxRetry);

        // FileOrigin com remoção pendente e numRetry >= maxRetry
        String errorMsg = "REMOVE_ORIGIN_FILE_ERROR. arquivo arquivo.csv, motivo: timeout";
        FileOrigin fileOrigin = createFileOrigin(1L, "arquivo.csv", numRetry, maxRetry, Status.ERRO, errorMsg);
        FileTransferMessageDTO message = new FileTransferMessageDTO(1L, "arquivo.csv", 10L, 20L, 1024L);

        lenient().when(fileOriginRepository.findById(anyLong())).thenReturn(Optional.of(fileOrigin));

        // Act: não deve lançar exceção (ACK — limite atingido)
        listener.handleFileTransfer(message);

        // Propriedade 5: removeFile nunca deve ser chamado
        verify(removeOriginService, never()).removeFile(anyLong(), anyString());

        // Propriedade 5: status final deve ser ERRO com mensagem contendo REMOVE_ORIGIN_FILE_ERROR
        verify(statusUpdateService).updateStatusWithError(
                eq(1L),
                eq(Status.ERRO),
                argThat(msg -> msg != null && msg.contains("REMOVE_ORIGIN_FILE_ERROR"))
        );
    }

    // -------------------------------------------------------------------------
    // Providers
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<String> mensagensDeErro() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100);
    }

    @Provide
    Arbitrary<String> sufixosDeErro() {
        return Arbitraries.of(
                "",
                ". arquivo test.csv, motivo: timeout",
                ". arquivo test.csv, motivo: connection refused",
                " extra info"
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private FileOrigin createFileOrigin(Long id, String filename, int numRetry, int maxRetry,
                                        Status status, String errorMsg) {
        FileOrigin fo = new FileOrigin();
        fo.setIdtFileOrigin(id);
        fo.setIdtAcquirer(1L);
        fo.setIdtLayout(1L);
        fo.setDesFileName(filename);
        fo.setNumFileSize(1024L);
        fo.setDesFileType(FileType.CSV);
        fo.setDesStep(Step.COLETA);
        fo.setDesStatus(status);
        fo.setDesMessageError(errorMsg);
        fo.setDesTransactionType(TransactionType.COMPLETO);
        fo.setDatTimestampFile(new Timestamp(System.currentTimeMillis()));
        fo.setIdtSeverPathsInOut(1L);
        fo.setDatCreation(new Date());
        fo.setFlgActive(1);
        fo.setNumRetry(numRetry);
        fo.setMaxRetry(maxRetry);
        return fo;
    }

    private Server createServer(Long id, String codServer, ServerType type) {
        Server s = new Server();
        s.setIdtServer(id);
        s.setCodServer(codServer);
        s.setDesServerType(type);
        s.setCodVault("VAULT_" + id);
        s.setDesVaultSecret("secret/" + id);
        s.setFlgActive(1);
        s.setDatCreation(new Date());
        return s;
    }

    private ServerPath createServerPath(Long id, Server server, String path) {
        ServerPath sp = new ServerPath();
        sp.setIdtSeverPath(id);
        sp.setServer(server);
        sp.setDesPath(path);
        sp.setIdtAcquirer(1L);
        sp.setFlgActive(1);
        sp.setDatCreation(new Date());
        return sp;
    }
}
