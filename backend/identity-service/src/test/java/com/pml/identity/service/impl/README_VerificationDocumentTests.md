# VerificationDocument Integration Test Documentation

## Overview

The `VerificationDocumentWorkflowIntegrationTest` is a comprehensive integration test suite that validates the complete document verification lifecycle for event organizers in the ticketing system.

## File Location

```
backend/identity-service/src/test/java/com/pml/identity/service/impl/VerificationDocumentWorkflowIntegrationTest.java
```

## Purpose

This integration test ensures that the Know Your Business (KYB) document verification workflow functions correctly from upload through approval/rejection, including:

- Document upload and metadata storage
- Admin approval workflow
- Admin rejection workflow with feedback
- Multiple document type handling
- Organization verification status updates
- Security and ownership validation
- Concurrent upload scenarios

## Technology Stack

- **Spring Boot Test**: Full application context
- **TestContainers**: Real MongoDB 8.0 instance with automatic cleanup
- **MockBean**: StreamBridge for Azure Service Bus event publication
- **Reactor Test**: StepVerifier for Mono/Flux validation
- **JUnit 5**: Test framework with ordered execution

## Test Coverage

### 1. Complete Upload Workflow (Tests 1-3)

**Test 1: shouldUploadDocumentSuccessfully**
- Validates successful document upload
- Verifies metadata is correctly saved to MongoDB
- Checks document status is PENDING
- Ensures timestamps are set correctly

**Test 2: shouldUpdateExistingDocumentOnReupload**
- Tests document replacement when same type is uploaded again
- Verifies status resets to PENDING
- Ensures no duplicate documents are created

**Test 3: shouldRejectUploadForNonExistentOrganization**
- Validates error handling for invalid organization IDs
- Ensures proper exception is thrown

### 2. Admin Approval Workflow (Tests 10-12)

**Test 10: shouldApproveDocumentAndUpdateOrganization**
- Tests admin approval of three documents (minimum threshold)
- Verifies document status changes to APPROVED
- Validates organization `documentsVerified` flag is set to true
- Confirms event publication to Azure Service Bus

**Test 11: shouldRejectApprovalOfApprovedDocument**
- Prevents double-approval of documents
- Ensures proper error handling for invalid state transitions

**Test 12: shouldRejectApprovalOfNonExistentDocument**
- Validates error handling for invalid document IDs

### 3. Admin Rejection Workflow (Tests 20-21)

**Test 20: shouldRejectDocumentWithReason**
- Tests admin rejection with feedback reason
- Verifies rejection metadata (timestamp, admin ID, reason)
- Ensures organization verification status remains false
- Confirms rejection event is published

**Test 21: shouldAllowReuploadAfterRejection**
- Validates users can re-upload after rejection
- Ensures new upload resets status to PENDING
- Clears previous rejection reason

### 4. Multiple Document Types (Tests 30-31)

**Test 30: shouldHandleMultipleDocumentTypes**
- Tests concurrent handling of different document types:
  - ID_DOCUMENT
  - BUSINESS_LICENSE
  - TAX_CERTIFICATE
  - BANK_STATEMENT
- Verifies each type is stored independently

**Test 31: shouldQueryDocumentsByStatus**
- Tests filtering documents by status
- Validates correct document counts for:
  - PENDING documents
  - APPROVED documents
  - REJECTED documents

### 5. Delete Document Workflow (Tests 40-41)

**Test 40: shouldDeleteDocument**
- Tests single document deletion
- Verifies document is removed from database

**Test 41: shouldDeleteAllDocumentsForOrganization**
- Tests bulk deletion for organization
- Ensures all documents are removed

### 6. Concurrent Uploads (Test 50)

**Test 50: shouldHandleConcurrentUploadsCorrectly**
- Tests 5 concurrent document uploads
- Uses CountDownLatch for synchronization
- Verifies all uploads succeed without race conditions
- Validates correct document count

### 7. Pending Documents Queue (Test 60)

**Test 60: shouldRetrievePendingDocumentsQueue**
- Tests admin review queue functionality
- Validates retrieval of pending documents across multiple organizations
- Ensures proper filtering by status

### 8. Count Operations (Test 70)

**Test 70: shouldCountDocumentsCorrectly**
- Tests document counting methods
- Validates total count vs approved count
- Ensures aggregation accuracy

### 9. Document Type Existence Check (Test 80)

**Test 80: shouldCheckDocumentTypeExistence**
- Tests existence checking for document types
- Validates boolean return values

### 10. Organization Verification Threshold (Test 90)

**Test 90: shouldUpdateOrganizationAfterThreeApprovals**
- Tests the business rule: organization verified after 3 approved documents
- Validates organization status remains unverified with < 3 documents
- Confirms status updates to verified after 3rd approval
- Tests async status update handling

### 11. Error Scenarios (Tests 100-101)

**Test 100: shouldHandleNullParameters**
- Tests null parameter handling
- Validates proper error responses

**Test 101: shouldRejectRejectionWithoutReason**
- Tests rejection without reason provided
- Ensures system handles optional rejection reason

## Test Data Setup

### Test Organizations
- **Primary Organization**: `testOrganization` - APPROVED status, owner: testUser
- **Secondary Organization**: `anotherOrganization` - Used for cross-organization tests

### Test Users
- **Organizer User**: testUser - Has CUSTOMER + ORGANIZER roles
- **Admin User**: adminUser - Has CUSTOMER + ADMIN roles

### Document Constants
- Max file size: 10 MB
- Valid MIME type: application/pdf
- Test documents stored with S3 URLs (mocked)

## Key Assertions

Each test validates:
- **Reactive Streams**: Uses StepVerifier for Mono/Flux validation
- **Entity State**: Verifies entity properties match expected values
- **Database State**: Confirms persistence using repository queries
- **Event Publication**: Verifies StreamBridge mock calls
- **Error Handling**: Validates exception types and messages
- **Async Operations**: Uses Thread.sleep for async status updates (300-500ms)

## Running the Tests

### Prerequisites
1. Docker must be running (for TestContainers MongoDB)
2. No existing MongoDB instance on port 27017 (TestContainers will bind dynamically)

### Run All Tests
```bash
cd backend/identity-service
mvn test -Dtest=VerificationDocumentWorkflowIntegrationTest
```

### Run Specific Test
```bash
mvn test -Dtest=VerificationDocumentWorkflowIntegrationTest#shouldUploadDocumentSuccessfully
```

### Run with Debug Logging
```bash
mvn test -Dtest=VerificationDocumentWorkflowIntegrationTest -Dlogging.level.com.pml.identity=DEBUG
```

## Test Execution Order

Tests are executed in numerical order (@Order annotation):
1. Upload workflow (1-3)
2. Approval workflow (10-12)
3. Rejection workflow (20-21)
4. Multiple document types (30-31)
5. Delete operations (40-41)
6. Concurrent uploads (50)
7. Admin queue (60)
8. Count operations (70)
9. Existence checks (80)
10. Verification threshold (90)
11. Error scenarios (100-101)

## Known Limitations

1. **Async Updates**: Uses Thread.sleep() for organization status updates
   - Could be improved with awaitility or custom wait conditions

2. **Mock Event Bus**: StreamBridge is mocked
   - Actual Azure Service Bus integration not tested
   - Event payload structure not validated

3. **No File Storage Validation**: S3 operations are not tested
   - File upload/download is out of scope
   - Only metadata operations are tested

## Future Enhancements

1. **Add Performance Tests**: Measure upload/approval throughput
2. **Add Security Tests**: Test role-based access control at resolver level
3. **Add File Validation Tests**: Test file size limits, MIME type restrictions
4. **Add Cross-Organization Security**: Test that users cannot access other orgs' docs
5. **Add Event Payload Validation**: Verify event structure for downstream consumers
6. **Add Retry Logic Tests**: Test document approval retry on failure
7. **Add Pagination Tests**: Test document listing with pagination

## Related Files

- **Service**: `com.pml.identity.service.impl.VerificationDocumentServiceImpl`
- **Repository**: `com.pml.identity.repository.VerificationDocumentRepository`
- **Entity**: `com.pml.identity.domain.model.VerificationDocument`
- **Enum**: `com.pml.identity.domain.enums.DocumentStatus`
- **Organization**: `com.pml.identity.domain.model.Organization`

## Test Execution Time

- **Average execution time**: ~15-20 seconds
- **TestContainers startup**: ~5-8 seconds
- **All tests**: ~12 seconds
- **Async operations**: ~2-3 seconds (sleep delays)

## Maintenance Notes

- Update test data IDs when schema changes
- Add new tests when adding document types
- Update verification threshold if business rules change
- Mock new event types when adding event publishing
- Update assertions if entity structure changes

## Troubleshooting

### MongoDB Container Fails to Start
- Ensure Docker Desktop is running
- Check port 27017 is not in use
- Verify Docker has sufficient memory (4GB+ recommended)

### Tests Fail on CI/CD
- Ensure CI runner has Docker support
- Increase timeout values for slower CI environments
- Use TestContainers Cloud for faster startup

### Flaky Tests (Random Failures)
- Increase sleep delays for async operations
- Check for race conditions in concurrent tests
- Verify database cleanup in @BeforeEach

### Compilation Errors
- Ensure AWS SDK dependencies are in pom.xml
- Verify all required services are implemented
- Check for missing event record definitions
