package com.concil.edi.commons.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Data Transfer Object for RabbitMQ messages containing file transfer information.
 * Implements Serializable for RabbitMQ message serialization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileTransferMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Identifier of the file_origin record in the database.
     */
    private Long idtFileOrigin;

    /**
     * Name of the file to be transferred.
     */
    private String filename;

    /**
     * Identifier of the origin server path (sever_paths table).
     */
    private Long idtServerPathOrigin;

    /**
     * Identifier of the destination server path (sever_paths table).
     */
    private Long idtServerPathDestination;
    
    /**
     * Size of the file in bytes.
     */
    private Long fileSize;
}
