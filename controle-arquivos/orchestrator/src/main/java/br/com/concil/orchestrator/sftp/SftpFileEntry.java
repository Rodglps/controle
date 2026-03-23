package br.com.concil.orchestrator.sftp;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SftpFileEntry {
    private String fileName;
    private String remotePath;
    private long lastModifiedMillis;
    private long sizeBytes;
}
