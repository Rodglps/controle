package com.concil.edi.commons.enums;

/**
 * Enum representing the type of server for file storage and transfer.
 * Maps to VARCHAR2 column des_server_type in the server table.
 */
public enum ServerType {
    S3("S3"),
    BLOB_STORAGE("Blob-Storage"),
    OBJECT_STORAGE("Object Storage"),
    SFTP("SFTP"),
    NFS("NFS");

    private final String value;

    ServerType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ServerType fromValue(String value) {
        for (ServerType type : ServerType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid ServerType value: " + value);
    }
}
