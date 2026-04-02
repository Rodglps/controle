package com.concil.edi.producer.dto;

import com.concil.edi.commons.enums.FileType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * DTO representing file metadata collected from SFTP server.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileMetadataDTO {
    private String filename;
    private Long fileSize;
    private Timestamp timestamp;
    private FileType fileType;
}
