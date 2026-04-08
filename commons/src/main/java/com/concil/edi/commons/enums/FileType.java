package com.concil.edi.commons.enums;

/**
 * Enum representing the type of file being processed.
 * Maps to VARCHAR2 column des_file_type in the file_origin table and layout table.
 * 
 * Values:
 * - CSV: Comma-separated values file
 * - JSON: JSON format file
 * - TXT: Plain text file (positional or delimited)
 * - XML: XML format file
 * - OFX: Open Financial Exchange format file
 */
public enum FileType {
    CSV,
    JSON,
    TXT,
    XML,
    OFX
}
