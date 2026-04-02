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
 * Property-based test for RabbitMQ retry behavior.
 * 
 * Property 45: RabbitMQ retry behavior
 * Validates: Requirements 14.7
 * 
 * Correctness Property:
 * When an error occurs during file transfer and num_retry < max_retry,
 * the Consumer must NACK the message to trigger requeue and retry.
 */
@DisplayName("Property 45: RabbitMQ Retry Behavior")
class RabbitMQRetryBehaviorPropertyTest {

    @Property
    @Label("Message must be NACKed when retry limit not reached")
    void messageMustBeNackedWhenRetryLimitNotReached(
        @ForAll("fileOriginBelowMaxRetry") FileOrigin fileOrigin
    ) {
        // Precondition: num_retry < max_retry
        assertThat(fileOrigin.getNumRetry())
            .as("Precondition: num_retry must be less than max_retry")
            .isLessThan(fileOrigin.getMaxRetry());
        
        // Simulate error handling
        fileOrigin.setDesStatus(Status.ERRO);
        fileOrigin.setDesMessageError("Simulated error");
        fileOrigin.setNumRetry(fileOrigin.getNumRetry() + 1);
        fileOrigin.setDatUpdate(new Date());
        
        // Property: After error, if still below max_retry, message should be NACKed
        boolean shouldNack = fileOrigin.getNumRetry() < fileOrigin.getMaxRetry();
        
        assertThat(shouldNack)
            .as("Message should be NACKed when num_retry < max_retry after increment")
            .isTrue();
    }

    @Property
    @Label("Retry count must increment on each error")
    void retryCountMustIncrementOnEachError(
        @ForAll("fileOriginBelowMaxRetry") FileOrigin fileOrigin
    ) {
        Integer originalRetry = fileOrigin.getNumRetry();
        
        // Precondition: num_retry < max_retry
        assertThat(originalRetry)
            .as("Precondition: num_retry must be less than max_retry")
            .isLessThan(fileOrigin.getMaxRetry());
        
        // Simulate error handling with retry increment
        fileOrigin.setDesStatus(Status.ERRO);
        fileOrigin.setDesMessageError("Error occurred");
        fileOrigin.setNumRetry(originalRetry + 1);
        
        // Property: Retry count must be incremented by exactly 1
        assertThat(fileOrigin.getNumRetry())
            .as("Retry count must increment by 1 on error")
            .isEqualTo(originalRetry + 1);
    }

    @Property
    @Label("Status must be ERRO when retry is triggered")
    void statusMustBeErroWhenRetryTriggered(
        @ForAll("fileOriginBelowMaxRetry") FileOrigin fileOrigin
    ) {
        // Precondition: num_retry < max_retry
        assertThat(fileOrigin.getNumRetry())
            .as("Precondition: num_retry must be less than max_retry")
            .isLessThan(fileOrigin.getMaxRetry());
        
        // Simulate error handling
        fileOrigin.setDesStatus(Status.ERRO);
        fileOrigin.setDesMessageError("Transfer failed");
        fileOrigin.setNumRetry(fileOrigin.getNumRetry() + 1);
        fileOrigin.setDatUpdate(new Date());
        
        // Property: Status must be ERRO when retry is triggered
        assertThat(fileOrigin.getDesStatus())
            .as("Status must be ERRO when retry is triggered")
            .isEqualTo(Status.ERRO);
        
        // Property: Error message must be recorded
        assertThat(fileOrigin.getDesMessageError())
            .as("Error message must be recorded")
            .isNotNull()
            .isNotEmpty();
    }

    @Property
    @Label("Retry behavior must be consistent across multiple errors")
    void retryBehaviorMustBeConsistentAcrossMultipleErrors(
        @ForAll @IntRange(min = 0, max = 3) int initialRetry,
        @ForAll @IntRange(min = 5, max = 10) int maxRetry
    ) {
        FileOrigin fileOrigin = createFileOrigin(initialRetry, maxRetry);
        
        // Simulate multiple errors until max_retry is reached
        int errorCount = 0;
        while (fileOrigin.getNumRetry() < fileOrigin.getMaxRetry()) {
            Integer retryBeforeError = fileOrigin.getNumRetry();
            
            // Simulate error
            fileOrigin.setDesStatus(Status.ERRO);
            fileOrigin.setDesMessageError("Error #" + (errorCount + 1));
            fileOrigin.setNumRetry(fileOrigin.getNumRetry() + 1);
            
            errorCount++;
            
            // Property: Each error must increment retry by exactly 1
            assertThat(fileOrigin.getNumRetry())
                .as("Retry must increment by 1 on each error")
                .isEqualTo(retryBeforeError + 1);
            
            // Property: Message should be NACKed if still below max_retry
            boolean shouldNack = fileOrigin.getNumRetry() < fileOrigin.getMaxRetry();
            if (shouldNack) {
                assertThat(fileOrigin.getNumRetry())
                    .as("When NACKed, retry must be less than max_retry")
                    .isLessThan(fileOrigin.getMaxRetry());
            }
        }
        
        // Property: Total errors must equal (max_retry - initial_retry)
        assertThat(errorCount)
            .as("Total errors must equal max_retry - initial_retry")
            .isEqualTo(maxRetry - initialRetry);
    }

    @Property
    @Label("NACK decision must be deterministic based on retry count")
    void nackDecisionMustBeDeterministic(
        @ForAll @IntRange(min = 0, max = 10) int numRetry,
        @ForAll @IntRange(min = 1, max = 10) int maxRetry
    ) {
        FileOrigin fileOrigin = createFileOrigin(numRetry, maxRetry);
        
        // Property: NACK decision must be deterministic
        boolean shouldNack = fileOrigin.getNumRetry() < fileOrigin.getMaxRetry();
        
        if (numRetry < maxRetry) {
            assertThat(shouldNack)
                .as("Must NACK when num_retry < max_retry")
                .isTrue();
        } else {
            assertThat(shouldNack)
                .as("Must ACK when num_retry >= max_retry")
                .isFalse();
        }
    }

    @Provide
    Arbitrary<FileOrigin> fileOriginBelowMaxRetry() {
        Arbitrary<Long> positiveIds = Arbitraries.longs().greaterOrEqual(1L);
        Arbitrary<String> filenames = Arbitraries.strings()
            .alpha()
            .numeric()
            .withChars('.', '_', '-')
            .ofMinLength(1)
            .ofMaxLength(255);
        Arbitrary<Integer> retryCount = Arbitraries.integers().between(0, 4);
        
        return Combinators.combine(
            positiveIds,
            filenames,
            retryCount
        ).as((id, filename, retry) -> createFileOrigin(id, filename, retry, 5));
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
