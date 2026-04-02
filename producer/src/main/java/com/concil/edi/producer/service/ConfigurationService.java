package com.concil.edi.producer.service;

import com.concil.edi.commons.enums.PathType;
import com.concil.edi.commons.entity.ServerPath;
import com.concil.edi.commons.entity.ServerPathInOut;
import com.concil.edi.producer.dto.ServerConfigurationDTO;
import com.concil.edi.commons.repository.ServerPathInOutRepository;
import com.concil.edi.commons.repository.ServerPathRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for loading active server configurations.
 * Loads active configurations by joining server, sever_paths, and sever_paths_in_out tables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationService {
    
    private final ServerPathRepository serverPathRepository;
    private final ServerPathInOutRepository serverPathInOutRepository;
    
    @Transactional(readOnly = true)
    public List<ServerConfigurationDTO> loadActiveConfigurations() {
        log.debug("Loading active configurations");
        
        // Load all active origin paths (flg_active=1, des_path_type=ORIGIN)
        List<ServerPath> originPaths = serverPathRepository.findByFlgActiveAndDesPathType(1, PathType.ORIGIN);
        
        // Load all active mappings
        List<ServerPathInOut> activeMappings = serverPathInOutRepository.findByFlgActive(1);
        
        List<ServerConfigurationDTO> configurations = new ArrayList<>();
        
        for (ServerPath originPath : originPaths) {
            // Find mappings for this origin path
            for (ServerPathInOut mapping : activeMappings) {
                if (mapping.getSeverPathOrigin().getIdtSeverPath().equals(originPath.getIdtSeverPath())) {
                    ServerConfigurationDTO config = new ServerConfigurationDTO();
                    
                    // Server information
                    config.setServerId(originPath.getServer().getIdtServer());
                    config.setCodServer(originPath.getServer().getCodServer());
                    config.setCodVault(originPath.getServer().getCodVault());
                    config.setDesVaultSecret(originPath.getServer().getDesVaultSecret());
                    config.setServerType(originPath.getServer().getDesServerType());
                    
                    // Origin path information
                    config.setServerPathOriginId(originPath.getIdtSeverPath());
                    config.setOriginPath(originPath.getDesPath());
                    config.setAcquirerId(originPath.getIdtAcquirer());
                    
                    // Destination path information
                    config.setServerPathDestinationId(mapping.getSeverPathDestination().getIdtSeverPath());
                    
                    // Mapping information
                    config.setServerPathInOutId(mapping.getIdtSeverPathsInOut());
                    
                    // Validation parameters
                    config.setMinAgeSeconds(originPath.getServer().getNumMinAgeSeconds());
                    config.setDoubleCheckWaitSeconds(originPath.getServer().getNumDoubleCheckWaitSeconds());
                    
                    configurations.add(config);
                }
            }
        }
        
        log.info("Loaded {} active configurations", configurations.size());
        return configurations;
    }
}
