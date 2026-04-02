package com.concil.edi.producer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing server credentials.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CredentialsDTO {
    private String user;
    private String password;
}
