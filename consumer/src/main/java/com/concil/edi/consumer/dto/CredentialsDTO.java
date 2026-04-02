package com.concil.edi.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing server credentials with connection details.
 * Includes host, port, user, and password for SFTP connections.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredentialsDTO {
    private String host;
    private String port;
    private String user;
    private String password;
}
