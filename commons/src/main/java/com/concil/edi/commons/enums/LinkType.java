package com.concil.edi.commons.enums;

/**
 * Enum representing the type of link between origin and destination paths.
 * Maps to VARCHAR2 column des_link_type in the sever_paths_in_out table.
 */
public enum LinkType {
    PRINCIPAL,
    SECUNDARIO
}
