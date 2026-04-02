package com.concil.edi.producer.dto;

import com.concil.edi.commons.enums.ServerType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a complete server configuration for file collection.
 * Contains joined data from server, sever_paths, and sever_paths_in_out tables.
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
    
    // Origin path information
    private Long serverPathOriginId;
    private String originPath;
    private Long acquirerId;
    
    // Destination path information
    private Long serverPathDestinationId;
    
    // Mapping information
    private Long serverPathInOutId;
    
    // Validation parameters
    private Integer minAgeSeconds;
    private Integer doubleCheckWaitSeconds;
}
