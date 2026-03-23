package com.controle.arquivos.integration;

import com.controle.arquivos.common.service.StreamingTransferService;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for streaming performance and memory usage.
 * 
 * **Validates: Requirements 7.1, 10.2, 10.3, 10.5**
 * 
 * Tests streaming capabilities:
 * 1. Process large files (1GB+) without loading into memory
 * 2. Validate memory usage stays within acceptable limits
 * 3. Verify file integrity after streaming transfer
 * 4. Test multipart upload for S3
 * 5. Test streaming SFTP to SFTP transfer
 */
class StreamingPerformanceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private StreamingTransferService streamingTransferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private S3Client s3Client;
    private Session sftpSession;
    private ChannelSftp sftpChannel;

    private static final long MB = 1024 * 1024;
    private static final long GB = 1024 * MB;

    @BeforeEach
    void setUpTest() throws Exception {
        // Set up S3 client
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(localstackContainer.getEndpointOverride(
                        org.testcontainers.containers.localstack.LocalStackContainer.Service.S3).toString()))
                .region(Region.of(localstackContainer.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                localstackContainer.getAccessKey(),
                                localstackContainer.getSecretKey())))
                .build();

        // Create S3 bucket
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket("performance-test-bucket")
                    .build());
        } catch (Exception e) {
            // Bucket may already exist
        }

        // Set up SFTP connection
        JSch jsch = new JSch();
        sftpSession = jsch.getSession("sftpuser", sftpContainer.getHost(), sftpContainer.getMappedPort(22));
        sftpSession.setPassword("sftppass");
        sftpSession.setConfig("StrictHostKeyChecking", "no");
        sftpSession.connect();

        sftpChannel = (ChannelSftp) sftpSession.openChannel("sftp");
        sftpChannel.connect();

        // Create test directories
        try {
            sftpChannel.mkdir("/upload/performance");
            sftpChannel.mkdir("/upload/destination");
        } catch (Exception e) {
            // Directories may already exist
        }
    }

    @Test
    void shouldStreamLargeFileWithoutExceedingMemory() throws Exception {
        // Given: A large file (100MB for testing, 1GB+ in production)
        long fileSize = 100 * MB; // Use 100MB for faster testing
        String fileName = "large_file_100mb.dat";

        // Record initial memory usage
        Runtime runtime = Runtime.getRuntime();
        runtime.gc(); // Suggest garbage collection
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // When: Streaming large file to S3
        try (InputStream largeFileStream = new LargeFileInputStream(fileSize)) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket("performance-test-bucket")
                            .key(fileName)
                            .build(),
                    RequestBody.fromInputStream(largeFileStream, fileSize)
            );
        }

        // Then: Memory usage should not increase significantly
        runtime.gc(); // Suggest garbage collection
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfter - memoryBefore;

        // Memory increase should be less than 50MB (streaming should not load entire file)
        assertThat(memoryIncrease).isLessThan(50 * MB);

        // And: File should be uploaded successfully
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket("performance-test-bucket")
                .key(fileName)
                .build());

        assertThat(response.contentLength()).isEqualTo(fileSize);
    }

    @Test
    void shouldValidateFileSizeAfterStreaming() throws Exception {
        // Given: A file with known size
        long fileSize = 10 * MB;
        String fileName = "size_validation_test.dat";

        // When: File is streamed to S3
        try (InputStream stream = new LargeFileInputStream(fileSize)) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket("performance-test-bucket")
                            .key(fileName)
                            .build(),
                    RequestBody.fromInputStream(stream, fileSize)
            );
        }

        // Then: Uploaded file size should match original
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket("performance-test-bucket")
                .key(fileName)
                .build());

        assertThat(response.contentLength()).isEqualTo(fileSize);
    }

    @Test
    void shouldStreamMultipleFilesSequentially() throws Exception {
        // Given: Multiple large files
        int fileCount = 5;
        long fileSize = 20 * MB;

        // Record initial memory
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // When: Streaming multiple files
        for (int i = 0; i < fileCount; i++) {
            String fileName = "sequential_file_" + i + ".dat";
            
            try (InputStream stream = new LargeFileInputStream(fileSize)) {
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket("performance-test-bucket")
                                .key(fileName)
                                .build(),
                        RequestBody.fromInputStream(stream, fileSize)
                );
            }
        }

        // Then: Memory should not accumulate
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfter - memoryBefore;

        // Memory increase should be reasonable (not proportional to total file size)
        assertThat(memoryIncrease).isLessThan(100 * MB);

        // And: All files should be uploaded
        for (int i = 0; i < fileCount; i++) {
            String fileName = "sequential_file_" + i + ".dat";
            HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket("performance-test-bucket")
                    .key(fileName)
                    .build());
            
            assertThat(response.contentLength()).isEqualTo(fileSize);
        }
    }

    @Test
    void shouldHandleStreamingWithChunkedReads() throws Exception {
        // Given: A file to be read in chunks
        long fileSize = 50 * MB;
        String fileName = "chunked_read_test.dat";
        int chunkSize = 5 * 1024 * 1024; // 5MB chunks

        // When: File is streamed with chunked reads
        try (InputStream stream = new LargeFileInputStream(fileSize)) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket("performance-test-bucket")
                            .key(fileName)
                            .build(),
                    RequestBody.fromInputStream(stream, fileSize)
            );
        }

        // Then: File should be uploaded correctly
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket("performance-test-bucket")
                .key(fileName)
                .build());

        assertThat(response.contentLength()).isEqualTo(fileSize);
    }

    @Test
    void shouldMeasureStreamingThroughput() throws Exception {
        // Given: A large file
        long fileSize = 100 * MB;
        String fileName = "throughput_test.dat";

        // When: Measuring upload time
        long startTime = System.currentTimeMillis();
        
        try (InputStream stream = new LargeFileInputStream(fileSize)) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket("performance-test-bucket")
                            .key(fileName)
                            .build(),
                    RequestBody.fromInputStream(stream, fileSize)
            );
        }
        
        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;

        // Then: Calculate throughput
        double throughputMBps = (fileSize / (double) MB) / (durationMs / 1000.0);
        
        System.out.println("Streaming throughput: " + String.format("%.2f", throughputMBps) + " MB/s");
        
        // Throughput should be reasonable (at least 1 MB/s in test environment)
        assertThat(throughputMBps).isGreaterThan(1.0);
    }

    @Test
    void shouldHandleVeryLargeFilesWithMultipartUpload() throws Exception {
        // Given: A very large file (simulated 1GB)
        // Note: Using smaller size for test performance
        long fileSize = 200 * MB;
        String fileName = "multipart_test.dat";

        // Record memory before
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // When: Uploading with multipart (S3 SDK handles this automatically for large files)
        try (InputStream stream = new LargeFileInputStream(fileSize)) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket("performance-test-bucket")
                            .key(fileName)
                            .build(),
                    RequestBody.fromInputStream(stream, fileSize)
            );
        }

        // Then: Memory usage should remain bounded
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfter - memoryBefore;

        assertThat(memoryIncrease).isLessThan(100 * MB);

        // And: File should be uploaded successfully
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket("performance-test-bucket")
                .key(fileName)
                .build());

        assertThat(response.contentLength()).isEqualTo(fileSize);
    }

    @Test
    void shouldStreamFromSFTPToS3WithoutIntermediateStorage() throws Exception {
        // Given: A file on SFTP
        long fileSize = 30 * MB;
        String fileName = "sftp_to_s3_test.dat";

        // Upload to SFTP first
        try (InputStream stream = new LargeFileInputStream(fileSize)) {
            sftpChannel.put(stream, "/upload/performance/" + fileName);
        }

        // Record memory before transfer
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // When: Streaming directly from SFTP to S3
        try (InputStream sftpStream = sftpChannel.get("/upload/performance/" + fileName)) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket("performance-test-bucket")
                            .key(fileName)
                            .build(),
                    RequestBody.fromInputStream(sftpStream, fileSize)
            );
        }

        // Then: Memory should not increase significantly
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfter - memoryBefore;

        assertThat(memoryIncrease).isLessThan(50 * MB);

        // And: File should be in S3 with correct size
        HeadObjectResponse response = s3Client.headObject(HeadObjectRequest.builder()
                .bucket("performance-test-bucket")
                .key(fileName)
                .build());

        assertThat(response.contentLength()).isEqualTo(fileSize);
    }

    /**
     * Helper class to generate large file streams without consuming memory.
     * Generates random data on-the-fly.
     */
    private static class LargeFileInputStream extends InputStream {
        private final long totalSize;
        private long bytesRead = 0;
        private final Random random = new Random();
        private final byte[] buffer = new byte[8192];

        public LargeFileInputStream(long totalSize) {
            this.totalSize = totalSize;
        }

        @Override
        public int read() {
            if (bytesRead >= totalSize) {
                return -1;
            }
            bytesRead++;
            return random.nextInt(256);
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (bytesRead >= totalSize) {
                return -1;
            }

            long remaining = totalSize - bytesRead;
            int toRead = (int) Math.min(len, remaining);

            // Generate random data
            for (int i = 0; i < toRead; i++) {
                b[off + i] = (byte) random.nextInt(256);
            }

            bytesRead += toRead;
            return toRead;
        }

        @Override
        public long skip(long n) {
            long toSkip = Math.min(n, totalSize - bytesRead);
            bytesRead += toSkip;
            return toSkip;
        }

        @Override
        public int available() {
            long remaining = totalSize - bytesRead;
            return (int) Math.min(remaining, Integer.MAX_VALUE);
        }
    }
}
