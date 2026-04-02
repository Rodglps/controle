# Bugfix Requirements Document

## Introduction

The Producer's `SftpService.listFiles()` method creates a new `SessionFactory` instance on every invocation via `sftpConfig.createSessionFactory()`. Each `SessionFactory` wraps a `CachingSessionFactory` with a pool of 10 connections. While individual `Session` objects are properly closed in the finally block, the `SessionFactory` instances themselves are never closed or destroyed. This causes a resource leak where `SessionFactory` instances and their underlying connection pools accumulate over time, potentially exhausting system resources.

This bugfix addresses the lifecycle management of `SessionFactory` instances to ensure they are cached, reused per server configuration, and properly closed on application shutdown.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN `SftpService.listFiles()` is called THEN the system creates a new `SessionFactory` instance via `sftpConfig.createSessionFactory()`

1.2 WHEN a `SessionFactory` is created THEN the system wraps it in a `CachingSessionFactory` with a pool of 10 connections

1.3 WHEN `listFiles()` completes THEN the system closes the `Session` but never closes or destroys the `SessionFactory` instance

1.4 WHEN `listFiles()` is called multiple times for the same server configuration THEN the system creates duplicate `SessionFactory` instances instead of reusing existing ones

1.5 WHEN the application runs over time THEN the system accumulates `SessionFactory` instances and their connection pools, causing a resource leak

### Expected Behavior (Correct)

2.1 WHEN `SftpService.listFiles()` is called for a server configuration THEN the system SHALL retrieve or create a cached `SessionFactory` instance for that configuration

2.2 WHEN a `SessionFactory` is requested for a server configuration that already has a cached instance THEN the system SHALL reuse the existing `SessionFactory` instead of creating a new one

2.3 WHEN determining cache key for `SessionFactory` instances THEN the system SHALL use a combination of host, port, codVault, and vaultSecret to uniquely identify server configurations

2.4 WHEN the application shuts down THEN the system SHALL close all cached `SessionFactory` instances to release their connection pools

2.5 WHEN a `SessionFactory` is closed THEN the system SHALL properly release all pooled connections in its underlying `CachingSessionFactory`

### Unchanged Behavior (Regression Prevention)

3.1 WHEN `Session` objects are obtained from a `SessionFactory` THEN the system SHALL CONTINUE TO close them in a finally block after use

3.2 WHEN listing files from SFTP servers THEN the system SHALL CONTINUE TO return the same file metadata results as before

3.3 WHEN credentials are retrieved from Vault THEN the system SHALL CONTINUE TO use the same `VaultConfig.getCredentials()` method

3.4 WHEN SFTP connection parameters are configured THEN the system SHALL CONTINUE TO use the same host, port, user, password, and allowUnknownKeys settings

3.5 WHEN the `CachingSessionFactory` pool size is configured THEN the system SHALL CONTINUE TO use a pool size of 10 connections per `SessionFactory`

3.6 WHEN errors occur during SFTP operations THEN the system SHALL CONTINUE TO log errors and throw RuntimeException with appropriate messages
