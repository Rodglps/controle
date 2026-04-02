package com.concil.edi.producer.service;

import com.concil.edi.producer.dto.FileMetadataDTO;
import com.concil.edi.producer.dto.FileMetadataSnapshot;
import com.concil.edi.producer.dto.ServerConfigurationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for validating file eligibility using hybrid validation approach.
 * Combines age threshold filter (primary) with double-check validation (secondary).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileValidator {
    
    private final SftpService sftpService;
    
    private static final long FUTURE_TIMESTAMP_TOLERANCE_HOURS = 24;
    private static final long FUTURE_TIMESTAMP_TOLERANCE_MILLIS = FUTURE_TIMESTAMP_TOLERANCE_HOURS * 60 * 60 * 1000;

    /**
     * Normalize validation parameter (treat null or negative as 0).
     * 
     * @param value Parameter value from database
     * @return Normalized value (>= 0)
     */
    private int normalizeParameter(Integer value) {
        if (value == null || value < 0) {
            return 0;
        }
        return value;
    }

    /**
     * Calculate file age in milliseconds with UTC normalization.
     * Converts both lastModified and currentTime to UTC before calculating age.
     * Handles edge cases: 
     * - Future timestamps within 24h tolerance return 0
     * - Future timestamps > 24h are rejected (return -1)
     * - Null timestamps return -1
     * 
     * @param lastModified File's last modified timestamp
     * @param currentTimeMillis Current system time
     * @return Age in milliseconds, or 0 if future within tolerance, or -1 if null/invalid
     */
    private long calculateFileAge(Timestamp lastModified, long currentTimeMillis) {
        if (lastModified == null) {
            return -1;
        }
        
        // Convert both timestamps to UTC
        Instant lastModifiedUTC = lastModified.toInstant();
        Instant currentTimeUTC = Instant.ofEpochMilli(currentTimeMillis);
        
        // Calculate age in milliseconds
        long ageMillis = currentTimeUTC.toEpochMilli() - lastModifiedUTC.toEpochMilli();
        
        // Handle future timestamps with 24h tolerance
        if (ageMillis < 0) {
            long futureOffsetHours = Math.abs(ageMillis) / (1000 * 60 * 60);
            
            if (futureOffsetHours <= FUTURE_TIMESTAMP_TOLERANCE_HOURS) {
                // Within tolerance: treat as age 0
                log.info("File has future timestamp within tolerance: {} hours ahead", futureOffsetHours);
                return 0;
            } else {
                // Beyond tolerance: reject file
                log.warn("File has future timestamp beyond tolerance: {} hours ahead", futureOffsetHours);
                return -1;
            }
        }
        
        return ageMillis;
    }

    /**
     * Apply primary filter using age threshold.
     * Excludes files with age < threshold.
     * 
     * @param files Raw file list
     * @param ageThresholdSeconds Minimum age in seconds
     * @param codServer Server code for logging
     * @return Files passing age threshold with their snapshots
     */
    private Map<String, FileMetadataSnapshot> applyPrimaryFilter(
            List<FileMetadataDTO> files,
            int ageThresholdSeconds,
            String codServer) {
        
        Map<String, FileMetadataSnapshot> snapshots = new HashMap<>();
        long currentTimeMillis = System.currentTimeMillis();
        long ageThresholdMillis = ageThresholdSeconds * 1000L;
        
        for (FileMetadataDTO file : files) {
            // Exclude files with null/negative size
            if (file.getFileSize() == null || file.getFileSize() < 0) {
                log.warn("[{}] Excluding file with invalid size: {}, size: {}", 
                    codServer, file.getFilename(), file.getFileSize());
                continue;
            }
            
            // Calculate file age
            long fileAgeMillis = calculateFileAge(file.getTimestamp(), currentTimeMillis);
            
            // Exclude files with null/invalid timestamps
            if (fileAgeMillis < 0) {
                log.warn("[{}] Excluding file with invalid timestamp: {}", 
                    codServer, file.getFilename());
                continue;
            }
            
            // Exclude files younger than age threshold
            if (fileAgeMillis < ageThresholdMillis) {
                long remainingWaitSeconds = (ageThresholdMillis - fileAgeMillis) / 1000;
                log.debug("[{}] Excluding file by age threshold: {}, age: {}s, threshold: {}s, remaining: {}s", 
                    codServer, file.getFilename(), fileAgeMillis / 1000, ageThresholdSeconds, remainingWaitSeconds);
                continue;
            }
            
            // File passes primary filter - store metadata snapshot
            FileMetadataSnapshot snapshot = new FileMetadataSnapshot(
                file.getFilename(),
                file.getTimestamp(),
                file.getFileSize(),
                file.getFileType()
            );
            snapshots.put(file.getFilename(), snapshot);
        }
        
        log.debug("[{}] Primary filter: {} files passed out of {}", 
            codServer, snapshots.size(), files.size());
        
        return snapshots;
    }

    /**
     * Apply secondary validation using double-check.
     * Re-lists files and compares metadata to detect ongoing writes.
     * 
     * @param snapshots Metadata snapshots from primary filter
     * @param config Server configuration
     * @param doubleCheckWaitSeconds Wait duration before re-check
     * @return List of eligible files with unchanged metadata
     */
    private List<FileMetadataDTO> applySecondaryValidation(
            Map<String, FileMetadataSnapshot> snapshots,
            ServerConfigurationDTO config,
            int doubleCheckWaitSeconds) {
        
        List<FileMetadataDTO> eligibleFiles = new ArrayList<>();
        
        // If double-check is disabled, all files passing primary filter are eligible
        if (doubleCheckWaitSeconds == 0) {
            for (FileMetadataSnapshot snapshot : snapshots.values()) {
                eligibleFiles.add(new FileMetadataDTO(
                    snapshot.getFilename(),
                    snapshot.getSize(),
                    snapshot.getLastModified(),
                    snapshot.getFileType()
                ));
            }
            log.debug("[{}] Secondary validation skipped (doubleCheckWait=0), {} files eligible", 
                config.getCodServer(), eligibleFiles.size());
            return eligibleFiles;
        }
        
        // Wait for double-check duration
        try {
            log.debug("[{}] Waiting {} seconds before re-checking files", 
                config.getCodServer(), doubleCheckWaitSeconds);
            Thread.sleep(doubleCheckWaitSeconds * 1000L);
        } catch (InterruptedException e) {
            log.warn("[{}] Double-check wait interrupted", config.getCodServer(), e);
            Thread.currentThread().interrupt();
            return eligibleFiles; // Return empty list on interruption
        }
        
        // Re-list files from SFTP
        List<FileMetadataDTO> currentFiles;
        try {
            currentFiles = sftpService.listFiles(config);
        } catch (Exception e) {
            log.error("[{}] SFTP connection error during re-check, excluding all files", 
                config.getCodServer(), e);
            return eligibleFiles; // Return empty list on SFTP error
        }
        
        // Build map of current files for quick lookup
        Map<String, FileMetadataDTO> currentFileMap = new HashMap<>();
        for (FileMetadataDTO file : currentFiles) {
            currentFileMap.put(file.getFilename(), file);
        }
        
        // Compare current metadata with snapshots
        for (Map.Entry<String, FileMetadataSnapshot> entry : snapshots.entrySet()) {
            String filename = entry.getKey();
            FileMetadataSnapshot snapshot = entry.getValue();
            
            // Check if file still exists
            FileMetadataDTO currentFile = currentFileMap.get(filename);
            if (currentFile == null) {
                log.info("[{}] Excluding file by double-check: {} (file disappeared)", 
                    config.getCodServer(), filename);
                continue;
            }
            
            // Compare metadata
            if (!snapshot.matches(currentFile)) {
                log.info("[{}] Excluding file by double-check: {} (metadata changed - lastModified or size)", 
                    config.getCodServer(), filename);
                continue;
            }
            
            // File passed secondary validation
            eligibleFiles.add(currentFile);
        }
        
        log.info("[{}] Secondary validation complete: {} eligible files out of {} checked", 
            config.getCodServer(), eligibleFiles.size(), snapshots.size());
        
        return eligibleFiles;
    }

    /**
     * Validate files using hybrid approach (age threshold + double-check).
     * 
     * @param files Raw file list from SFTP
     * @param config Server configuration with validation parameters
     * @return List of eligible files that passed both validations
     */
    public List<FileMetadataDTO> validateFiles(
            List<FileMetadataDTO> files, 
            ServerConfigurationDTO config) {
        
        try {
            // Normalize validation parameters
            int ageThresholdSeconds = normalizeParameter(config.getMinAgeSeconds());
            int doubleCheckWaitSeconds = normalizeParameter(config.getDoubleCheckWaitSeconds());
            
            // Skip validation when both parameters are 0
            if (ageThresholdSeconds == 0 && doubleCheckWaitSeconds == 0) {
                log.debug("[{}] Validation disabled (both parameters = 0), processing all {} files", 
                    config.getCodServer(), files.size());
                return files;
            }
            
            // Apply primary filter
            Map<String, FileMetadataSnapshot> snapshots = 
                applyPrimaryFilter(files, ageThresholdSeconds, config.getCodServer());
            
            // Apply secondary validation
            return applySecondaryValidation(snapshots, config, doubleCheckWaitSeconds);
            
        } catch (Exception e) {
            log.error("[{}] Validation failed, excluding all files", config.getCodServer(), e);
            return new ArrayList<>(); // Fail-safe: exclude all files
        }
    }
}
