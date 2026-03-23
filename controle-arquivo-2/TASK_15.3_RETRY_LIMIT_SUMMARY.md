# Task 15.3: Implementar Limite de Reprocessamento - Summary

## Overview

Implemented retry limit mechanism to prevent infinite reprocessing of files. The system now tracks retry attempts in `jsn_additional_info` and marks files as permanent ERROR after 5 attempts.

## Changes Made

### 1. ProcessadorService.java

#### New Methods Added:

**verificarLimiteReprocessamento(Long idFileOrigin)**
- Checks retry limit before processing a file
- Counts previous retry attempts from `file_origin_client_processing` records
- Throws `ErroNaoRecuperavelException` if limit (5 attempts) is reached
- This causes ACK of the RabbitMQ message (no reprocessing)

**contarTentativasAnteriores(Long idFileOrigin)**
- Counts previous processing attempts for a file
- Searches for `file_origin_client` associated with the file
- Extracts `retryCount` from `jsn_additional_info` in error records
- Returns the maximum retry count found
- Fail-safe: returns 0 if any error occurs

**incrementarContadorTentativas(Long idFileOrigin)**
- Increments the retry counter
- Returns the new counter value
- Called when an error occurs during processing

#### Modified Methods:

**processarArquivo(MensagemProcessamento mensagem)**
- Added call to `verificarLimiteReprocessamento()` at the beginning
- Added call to `incrementarContadorTentativas()` in the catch block
- Passes retry count to `registrarErroRastreabilidade()`

**registrarErroRastreabilidade(...)**
- Added `tentativasAtuais` parameter
- Includes `retryCount` in `jsn_additional_info` along with stack trace
- Logs retry count for debugging

#### New Dependencies:
- `FileOriginClientProcessingRepository` - to query processing history
- `ObjectMapper` - to parse JSON from `jsn_additional_info`

### 2. FileOriginClientRepository.java

#### New Method Added:

**findByFileOriginIdAndActiveTrue(Long fileOriginId)**
- Spring Data JPA query method
- Finds active `FileOriginClient` by `FileOrigin` ID
- Used to link file to its processing history

### 3. ProcessadorServiceTest.java

#### Updated:
- Added mocks for `FileOriginClientProcessingRepository` and `ObjectMapper`
- Updated constructor call in `setup()` method to include new dependencies

### 4. ProcessadorServiceRetryLimitTest.java (NEW)

#### New Test Class:
Created comprehensive test suite for retry limit functionality:

**devePermitirProcessamentoQuandoNaoHaTentativasAnteriores()**
- Verifies first attempt is allowed
- No `file_origin_client` exists yet

**devePermitirProcessamentoQuandoMenosDe5Tentativas()**
- Verifies attempts 2, 3, 4 are allowed
- Simulates 3 previous error records with retry counts

**deveBloquearProcessamentoQuandoAtingiu5Tentativas()**
- Verifies processing is blocked at exactly 5 attempts
- Throws `ErroNaoRecuperavelException`
- Verifies error message contains limit information
- Verifies SFTP connection is never attempted

**deveBloquearProcessamentoQuandoMaisDe5Tentativas()**
- Verifies processing is blocked when > 5 attempts
- Tests with 7 previous attempts

**deveIncrementarContadorQuandoOcorreErro()**
- Verifies counter is incremented on error
- Verifies `retryCount` is included in `jsn_additional_info`
- Checks that `rastreabilidadeService.registrarConclusao()` receives correct counter

## Implementation Details

### Retry Counter Storage

The retry counter is stored in `jsn_additional_info` as JSON:
```json
{
  "retryCount": 3,
  "stackTrace": "...",
  "exceptionType": "..."
}
```

### Flow

1. **Before Processing:**
   - `verificarLimiteReprocessamento()` is called
   - Counts previous attempts from `file_origin_client_processing`
   - If count >= 5, throws `ErroNaoRecuperavelException`

2. **During Error:**
   - `incrementarContadorTentativas()` calculates new count
   - `registrarErroRastreabilidade()` stores count in `jsn_additional_info`
   - Error is classified and appropriate exception is thrown

3. **RabbitMQ Handling:**
   - If `ErroNaoRecuperavelException`: ACK message (don't reprocess)
   - If `ErroRecuperavelException`: NACK message (reprocess)

### Fail-Safe Behavior

The implementation includes fail-safe mechanisms:
- If error occurs while checking limit, processing continues (don't block)
- If error occurs while counting attempts, assumes 0 attempts
- If JSON parsing fails, ignores that record and continues

## Requirements Validated

**Requisito 15.6:** "IF múltiplos erros ocorrem para o mesmo arquivo, THEN THE Sistema SHALL limitar tentativas a 5 reprocessamentos"

✅ System checks retry count before processing
✅ System increments counter in `jsn_additional_info` on error
✅ System blocks processing after 5 attempts
✅ System throws `ErroNaoRecuperavelException` to prevent reprocessing

## Testing

### Unit Tests Created:
- 5 new test methods in `ProcessadorServiceRetryLimitTest`
- All tests use mocks to simulate different retry scenarios
- Tests verify both the blocking mechanism and counter increment

### Test Coverage:
- ✅ First attempt (no previous errors)
- ✅ Attempts 2-4 (under limit)
- ✅ Attempt 5 (at limit - blocked)
- ✅ Attempts > 5 (over limit - blocked)
- ✅ Counter increment on error

## Integration with Existing Code

The implementation integrates seamlessly with existing error handling:
- Uses existing `ErroNaoRecuperavelException` for non-recoverable errors
- Uses existing `RastreabilidadeService` for storing retry count
- Uses existing `FileOriginClientProcessing` entity for persistence
- Compatible with existing RabbitMQ ACK/NACK mechanism

## Next Steps

Task 15.3 is now complete. The next tasks in the spec are:

- **Task 15.4:** Write unit tests for error handling (partially done)
- **Task 15.5:** Write property-based test for retry limit
- **Task 15.6:** Implement structured error logging (already done)

## Notes

- The retry limit is hardcoded to 5 as per requirements
- The counter is stored per file (via `file_origin_id`)
- The counter persists across multiple processing attempts
- The implementation is thread-safe (uses database transactions)
