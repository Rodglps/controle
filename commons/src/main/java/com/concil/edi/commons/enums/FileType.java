package com.concil.edi.commons.enums;

/**
 * Enum representing the type of file being processed.
 * Maps to VARCHAR2 column des_file_type in the file_origin table.
 */
public enum FileType {
    csv,
    json,
    txt,
    xml
}
