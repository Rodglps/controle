package com.concil.edi.commons.enums;

/**
 * Enum representing the type of path (origin or destination).
 * Maps to VARCHAR2 column des_path_type in the sever_paths table.
 */
public enum PathType {
    ORIGIN,
    DESTINATION
}
