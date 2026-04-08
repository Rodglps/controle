package com.concil.edi.commons.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * JPA Entity representing identified customers for a file.
 * Maps to the 'file_origin_clients' table in Oracle database.
 * 
 * This entity tracks which customers have been identified as owners of each file.
 * A file can have multiple customer owners, resulting in multiple records with the same
 * idt_file_origin but different idt_client values.
 * 
 * The unique constraint on (idt_file_origin, idt_client) prevents duplicate associations.
 */
@Entity
@Table(name = "file_origin_clients",
       uniqueConstraints = @UniqueConstraint(columnNames = {"idt_file_origin", "idt_client"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileOriginClients {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "file_origin_clients_seq_gen")
    @SequenceGenerator(name = "file_origin_clients_seq_gen", 
                       sequenceName = "file_origin_clients_seq", 
                       allocationSize = 1)
    @Column(name = "idt_client_identified")
    private Long idtClientIdentified;

    @Column(name = "idt_file_origin", nullable = false)
    private Long idtFileOrigin;

    @Column(name = "idt_client", nullable = false)
    private Long idtClient;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dat_creation", nullable = false)
    private Date datCreation;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "dat_update")
    private Date datUpdate;
}
