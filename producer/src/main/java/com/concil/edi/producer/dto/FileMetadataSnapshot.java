package com.concil.edi.producer.dto;

import com.concil.edi.commons.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Timestamp;

/**
 * Snapshot of file metadata captured during primary filter validation.
 * Used for comparison during secondary validation to detect ongoing file writes.
 */
@Data
@AllArgsConstructor
public class FileMetadataSnapshot {
    private String filename;
    private Timestamp lastModified;
    private Long size;
    private FileType fileType;
    
    /**
     * Compare this snapshot with current file metadata to detect changes.
     * 
     * @param current Current file metadata from SFTP
     * @return true if metadata unchanged (lastModified and size match), false if changed
     */
    public boolean matches(FileMetadataDTO current) {
        if (current == null) {
            return false;
        }
        
        boolean timestampMatches = this.lastModified != null 
            && this.lastModified.equals(current.getTimestamp());
        boolean sizeMatches = this.size != null 
            && this.size.equals(current.getFileSize());
        
        return timestampMatches && sizeMatches;
    }
}
