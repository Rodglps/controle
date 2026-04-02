# SFTP SessionFactory Leak Fix Design

## Overview

The Producer's `SftpService.listFiles()` method creates a new `SessionFactory` instance on every invocation, causing a resource leak. Each `SessionFactory` wraps a `CachingSessionFactory` with a pool of 10 connections. While individual `Session` objects are properly closed, the `SessionFactory` instances themselves are never closed or destroyed, causing connection pools to accumulate over time.

This fix implements a caching mechanism in `SftpConfig` to reuse `SessionFactory` instances per unique server configuration (host, port, codVault, vaultSecret) and ensures proper cleanup on application shutdown using Spring's lifecycle management.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug - when `listFiles()` is called, a new `SessionFactory` is created instead of reusing an existing one
- **Property (P)**: The desired behavior - `SessionFactory` instances should be cached per server configuration and reused across multiple calls
- **Preservation**: Existing SFTP file listing behavior, session management, and connection pooling that must remain unchanged by the fix
- **SessionFactory**: Spring Integration abstraction that creates SFTP sessions; wraps `CachingSessionFactory` with connection pool
- **CachingSessionFactory**: Spring Integration component that pools SFTP connections (configured with pool size of 10)
- **Server Configuration**: Unique combination of host, port, codVault, and vaultSecret that identifies an SFTP server
- **Cache Key**: String representation of server configuration used to lookup cached `SessionFactory` instances

## Bug Details

### Bug Condition

The bug manifests when `SftpService.listFiles()` is called multiple times (typically by the scheduled `FileCollectionScheduler`). The `SftpConfig.createSessionFactory()` method creates a new `SessionFactory` instance on every call, even for the same server configuration. Each `SessionFactory` wraps a `CachingSessionFactory` with 10 pooled connections that are never released.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type SftpServiceCall
  OUTPUT: boolean
  
  RETURN input.methodName == "listFiles"
         AND sessionFactoryCreated(input.host, input.port, input.codVault, input.vaultSecret)
         AND NOT sessionFactoryReused(input.host, input.port, input.codVault, input.vaultSecret)
END FUNCTION
```

### Examples

- **Example 1**: Scheduler calls `listFiles()` for Cielo SFTP server (host: cielo.sftp.com, port: 22) at 10:00 AM → Creates SessionFactory #1 with 10 connections
- **Example 2**: Scheduler calls `listFiles()` for same Cielo server at 10:05 AM → Creates SessionFactory #2 with 10 more connections (should reuse #1)
- **Example 3**: Scheduler calls `listFiles()` for Rede SFTP server (host: rede.sftp.com, port: 22) at 10:00 AM → Creates SessionFactory #3 with 10 connections (correct - different server)
- **Edge Case**: Application runs for 24 hours with scheduler calling `listFiles()` every 5 minutes for 3 different servers → Creates 864 SessionFactory instances (288 per server) instead of 3, accumulating 8,640 pooled connections

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- SFTP file listing must continue to return the same file metadata results as before
- Session objects must continue to be closed in finally blocks after use
- CachingSessionFactory pool size must remain at 10 connections per SessionFactory
- Credentials retrieval from Vault must continue to work exactly as before
- SFTP connection parameters (host, port, user, password, allowUnknownKeys) must remain unchanged
- Error handling and logging behavior must remain unchanged

**Scope:**
All SFTP operations that do NOT involve SessionFactory creation/caching should be completely unaffected by this fix. This includes:
- Session acquisition from SessionFactory
- File listing operations via session.list()
- Session closing and return to pool
- Credential retrieval from VaultConfig
- Error handling and exception throwing

## Hypothesized Root Cause

Based on the bug description and code analysis, the root cause is:

1. **No Caching Mechanism**: The `SftpConfig.createSessionFactory()` method is designed as a factory method that creates a new instance on every call, with no caching logic to check if a SessionFactory already exists for the given configuration.

2. **Missing Lifecycle Management**: There is no Spring lifecycle hook (e.g., `@PreDestroy`) to close SessionFactory instances when the application shuts down, even if caching were implemented.

3. **Service-Level Factory Calls**: The `SftpService.listFiles()` method directly calls `sftpConfig.createSessionFactory()` on every invocation, treating it as a disposable resource rather than a long-lived cached resource.

4. **No Cache Key Strategy**: There is no defined strategy for creating unique cache keys based on server configuration (host, port, codVault, vaultSecret) to enable proper caching.

## Correctness Properties

Property 1: Bug Condition - SessionFactory Caching and Reuse

_For any_ call to `listFiles()` with a server configuration (host, port, codVault, vaultSecret) that has been used before, the fixed `SftpConfig` SHALL return the same cached `SessionFactory` instance instead of creating a new one, reducing resource consumption and preventing connection pool accumulation.

**Validates: Requirements 2.1, 2.2, 2.3**

Property 2: Preservation - SFTP Operations Behavior

_For any_ SFTP operation (file listing, session management, credential retrieval, error handling), the fixed code SHALL produce exactly the same behavior as the original code, preserving all existing functionality for SFTP file operations and connection management.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `producer/src/main/java/com/concil/edi/producer/config/SftpConfig.java`

**Function**: `createSessionFactory()` → `getOrCreateSessionFactory()`

**Specific Changes**:

1. **Add SessionFactory Cache**: Introduce a `ConcurrentHashMap<String, SessionFactory>` field to store cached SessionFactory instances keyed by server configuration
   - Field name: `sessionFactoryCache`
   - Thread-safe to handle concurrent scheduler calls
   - Initialized in constructor or as field initializer

2. **Implement Cache Key Generation**: Create a private method to generate unique cache keys from server configuration
   - Method: `private String generateCacheKey(String host, int port, String codVault, String vaultSecret)`
   - Format: `"{host}:{port}:{codVault}:{vaultSecret}"`
   - Example: `"cielo.sftp.com:22:CIELO_VAULT:cielo/sftp/credentials"`

3. **Rename and Refactor Factory Method**: Change `createSessionFactory()` to `getOrCreateSessionFactory()` with caching logic
   - Check cache first using generated key
   - If found, return cached instance
   - If not found, create new SessionFactory, store in cache, and return
   - Use `computeIfAbsent()` for thread-safe cache operations

4. **Add Lifecycle Management**: Implement `DisposableBean` interface or use `@PreDestroy` annotation
   - Method: `@PreDestroy public void destroy()`
   - Iterate through all cached SessionFactory instances
   - Call `sessionFactory.close()` on each (if SessionFactory is Closeable/DisposableBean)
   - Clear the cache map
   - Log cleanup operations

5. **Update SftpService**: Modify `SftpService.listFiles()` to call `getOrCreateSessionFactory()` instead of `createSessionFactory()`
   - No other changes needed in SftpService
   - Maintains same method signature and behavior

### Implementation Pseudocode

```java
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SftpConfig implements DisposableBean {
    
    private final VaultConfig vaultConfig;
    private final ConcurrentHashMap<String, SessionFactory<DirEntry>> sessionFactoryCache = new ConcurrentHashMap<>();
    
    public SessionFactory<DirEntry> getOrCreateSessionFactory(
            String host, int port, String codVault, String vaultSecret) {
        
        String cacheKey = generateCacheKey(host, port, codVault, vaultSecret);
        
        return sessionFactoryCache.computeIfAbsent(cacheKey, key -> {
            log.info("Creating new SessionFactory for: {}", cacheKey);
            return createSessionFactoryInternal(host, port, codVault, vaultSecret);
        });
    }
    
    private SessionFactory<DirEntry> createSessionFactoryInternal(
            String host, int port, String codVault, String vaultSecret) {
        // Existing creation logic from createSessionFactory()
    }
    
    private String generateCacheKey(String host, int port, String codVault, String vaultSecret) {
        return String.format("%s:%d:%s:%s", host, port, codVault, vaultSecret);
    }
    
    @PreDestroy
    public void destroy() {
        log.info("Closing {} cached SessionFactory instances", sessionFactoryCache.size());
        sessionFactoryCache.values().forEach(factory -> {
            try {
                if (factory instanceof DisposableBean) {
                    ((DisposableBean) factory).destroy();
                }
            } catch (Exception e) {
                log.error("Error closing SessionFactory", e);
            }
        });
        sessionFactoryCache.clear();
    }
}
```

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code (SessionFactory instances accumulate), then verify the fix works correctly (SessionFactory instances are cached and reused) and preserves existing behavior (SFTP operations work identically).

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm that multiple calls to `listFiles()` with the same server configuration create multiple SessionFactory instances instead of reusing one.

**Test Plan**: Write tests that call `listFiles()` multiple times with the same server configuration and verify that new SessionFactory instances are created each time (on unfixed code). Use reflection or instrumentation to count SessionFactory instances or track object creation.

**Test Cases**:
1. **Multiple Calls Same Server Test**: Call `listFiles()` 10 times for same server config → Verify 10 SessionFactory instances created (will fail on unfixed code - demonstrates bug)
2. **Multiple Servers Test**: Call `listFiles()` for 3 different servers → Verify 3 SessionFactory instances created (should pass on unfixed code - correct behavior)
3. **Interleaved Calls Test**: Call `listFiles()` alternating between 2 servers, 5 times each → Verify 10 SessionFactory instances created instead of 2 (will fail on unfixed code)
4. **Memory Leak Simulation**: Call `listFiles()` 100 times for same server → Observe memory growth and connection pool accumulation (will fail on unfixed code)

**Expected Counterexamples**:
- SessionFactory instances are created on every call, even for identical server configurations
- Connection pools accumulate (10 connections per SessionFactory × number of calls)
- Memory usage grows linearly with number of calls

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds (repeated calls with same server config), the fixed function reuses cached SessionFactory instances.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  firstFactory := getOrCreateSessionFactory_fixed(input.host, input.port, input.codVault, input.vaultSecret)
  secondFactory := getOrCreateSessionFactory_fixed(input.host, input.port, input.codVault, input.vaultSecret)
  ASSERT firstFactory == secondFactory  // Same instance reference
END FOR
```

**Test Plan**: After implementing the fix, write tests that verify SessionFactory caching works correctly.

**Test Cases**:
1. **Cache Hit Test**: Call `getOrCreateSessionFactory()` twice with same config → Verify same instance returned
2. **Cache Miss Test**: Call `getOrCreateSessionFactory()` with different configs → Verify different instances returned
3. **Concurrent Access Test**: Call `getOrCreateSessionFactory()` concurrently from multiple threads with same config → Verify only one instance created
4. **Cleanup Test**: Trigger application shutdown → Verify all SessionFactory instances are closed and cache is cleared

### Preservation Checking

**Goal**: Verify that for all SFTP operations, the fixed code produces the same results as the original code.

**Pseudocode:**
```
FOR ALL sftpOperation WHERE NOT affectedBySessionFactoryCaching(sftpOperation) DO
  ASSERT listFiles_original(config) == listFiles_fixed(config)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across different server configurations
- It catches edge cases that manual unit tests might miss (e.g., special characters in paths, different file types)
- It provides strong guarantees that file listing behavior is unchanged for all server configurations

**Test Plan**: Observe behavior on UNFIXED code first for file listing operations, then write property-based tests capturing that behavior and verify it remains unchanged after fix.

**Test Cases**:
1. **File Listing Preservation**: Observe file listing results on unfixed code for various server configs, then verify fixed code returns identical results
2. **Session Management Preservation**: Verify sessions are still closed in finally blocks after fix
3. **Error Handling Preservation**: Verify same exceptions are thrown for invalid configs or connection failures
4. **Credential Retrieval Preservation**: Verify VaultConfig.getCredentials() is still called with same parameters

### Unit Tests

- Test cache key generation for various server configurations
- Test SessionFactory creation and caching logic
- Test concurrent access to cache (multiple threads requesting same config)
- Test cleanup on application shutdown (@PreDestroy method)
- Test that different server configs produce different cache keys
- Test that identical server configs produce same cache key

### Property-Based Tests

- Generate random server configurations and verify SessionFactory caching works correctly
- Generate random sequences of listFiles() calls and verify cache hit/miss behavior
- Generate random file listing scenarios and verify preservation of file metadata results
- Test that caching does not affect file listing correctness across many scenarios

### Integration Tests

- Test full scheduler flow with SessionFactory caching enabled
- Test multiple scheduler cycles reusing SessionFactory instances
- Test application startup and shutdown with SessionFactory lifecycle management
- Test that cached SessionFactory instances work correctly across multiple file listing operations
