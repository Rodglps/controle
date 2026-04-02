package com.concil.edi.consumer.dto;

import com.concil.edi.commons.enums.ServerType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a complete server configuration for file transfer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServerConfigurationDTO {
    
    // Server information
    private Long serverId;
    private String codServer;
    private String codVault;
    private String desVaultSecret;
    private ServerType serverType;
    private String host;
    private Integer port;
    
    // Path information
    private Long serverPathId;
    private String path;
    private Long acquirerId;
}
