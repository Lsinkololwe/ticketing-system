# Financial Engine - Redundant Code Report

## Overview
This report identifies potentially redundant, duplicate, or conflicting code in the booking-service financial engine that may cause compilation issues.

---

## 1. CRITICAL: Method Signature Mismatches

### 1.1 JournalEntry.create() Method - MISSING

**Location**: `/backend/booking-service/src/main/java/com/pml/booking/service/impl/JournalServiceImpl.java`

**Issue**: `JournalServiceImpl` calls `JournalEntry.create()` with 9 parameters, but this method does not exist in `JournalEntry.java`.

**Calling Code (JournalServiceImpl.java:87-97)**:
```java
JournalEntry entry = JournalEntry.create(
        entryNumber,
        correlationId,
        entryDate,
        effectiveDate,
        description,
        type,
        lines,
        createdBy,
        metadata
);
```

**Available Factory Methods in JournalEntry.java**:
- `createSimpleEntry()` - Only for two-line entries (one debit, one credit)
- `createReversal()` - Only for reversal entries

**Fix Required**: Either add a `JournalEntry.create()` factory method or refactor `JournalServiceImpl` to use the builder pattern.

---

### 1.2 JournalEntry.createReversal() - Signature Mismatch

**Location**: `/backend/booking-service/src/main/java/com/pml/booking/service/impl/JournalServiceImpl.java`

**Issue**: `JournalServiceImpl` calls `createReversal()` with 2 parameters, but the method requires 3 parameters.

**Calling Code (JournalServiceImpl.java:193)**:
```java
JournalEntry reversalEntry = originalEntry.createReversal(reason, reversedBy);
```

**Actual Method Signature (JournalEntry.java:615)**:
```java
public JournalEntry createReversal(String newEntryNumber, String reason, String userId)
```

**Fix Required**: Update the call to provide the entry number as the first parameter.

---

### 1.3 JournalEntry.markReversed() - Signature Mismatch

**Location**: `/backend/booking-service/src/main/java/com/pml/booking/service/impl/JournalServiceImpl.java`

**Issue**: `JournalServiceImpl` calls `markReversed()` with 3 parameters, but the method only accepts 2.

**Calling Code (JournalServiceImpl.java:204-208)**:
```java
originalEntry.markReversed(
        savedReversal.getId(),
        reason,
        reversedBy
);
```

**Actual Method Signature (JournalEntry.java:488)**:
```java
public void markReversed(String reversalEntryId, String userId)
```

**Fix Required**: Remove the `reason` parameter from the call.

---

### 1.4 JournalEntry.setReversalEntryId() - Non-existent Method

**Location**: `/backend/booking-service/src/main/java/com/pml/booking/service/impl/JournalServiceImpl.java`

**Issue**: `JournalServiceImpl` calls `setReversalEntryId()` which does not exist.

**Calling Code (JournalServiceImpl.java:211)**:
```java
savedReversal.setReversalEntryId(originalEntry.getId());
```

**Available Field**: The field is named `reversalOfEntryId`, so the setter should be `setReversalOfEntryId()`.

**Fix Required**: Change `setReversalEntryId` to `setReversalOfEntryId`.

---

## 2. Duplicate/Redundant Enum Values

### 2.1 AccountSubType Enum - Duplicate Account Codes

**Location**: `/backend/booking-service/src/main/java/com/pml/booking/domain/enums/AccountSubType.java`

**Issue**: Multiple enum values share the same account code prefix, causing potential confusion.

**Duplicates Found**:

| Enum Value | Account Code | Duplicate Of |
|------------|--------------|--------------|
| `PAYOUT_PAYABLE` | "2021" | `PAYOUTS_PAYABLE` |
| `REFUND_PAYABLE` | "2022" | `REFUNDS_PAYABLE` |
| `GATEWAY_FEE_EXPENSE` | "5010" | `GATEWAY_FEES` |
| `CHARGEBACK_EXPENSE` | "5020" | `CHARGEBACK_LOSS` |
| `BAD_DEBT_EXPENSE` | "5040" | `BAD_DEBT` |

**Relevant Code (AccountSubType.java:100-145)**:
```java
PAYOUT_PAYABLE(AccountType.LIABILITY, "2021"),     // Line 100
...
PAYOUTS_PAYABLE(AccountType.LIABILITY, "2021"),    // Line 138

REFUND_PAYABLE(AccountType.LIABILITY, "2022"),     // Line 108
...
REFUNDS_PAYABLE(AccountType.LIABILITY, "2022"),    // Line 145
```

**Recommendation**: Remove duplicate enum values, keeping only one variant per account code.

---

## 3. Duplicate Model Classes

### 3.1 EscrowTransaction vs StandaloneEscrowTransaction

**Locations**:
- `/backend/booking-service/src/main/java/com/pml/booking/domain/model/EscrowTransaction.java`
- `/backend/booking-service/src/main/java/com/pml/booking/domain/model/StandaloneEscrowTransaction.java`

**Issue**: Two models exist for the same concept. One is embedded (in `EventEscrowAccount`), one is standalone (separate collection).

**EscrowTransaction.java** (98 lines):
- Simple embedded record
- No annotations for standalone persistence
- Used within `EventEscrowAccount.transactions` list

**StandaloneEscrowTransaction.java** (612 lines):
- Full standalone entity with MongoDB annotations
- Has its own collection `escrow_transactions`
- Has factory methods and extensive documentation
- References `EscrowTransaction` as "deprecated for new code"

**Recommendation**:
- `EscrowTransaction` should be deprecated or removed
- Migrate to using `StandaloneEscrowTransaction` exclusively
- The comment on line 94 of StandaloneEscrowTransaction.java already indicates this: `@see EscrowTransaction (embedded version - deprecated for new code)`

---

### 3.2 EscrowAccount vs EventEscrowAccount

**Locations**:
- `/backend/booking-service/src/main/java/com/pml/booking/domain/model/EscrowAccount.java`
- `/backend/booking-service/src/main/java/com/pml/booking/domain/model/EventEscrowAccount.java`

**Issue**: Two escrow account models exist, with different collections and purposes.

**EscrowAccount.java**:
- Collection: `escrow_accounts`
- Generic escrow account (not event-specific)
- Has fields: `accountName`, `associatedBankAccountNumber`
- Uses `EscrowAccountStatus` from shared library

**EventEscrowAccount.java**:
- Collection: `event_escrow_accounts`
- Per-event escrow account
- Has fields: `eventId`, `eventTitle`, `organizerId`
- Uses inline `EscrowStatus` enum

**Corresponding Repositories**:
- `EscrowAccountRepository` -> `EscrowAccount`
- `EventEscrowAccountRepository` -> `EventEscrowAccount`

**Recommendation**: Consolidate to a single escrow account model if they represent the same concept, or clearly document the distinction if they serve different purposes.

---

## 4. Duplicate TransactionType Enums

### 4.1 Multiple TransactionType Definitions

**Locations**:
- `/backend/booking-service/src/main/java/com/pml/booking/domain/model/EscrowTransaction.java` (lines 87-97)
- `/backend/booking-service/src/main/java/com/pml/booking/domain/model/StandaloneEscrowTransaction.java` (lines 301-315)
- `/backend/booking-service/src/main/java/com/pml/booking/domain/enums/EscrowTransactionType.java` (full enum)

**Issue**: Three different `TransactionType`/`EscrowTransactionType` enums exist for the same concept (CREDIT/DEBIT).

**EscrowTransaction.TransactionType**:
```java
public enum TransactionType {
    CREDIT,
    DEBIT
}
```

**StandaloneEscrowTransaction.TransactionType**:
```java
public enum TransactionType {
    CREDIT,
    DEBIT
}
```

**EscrowTransactionType** (standalone enum):
```java
public enum EscrowTransactionType {
    CREDIT,
    DEBIT;

    public EscrowTransactionType opposite() { ... }
    public BigDecimal signedAmount(BigDecimal amount) { ... }
}
```

**Recommendation**: Use only `EscrowTransactionType` from the enums package, which has additional utility methods. Remove the inline `TransactionType` enums from the model classes.

---

## 5. Potentially Unused Exception Classes

### 5.1 Exceptions That May Be Unused

**Location**: `/backend/booking-service/src/main/java/com/pml/booking/exception/`

The following exception classes are defined but no `throw` statements for them were found in the codebase:

| Exception Class | File | Status |
|-----------------|------|--------|
| `InsufficientEscrowBalanceException` | InsufficientEscrowBalanceException.java | Imported but not thrown |
| `RefundNotAllowedException` | RefundNotAllowedException.java | Not found in `throw` statements |
| `DoubleBookingException` | DoubleBookingException.java | Not found in `throw` statements |
| `AccountNotFoundException` | AccountNotFoundException.java | Imported but not thrown |
| `ReconciliationDiscrepancyException` | ReconciliationDiscrepancyException.java | Imported but not thrown |
| `InactiveAccountException` | InactiveAccountException.java | Imported but not thrown |

**Note**: These exceptions may be used through inheritance or in generated code, so verify before removal.

---

## 6. Summary of Required Fixes

### High Priority (Compilation Blockers)

1. **Add `JournalEntry.create()` factory method** with signature matching `JournalServiceImpl` calls, OR refactor `JournalServiceImpl` to use builder pattern

2. **Fix `createReversal()` call** in `JournalServiceImpl.java:193` to include entry number parameter

3. **Fix `markReversed()` call** in `JournalServiceImpl.java:204-208` to remove the `reason` parameter

4. **Fix `setReversalEntryId()`** in `JournalServiceImpl.java:211` to `setReversalOfEntryId()`

### Medium Priority (Code Quality)

5. **Remove duplicate enum values** in `AccountSubType.java`:
   - Keep `PAYOUT_PAYABLE`, remove `PAYOUTS_PAYABLE`
   - Keep `REFUND_PAYABLE`, remove `REFUNDS_PAYABLE`
   - Keep `GATEWAY_FEE_EXPENSE`, remove `GATEWAY_FEES`
   - Keep `CHARGEBACK_EXPENSE`, remove `CHARGEBACK_LOSS`
   - Keep `BAD_DEBT_EXPENSE`, remove `BAD_DEBT`

6. **Consolidate TransactionType enums** - Use only `EscrowTransactionType` from enums package

7. **Deprecate `EscrowTransaction`** - Fully migrate to `StandaloneEscrowTransaction`

### Low Priority (Cleanup)

8. **Review `EscrowAccount` vs `EventEscrowAccount`** - Determine if both are needed

9. **Verify unused exceptions** - Remove if truly unused

---

## Files Requiring Changes

| File | Issue Count | Priority |
|------|-------------|----------|
| `JournalServiceImpl.java` | 4 | HIGH |
| `JournalEntry.java` | 1 (missing factory method) | HIGH |
| `AccountSubType.java` | 5 duplicate values | MEDIUM |
| `StandaloneEscrowTransaction.java` | 1 (duplicate inner enum) | MEDIUM |
| `EscrowTransaction.java` | 2 (deprecated, duplicate enum) | MEDIUM |
| Various exception classes | 6 potentially unused | LOW |

---

*Report generated: Review booking-service financial engine codebase*
