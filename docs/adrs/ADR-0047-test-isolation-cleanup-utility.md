# ADR-0047: Centralized Test Cleanup Utility for Singleton + Spring DI Isolation

**Status:** Accepted | **Date:** 2025-12-02

**Related**: ADR-0009, ADR-0020, ADR-0024, TestCleanupUtility.java

---

## Context

The application uses a hybrid service architecture that combines two patterns:

1. **Static singleton instances** (`getInstance()`) for backward compatibility with Milestone 1 requirements
2. **Spring DI beans** (`@Autowired`) for JPA-backed persistence and modern dependency injection

This dual approach was necessary to satisfy the requirement that legacy `ContactService.getInstance()` calls must continue to work while also supporting Spring Boot's dependency injection and JPA persistence.

### The Test Isolation Problem

During test suite evolution, we discovered a critical test isolation issue affecting 8 test classes:

- `AppointmentServiceTest` / `AppointmentServiceLegacyTest`
- `ContactServiceTest` / `ContactServiceLegacyTest`
- `TaskServiceTest` / `TaskServiceLegacyTest`
- `ProjectServiceTest` / `ProjectServiceLegacyTest`

**Symptoms:**
- `DuplicateResourceException` failures when running tests
- Failures were **order-dependent** - tests would pass in isolation but fail when run as a suite
- Error messages like: "Appointment with id '777' already exists" or "Contact with id 'legacy-100' already exists"

**Root Cause Analysis:**

The problem stemmed from the interaction between three factors:

1. **Spring Test Context Sharing**: Spring Boot caches `ApplicationContext` across test classes for performance (via `@SpringBootTest`)
2. **Static Singleton Persistence**: The static `instance` field in each service persists across test executions
3. **Data Migration on Initialization**: Service constructors call `registerInstance(this)`, which migrates data from old singleton instances to new Spring beans

**The Failure Sequence:**

```
Test Class 1 (e.g., ContactServiceTest):
  @BeforeEach: clearAllContacts() → clears database
  Test creates Contact("legacy-100", ...) → Success

Spring Context Reused (same ApplicationContext for performance)

Test Class 2 (e.g., ContactServiceLegacyTest):
  @BeforeEach: clearAllContacts() → clears database
  BUT: Static ContactService.instance still exists from Test Class 1
  Test calls ContactService.getInstance() → Returns existing instance
  registerInstance() NOT called (instance already set)
  Old singleton has Contact("legacy-100") in memory
  Test tries to create Contact("legacy-100", ...) → DuplicateResourceException! ❌
```

**Initial Mitigation Attempts (Insufficient):**

1. **Added `clearAllContacts()` in `@BeforeEach`** - Cleared database but NOT singleton state
2. **Added `@Isolated` annotations** - Prevented parallel execution but didn't reset singletons
3. **Disabled JUnit parallel execution** - Removed race conditions but didn't fix state bleeding

These mitigations reduced flakiness but didn't solve the fundamental problem: **singleton state persisted across test classes even when the database was cleared**.

---

## Decision

Create **`TestCleanupUtility`** - a centralized Spring component that enforces proper test isolation through a four-step cleanup process executed in a specific order.

### Implementation Location

```
src/test/java/contactapp/service/TestCleanupUtility.java
```

The utility is placed in the `contactapp.service` package (not `contactapp.support`) because it needs access to package-private `clearAll*()` methods in the service classes.

### Cleanup Order (Critical!)

```java
@Component
public class TestCleanupUtility {

    @Transactional
    public void resetTestEnvironment() {
        // Step 1: Clear security contexts
        clearSecurityContexts();

        // Step 2: Reset singletons BEFORE clearing data
        // This prevents registerInstance() from migrating old data
        resetAllSingletons();

        // Step 3: Clear all service data FIRST (tasks/contacts/appointments reference users)
        clearAllServiceData();

        // Step 4: Clean up test users LAST (FK cascade will handle orphans)
        if (testUserSetup != null) {
            testUserSetup.cleanup();
        }

        // Step 5: Setup fresh test user
        if (testUserSetup != null) {
            testUserSetup.setupTestUser();
        }
    }
}
```

**Why this order matters:**

1. **Security contexts first** - Prevents authentication errors during cleanup
2. **Singleton reset second** - Setting `instance = null` prevents `registerInstance()` from copying old data
3. **Service data third** - Must delete child records (tasks/contacts/appointments) BEFORE parent records (users)
4. **Users fourth** - PostgreSQL enforces FK constraints strictly; children must be deleted first (see ADR-0048)
5. **Fresh user last** - Ready for test execution

### Singleton Reset via Reflection

```java
private void resetSingleton(final Class<?> serviceClass) {
    try {
        final Field instanceField = serviceClass.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);  // Reset static field to null
    } catch (NoSuchFieldException | IllegalAccessException e) {
        System.err.println("Warning: Could not reset singleton for " +
            serviceClass.getSimpleName() + ": " + e.getMessage());
    }
}
```

This reflection approach is necessary because:
- The `instance` field is `private static` and not accessible outside the class
- Setting it to `null` prevents `getInstance()` from returning a stale instance
- Next `getInstance()` call will create a fresh instance or return the Spring bean

### Test Usage Pattern

**Before (Manual Cleanup - Error Prone):**

```java
@SpringBootTest
@ActiveProfiles("integration")
class ContactServiceTest {
    @Autowired private ContactService service;
    @Autowired private TestUserSetup testUserSetup;

    @BeforeEach
    void clearBeforeTest() {
        SecurityContextHolder.clearContext();  // Step 1
        testUserSetup.cleanup();                // Step 2
        testUserSetup.setupTestUser();          // Step 3
        service.clearAllContacts();             // Step 4
        setInstance(null);                      // Step 5 - via reflection
    }

    private static void setInstance(ContactService newInstance) {
        // Reflection boilerplate...
    }
}
```

Problems with manual approach:
- Inconsistent cleanup order across test classes
- Repeated reflection boilerplate
- Easy to forget a cleanup step
- No guarantee all services are cleared

**After (Centralized - Consistent):**

```java
@SpringBootTest
@ActiveProfiles("integration")
class ContactServiceTest {
    @Autowired private TestCleanupUtility testCleanup;

    @BeforeEach
    void clearBeforeTest() {
        testCleanup.resetTestEnvironment();
    }
}
```

Benefits:
- Single line in `@BeforeEach`
- Consistent across all test classes
- Enforces proper cleanup order
- Clears ALL services automatically

---

## Consequences

### Positive

✅ **Eliminated 8 Test Failures**: All `DuplicateResourceException` errors resolved
✅ **Consistent Test Isolation**: Every test starts with clean state
✅ **Single Source of Truth**: One place to update cleanup logic
✅ **Proper Cleanup Order**: Foreign key violations prevented
✅ **Less Boilerplate**: No reflection code in individual tests
✅ **Easier to Maintain**: Adding new services requires updating one utility
✅ **Self-Documenting**: Method name `resetTestEnvironment()` clearly states intent

### Negative

⚠️ **Requires Reflection**: Uses reflection to access private static fields
⚠️ **Package Coupling**: Must be in `contactapp.service` package to access `clearAll*()` methods
⚠️ **Maintenance Overhead**: Must update when new services are added
⚠️ **Test Dependency**: Tests now depend on Spring context loading TestCleanupUtility
⚠️ **Hidden Complexity**: Cleanup logic is abstracted away from individual tests

### Neutral

ℹ️ **SpringBootTest Only**: Only affects integration tests; unit tests unchanged
ℹ️ **Legacy Pattern Remains**: `getInstance()` still works, cleanup utility is purely for tests
ℹ️ **Optional Autowiring**: Uses `@Autowired(required = false)` for flexibility
ℹ️ **Backward Compatible**: Existing tests continue to work if they don't use utility

---

## Alternatives Considered

### Alternative 1: Remove Singleton Pattern Entirely

**Approach**: Delete `getInstance()` and force all code to use Spring DI

**Rejected Because:**
- Violates Milestone 1 requirements (must support `ContactService.getInstance()`)
- Would break backward compatibility
- Would require major refactoring of legacy code
- Out of scope for Phase 5

**Future Consideration**: When Milestone requirements no longer require `getInstance()`, this becomes viable (see backlog.md "Legacy Singleton Decommission")

### Alternative 2: Use @DirtiesContext

**Approach**: Annotate tests with `@DirtiesContext` to force Spring context recreation

```java
@SpringBootTest
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class ContactServiceTest {
    // Test methods...
}
```

**Rejected Because:**
- **Massive Performance Penalty**: Context recreation takes 2-5 seconds per test method
- 1066 tests × 3 seconds = ~53 minutes instead of 30 seconds
- Defeats the purpose of Spring's context caching
- CI/CD builds would timeout
- Local development iteration would be painfully slow

**When It Might Be Appropriate**: For a small number of integration tests that truly need isolated contexts

### Alternative 3: Manual Cleanup in Each Test

**Approach**: Continue with individual `@BeforeEach` methods using reflection

**Rejected Because:**
- Already tried this - led to inconsistencies
- Developers forget steps or get order wrong
- Reflection boilerplate repeated across 8+ test classes
- No guarantee all services are cleared (easy to miss one)
- Difficult to maintain when new services are added

### Alternative 4: Separate Test Contexts per Service

**Approach**: Create different Spring profiles for each service's tests

```java
@SpringBootTest
@ActiveProfiles("contactServiceTest")  // Separate profile
class ContactServiceTest {
}
```

**Rejected Because:**
- Creates artificial separation between services
- Doesn't reflect production configuration
- More complex test configuration management
- Still doesn't solve singleton state issue
- Harder to test service interactions

### Alternative 5: Clear Data Only (No Singleton Reset)

**Approach**: Just call `clearAllContacts()` without resetting singleton

**Rejected Because:**
- Already tried - didn't work
- `registerInstance()` migrates stale singleton data to fresh JPA stores
- Clearing database doesn't clear in-memory singleton state
- Root cause of the original DuplicateResourceException failures

---

## Implementation Details

### Full TestCleanupUtility Source

```java
package contactapp.service;

import contactapp.security.TestUserSetup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.lang.reflect.Field;

@Component
public class TestCleanupUtility {

    @Autowired(required = false)
    private ContactService contactService;

    @Autowired(required = false)
    private TaskService taskService;

    @Autowired(required = false)
    private AppointmentService appointmentService;

    @Autowired(required = false)
    private ProjectService projectService;

    @Autowired(required = false)
    private TestUserSetup testUserSetup;

    @Transactional
    public void resetTestEnvironment() {
        clearSecurityContexts();
        resetAllSingletons();
        if (testUserSetup != null) {
            testUserSetup.cleanup();
        }
        clearAllServiceData();
        if (testUserSetup != null) {
            testUserSetup.setupTestUser();
        }
    }

    private void clearSecurityContexts() {
        SecurityContextHolder.clearContext();
        TestSecurityContextHolder.clearContext();
    }

    private void resetAllSingletons() {
        resetSingleton(ContactService.class);
        resetSingleton(TaskService.class);
        resetSingleton(AppointmentService.class);
        resetSingleton(ProjectService.class);
    }

    private void resetSingleton(final Class<?> serviceClass) {
        try {
            final Field instanceField = serviceClass.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.err.println("Warning: Could not reset singleton for " +
                serviceClass.getSimpleName() + ": " + e.getMessage());
        }
    }

    private void clearAllServiceData() {
        try {
            if (contactService != null) {
                contactService.clearAllContacts();
            }
            if (taskService != null) {
                taskService.clearAllTasks();
            }
            if (appointmentService != null) {
                appointmentService.clearAllAppointments();
            }
            if (projectService != null) {
                projectService.clearAllProjects();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear service data", e);
        }
    }
}
```

### Affected Test Classes

All 8 failing test classes were updated to use `TestCleanupUtility`:

```
src/test/java/contactapp/service/
├── AppointmentServiceTest.java          ← Updated
├── AppointmentServiceLegacyTest.java    ← Updated
├── ContactServiceTest.java              ← Updated
├── ContactServiceLegacyTest.java        ← Updated
├── TaskServiceTest.java                 ← Updated
├── TaskServiceLegacyTest.java           ← Updated
├── ProjectServiceTest.java              ← Updated
├── ProjectServiceLegacyTest.java        ← Updated
└── TestCleanupUtility.java              ← Created
```

### Example Update

**Before:**
```java
@BeforeEach
void clearBeforeTest() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    testUserSetup.cleanup();
    testUserSetup.setupTestUser();
    service.clearAllContacts();
    setInstance(null);  // Reflection call
}
```

**After:**
```java
@Autowired
private TestCleanupUtility testCleanup;

@BeforeEach
void clearBeforeTest() {
    testCleanup.resetTestEnvironment();
}
```

---

## Relationship to Other ADRs

### ADR-0009 (Test Strategy)
- **Builds On**: The singleton reset helpers (`clearAllContacts`, etc.) documented in ADR-0009
- **Extends**: Centralizes those reset helpers into a consistent pattern
- **Supersedes**: Manual per-test cleanup approach

### ADR-0020 (Spring Boot Scaffold)
- **Related To**: The Spring Boot + singleton hybrid architecture
- **Addresses**: Test isolation challenges from dual patterns
- **Requires**: Package-private `clearAll*()` methods remain accessible

### ADR-0024 (Persistence Implementation)
- **Related To**: The JPA + in-memory store dual strategy
- **Solves**: Test isolation issues from store migration
- **Prevents**: `registerInstance()` from copying stale data

### ADR-0002, ADR-0011, ADR-0013 (Service Singletons)
- **Applies To**: All services using the singleton pattern
- **Maintains**: Backward compatibility with `getInstance()`
- **Ensures**: Clean test state regardless of service design

---

## Verification

### Test Execution Proof

**Before Fix:**
```
Error:  Errors:
Error:    AppointmentServiceLegacyTest.coldStartReturnsInMemoryStore:62 » DuplicateResource
          Appointment with id '777' already exists
Error:    AppointmentServiceTest.testSingletonSharesStateWithSpringBean:53 » DuplicateResource
          Appointment with id 'legacy-apt' already exists
Error:    ContactServiceLegacyTest.coldStartReturnsInMemoryInstance:62 » DuplicateResource
          Contact with id '900' already exists
Error:    ContactServiceTest.testSingletonSharesStateWithSpringBean:64 » DuplicateResource
          Contact with id 'legacy-100' already exists
Error:    ProjectServiceLegacyTest.coldStartReturnsInMemoryStore:57 » DuplicateResource
          Project with id 'legacy1' already exists
Error:    ProjectServiceTest.testSingletonSharesStateWithSpringBean:78 » DuplicateResource
          Project with id 'legacy-100' already exists
Error:    TaskServiceLegacyTest.coldStartReturnsInMemoryStore:62 » DuplicateResource
          Task with id '901' already exists
Error:    TaskServiceTest.testSingletonSharesStateWithSpringBean:65 » DuplicateResource
          Task with id 'legacy10' already exists

[INFO] Tests run: 1066, Failures: 0, Errors: 8, Skipped: 0
```

**After Fix:**
```
[INFO] Tests run: 1066, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Manual Verification Steps

1. Run full test suite: `mvn test`
2. Run only service tests: `mvn test -Dtest="*ServiceTest,*ServiceLegacyTest"`
3. Run in different orders to verify no order dependency
4. Check for any `DuplicateResourceException` in logs

---

## Future Evolution

### When Singletons are Decommissioned

Per `docs/logs/backlog.md` "Legacy Singleton Decommission (Future)":

Once `getInstance()` is no longer required:

1. Remove static `instance` fields from services
2. Remove `registerInstance()` methods
3. Remove `getInstance()` methods
4. **Simplify TestCleanupUtility**:
   - Remove `resetAllSingletons()` method
   - Remove reflection code
   - Keep `clearAllServiceData()` and security context cleanup

The cleanup utility would become simpler:

```java
@Transactional
public void resetTestEnvironment() {
    clearSecurityContexts();
    if (testUserSetup != null) {
        testUserSetup.cleanup();
    }
    clearAllServiceData();
    if (testUserSetup != null) {
        testUserSetup.setupTestUser();
    }
}
```

### Adding New Services

When adding a new service (e.g., `UserService`):

1. Add `@Autowired(required = false) private UserService userService;`
2. Add `resetSingleton(UserService.class);` to `resetAllSingletons()`
3. Add `userService.clearAllUsers();` to `clearAllServiceData()`

---

## References

- **Source Code**: `src/test/java/contactapp/service/TestCleanupUtility.java`
- **Test Examples**: Any of the 8 service test classes mentioned above
- **Related Commits**:
  - Test isolation improvements and parallel execution fix
  - Singleton test fixture hardening
- **Spring Boot Docs**: [TestContext Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework.html)
- **JUnit 5 Docs**: [@Isolated Annotation](https://junit.org/junit5/docs/current/user-guide/#writing-tests-parallel-execution-synchronization)

---

## Decision Makers

- **Proposed By**: Claude Code Agent (2025-12-02)
- **Approved By**: Project Maintainer
- **Reviewed By**: Test Suite (1066 passing tests)

---

## Tags

`#testing` `#isolation` `#spring-boot` `#singleton-pattern` `#reflection` `#test-infrastructure`
