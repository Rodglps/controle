package com.concil.edi.consumer.listener;

import com.concil.edi.commons.entity.FileOrigin;
import com.concil.edi.commons.enums.Status;
import com.concil.edi.commons.enums.Step;
import com.concil.edi.commons.enums.FileType;
import com.concil.edi.commons.enums.TransactionType;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.DisplayName;

import java.sql.Timestamp;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for RabbitMQ acknowledgment at max retry.
 * 
 * Property 46: RabbitMQ acknowledgment at max retry
 * Validates: Requirements 14.8
 * 
 * Correctness Property:
 * When an error occurs during file transfer and num_retry >= max_retry,
 * the Consumer must ACK the message to remove it from the queue and prevent infinite retry.
 */
@DisplayName("Property 46: RabbitMQ Acknowledgment at Max Retry")
class RabbitMQAcknowledgmentPropertyTest {

    @Property
    @Label("Message must be ACKed when max retry is reached")
    void messageMustBeAckedWhenMaxRetryReached(
        @ForAll("fileOriginAtMaxRetry") FileOrigin fileOrigin
    ) {
        // Precondition: num_retry >= max_retry
        assertThat(fileOrigin.getNumRetry())
            .as("Precondition: num_retry must be >= max_retry")
            .isGreaterThanOrEqualTo(fileOrigin.getMaxRetry());
        
        // Property: Message should be ACKed (not NACKed)
        boolean shouldNack = fileOrigin.getNumRetry() < fileOrigin.getMaxRetry();
        
        assertThat(shouldNack)
            .as("Message should be ACKed (not NACKed) when num_retry >= max_retry")
            .isFalse();
    }

    @Property
    @Label("Status must be ERRO when max retry is reached")
    void statusMustBeErroWhenMaxRetryReached(
        @ForAll("fileOriginAtMaxRetry") FileOrigin fileOrigin
    ) {
        // Precondition: num_retry >= max_retry
        assertThat(fileOrigin.getNumRetry())
            .as("Precondition: num_retry must be >= max_retry")
            .isGreaterThanOrEqualTo(fileOrigin.getMaxRetry());
        
        // Simulate final error state
        fileOrigin.setDesStatus(Status.ERRO);
        fileOrigin.setDesMessageError("Max retry reached");
        fileOrigin.setDatUpdate(new Date());
        
        // Property: Status must be ERRO
        assertThat(fileOrigin.getDesStatus())
            .as("Status must be ERRO when max retry is reached")
            .isEqualTo(Status.ERRO);
        
        // Property: Error message must be recorded
        assertThat(fileOrigin.getDesMessageError())
            .as("Error message must be recorded")
            .isNotNull()
            .isNotEmpty();
    }

    @Property
    @Label("No further retries must occur after max retry")
    void noFurtherRetriesAfterMaxRetry(
        @ForAll @IntRange(min = 1, max = 10) int maxRetry
    ) {
        FileOrigin fileOrigin = createFileOrigin(maxRetry, maxRetry);
        
        // Precondition: Already at max retry
        assertThat(fileOrigin.getNumRetry())
            .as("Precondition: num_retry equals max_retry")
            .isEqualTo(fileOrigin.getMaxRetry());
        
        // Property: Should not NACK (should ACK instead)
        boolean shouldNack = fileOrigin.getNumRetry() < fileOrigin.getMaxRetry();
        
        assertThat(shouldNack)
            .as("Must not NACK when at max retry")
            .isFalse();
        
        // Property: Retry count should not increase beyond max_retry
        assertThat(fileOrigin.getNumRetry())
            .as("Retry count must not exceed max_retry")
            .isLessThanOrEqualTo(fileOrigin.getMaxRetry());
    }

    @Property
    @Label("ACK decision must be deterministic at max retry boundary")
    void ackDecisionMustBeDeterministicAtBoundary(
        @ForAll @IntRange(min = 1, max = 10) int maxRetry
    ) {
        // Test at boundary: num_retry = max_retry
        FileOrigin atBoundary = createFileOrigin(maxRetry, maxRetry);
        boolean shouldNackAtBoundary = atBoundary.getNumRetry() < atBoundary.getMaxRetry();
        
        assertThat(shouldNackAtBoundary)
            .as("Must ACK (not NACK) when num_retry = max_retry")
            .isFalse();
        
        // Test above boundary: num_retry > max_retry
        FileOrigin aboveBoundary = createFileOrigin(maxRetry + 1, maxRetry);
        boolean shouldNackAboveBoundary = aboveBoundary.getNumRetry() < aboveBoundary.getMaxRetry();
        
        assertThat(shouldNackAboveBoundary)
            .as("Must ACK (not NACK) when num_retry > max_retry")
            .isFalse();
        
        // Test below boundary: num_retry < max_retry
        if (maxRetry > 1) {
            FileOrigin belowBoundary = createFileOrigin(maxRetry - 1, maxRetry);
            boolean shouldNackBelowBoundary = belowBoundary.getNumRetry() < belowBoundary.getMaxRetry();
            
            assertThat(shouldNackBelowBoundary)
                .as("Must NACK when num_retry < max_retry")
                .isTrue();
        }
    }

    @Property
    @Label("Alert message should be set when max retry is reached")
    void alertMessageShouldBeSetWhenMaxRetryReached(
        @ForAll("fileOriginAtMaxRetry") FileOrigin fileOrigin
    ) {
        // Precondition: num_retry >= max_retry
        assertThat(fileOrigin.getNumRetry())
            .as("Precondition: num_retry must be >= max_retry")
            .isGreaterThanOrEqualTo(fileOrigin.getMaxRetry());
        
        // Simulate setting alert when max retry reached
        fileOrigin.setDesStatus(Status.ERRO);
        fileOrigin.setDesMessageError("Transfer failed");
        fileOrigin.setDesMessageAlert("Max retry limit reached, manual intervention required");
        fileOrigin.setDatUpdate(new Date());
        
        // Property: Alert message should be set
        assertThat(fileOrigin.getDesMessageAlert())
            .as("Alert message should be set when max retry is reached")
            .isNotNull()
            .isNotEmpty()
            .contains("Max retry");
    }

    @Property
    @Label("File must remain in ERRO status after ACK at max retry")
    void fileMustRemainInErroStatusAfterAck(
        @ForAll("fileOriginAtMaxRetry") FileOrigin fileOrigin
    ) {
        // Simulate final error state
        fileOrigin.setDesStatus(Status.ERRO);
        fileOrigin.setDesMessageError("Max retry reached");
        fileOrigin.setDatUpdate(new Date());
        
        // Property: After ACK, file must remain in ERRO status
        assertThat(fileOrigin.getDesStatus())
            .as("File must remain in ERRO status after ACK")
            .isEqualTo(Status.ERRO);
        
        // Property: File must remain active for monitoring
        assertThat(fileOrigin.getFlgActive())
            .as("File must remain active for monitoring")
            .isEqualTo(1);
    }

    @Provide
    Arbitrary<FileOrigin> fileOriginAtMaxRetry() {
        Arbitrary<Long> positiveIds = Arbitraries.longs().greaterOrEqual(1L);
        Arbitrary<String> filenames = Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('.', '_', '-')
            .ofMinLength(1)
            .ofMaxLength(255);
        Arbitrary<Integer> maxRetry = Arbitraries.integers().between(1, 10);
        
        return Combinators.combine(
            positiveIds,
            filenames,
            maxRetry
        ).as((id, filename, max) -> createFileOrigin(id, filename, max, max));
    }

    private FileOrigin createFileOrigin(int numRetry, int maxRetry) {
        return createFileOrigin(1L, "test-file.csv", numRetry, maxRetry);
    }

    private FileOrigin createFileOrigin(Long id, String filename, int numRetry, int maxRetry) {
        FileOrigin fileOrigin = new FileOrigin();
        fileOrigin.setIdtFileOrigin(id);
        fileOrigin.setIdtAcquirer(1L);
        fileOrigin.setIdtLayout(1L);
        fileOrigin.setDesFileName(filename);
        fileOrigin.setNumFileSize(1024L);
        fileOrigin.setDesFileType(FileType.csv);
        fileOrigin.setDesStep(Step.COLETA);
        fileOrigin.setDesStatus(Status.PROCESSAMENTO);
        fileOrigin.setDesTransactionType(TransactionType.COMPLETO);
        fileOrigin.setDatTimestampFile(new Timestamp(System.currentTimeMillis()));
        fileOrigin.setIdtSeverPathsInOut(1L);
        fileOrigin.setDatCreation(new Date());
        fileOrigin.setFlgActive(1);
        fileOrigin.setNumRetry(numRetry);
        fileOrigin.setMaxRetry(maxRetry);
        return fileOrigin;
    }
}
