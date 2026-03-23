package br.com.concil.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMessage implements Serializable {

    private Long idtSeverPathsInOut;
    private Long idtAcquirer;
    private List<FileEntry> files;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileEntry implements Serializable {
        private Long idtFileOrigin;
        private String fileName;
        private String remotePath; // caminho completo no SFTP de origem
    }
}
