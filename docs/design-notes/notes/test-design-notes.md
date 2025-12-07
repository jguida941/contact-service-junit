# Test design and tooling

(Related: [ContactTest.java](../../../src/test/java/contactapp/domain/ContactTest.java), [ValidationTest.java](../../../src/test/java/contactapp/domain/ValidationTest.java), [ContactServiceTest.java](../../../src/test/java/contactapp/service/ContactServiceTest.java))

File: docs/design-notes/notes/test-design-notes.md

## Why the tests are split
- `ContactTest` covers the `Contact` class (constructor, setters, update helper) so we know the object enforces all rules, including invalid update atomicity.
- `ValidationTest` isolates the helper methods to verify boundary cases (min/max lengths, blank inputs) without creating an entire `Contact`.
- `ContactServiceTest` focuses on the service API (add/delete/update behavior, singleton reset).
- `TaskTest`/`TaskServiceTest` mirror the Contact coverage now that Task is implemented: constructor trimming, invalid inputs (including update atomicity), singleton behavior, and CRUD flows.

## Why AssertJ and parameterized tests
- AssertJ (`assertThat`, `assertThatThrownBy`) reads like plain English: `assertThat(contact.getFirstName()).isEqualTo("Alice")` is easy to follow and can chain multiple checks.
- Parameterized tests with `@CsvSource` cover many invalid input combinations in one method, reducing copy/paste and making it simple to add new cases.
  - Example: `ContactTest.testFailedCreation(...)` lists invalid inputs and expected error messages in one place instead of writing dozens of separate test methods.

## How this helps
- Each test class has a clear responsibility, so failures pinpoint whether the issue is in the domain, validation helper, or service layer.
- Surefire automatically runs every `*Test` class during `mvn verify`, so even newly added tests are picked up without manual configuration.
- The fluent assertions and parameterized inputs keep the suite small but expressive, making it easy for instructors or teammates to see which cases are covered.

## Test Isolation and Cleanup (Added 2025-12-02)

### The Problem We Solved
Early test suite development revealed a critical isolation issue: static singleton instances (`ContactService.getInstance()`, etc.) persisted across test executions while Spring Boot reused the same `ApplicationContext` for performance. This caused `DuplicateResourceException` failures when tests created resources with the same IDs - the errors were **order-dependent** and only appeared when running the full suite.

### Centralized Cleanup Solution
Created `TestCleanupUtility` component (in `contactapp.service` package) to enforce proper cleanup order:

```java
@Autowired
private TestCleanupUtility testCleanup;

@BeforeEach
void reset() {
    testCleanup.resetTestEnvironment();
}
```

**What it does (in order):**
1. **Clear security contexts** - Both `SecurityContextHolder` and `TestSecurityContextHolder`
2. **Reset singletons via reflection** - Sets static `instance` fields to `null` before data clearing
3. **Clean up test users** - Deletes users first to satisfy FK constraints
4. **Clear all service data** - Calls package-private `clearAllContacts()`, `clearAllTasks()`, etc.
5. **Setup fresh test user** - Ready for test execution

**Why this order matters:** If we clear data before resetting singletons, the next test's service initialization calls `registerInstance()` which migrates old singleton data into the fresh JPA store, recreating the DuplicateResource errors.

### Impact
- ✅ Eliminated 8 test failures across service test suites
- ✅ All 1107 tests now run reliably without order dependencies
- ✅ Single line in `@BeforeEach` replaces manual cleanup boilerplate
- ✅ Consistent isolation pattern across all Spring Boot integration tests

### Related Documentation
- **ADR-0047**: Complete architectural rationale and alternatives considered
- **ADR-0009**: Updated test strategy referencing centralized cleanup
- **CHANGELOG.md**: Historical context of the fix

### When Adding New Services
If you create a new service (e.g., `UserService`) with the singleton pattern:
1. Add `@Autowired(required = false) private UserService userService;` to `TestCleanupUtility`
2. Add `resetSingleton(UserService.class);` to the `resetAllSingletons()` method
3. Add `userService.clearAllUsers();` to the `clearAllServiceData()` method

This ensures all service tests benefit from consistent cleanup without duplicating reflection code.

## Mutation Testing Strategy

### Why We Target InMemoryStore and JPA Paths Separately

The application maintains dual storage backends to support different deployment modes:
- **InMemoryStore**: Singleton-based storage used as fallback when JPA is unavailable
- **JPA persistence**: Full database-backed storage for production use

Both code paths must be tested because:
1. **Different failure modes**: InMemoryStore can encounter collection modification issues, while JPA has transaction and constraint failures
2. **Mutation coverage**: PITest generates mutations in both paths - leaving either untested creates false negatives where mutations survive
3. **Production correctness**: If the JPA layer fails, the system falls back to InMemoryStore, which must be equally robust

**Example from TaskService:**
- `save()` checks `store != null` and either calls `store.save()` (JPA path) or `inMemoryStore.save()` (fallback)
- Both branches need test coverage or mutations like "negate conditional" will survive

### Unit vs Integration Test Pattern

We use a two-tier testing approach to achieve comprehensive mutation coverage:

**Unit Tests (e.g., TaskServiceValidationTest):**
- Direct `new TaskService()` instantiation without Spring context
- Target **InMemoryStore code paths exclusively**
- Focus on validation logic, edge cases, and domain rules
- Fast execution (no database setup/teardown)
- Kill mutations in: input validation, business logic, fallback storage operations

**Integration Tests (e.g., TaskServiceTest):**
- Use `@SpringBootTest` and `@Autowired TaskService`
- Target **JPA persistence paths**
- Verify transaction boundaries, constraint enforcement, query correctness
- Kill mutations in: repository interactions, entity mapping, database operations

**Why both are needed:**
- Unit tests alone miss JPA-specific mutations (e.g., `Optional.orElseThrow()` in repository calls)
- Integration tests alone miss InMemoryStore mutations (the JPA store is injected, so fallback code never runs)
- Together they achieve 95%+ mutation coverage across all code paths

### Key Mutation Types to Target

PITest generates various mutation types. These are the most common in our service layer:

**1. VoidMethodCallMutator**
- **What it does**: Removes calls to void methods
- **Example**: `inMemoryStore.save(task)` becomes a no-op
- **How to kill**: Assert the side effect occurred (e.g., `findById()` returns the saved object)

```java
@Test
void testSave() {
    Task task = service.save(new Task(...));
    // This kills VoidMethodCallMutator by verifying save() had an effect
    assertThat(service.findById(task.getId())).isPresent();
}
```

**2. BooleanReturnValsMutator**
- **What it does**: Flips boolean return values (`true` becomes `false`, vice versa)
- **Example**: `existsById(id)` returns `!existsById(id)`
- **How to kill**: Assert on the actual boolean outcome, not just side effects

```java
@Test
void testDeleteNonExistentTask() {
    // This kills BooleanReturnValsMutator by checking the return value
    assertThat(service.deleteById(999L)).isFalse();
}
```

**3. NegateConditionalsMutator**
- **What it does**: Inverts conditionals (`==` becomes `!=`, `<` becomes `>=`, etc.)
- **Example**: `if (store != null)` becomes `if (store == null)`
- **How to kill**: Test both branches of every conditional

```java
// Test the true branch (store != null)
@Test
@SpringBootTest
void testSaveWithJpaStore() {
    // JPA store is injected, condition is true
    Task task = service.save(new Task(...));
    assertThat(task.getId()).isNotNull();
}

// Test the false branch (store == null)
@Test
void testSaveWithoutJpaStore() {
    // Direct instantiation, store is null, fallback activates
    TaskService service = new TaskService();
    Task task = service.save(new Task(...));
    assertThat(service.findById(task.getId())).isPresent();
}
```

**4. IncrementsMutator**
- **What it does**: Changes `++` to `--`, adds/subtracts 1 from arithmetic
- **How to kill**: Assert on exact counts or list sizes

**5. ConditionalsBoundaryMutator**
- **What it does**: Changes `<` to `<=`, `>` to `>=`
- **How to kill**: Test boundary values explicitly (e.g., max length strings)

### Identifying and Handling Equivalent Mutations

**Equivalent mutations** are code changes that don't affect program behavior. PITest can't distinguish these automatically, so they appear as "survived mutations" even with perfect tests.

**Common equivalent mutations in our codebase:**

**1. Return value of `save()` operations**
```java
public Task save(Task task) {
    boolean saved = inMemoryStore.save(task);
    return saved ? task : null; // PITest mutates: return !saved ? task : null
}
```
- If `save()` always returns `true`, the mutation is equivalent
- **How to handle**: Document in PITest exclusions or accept the survived mutation
- **Better approach**: Change signature to `public void save(Task task)` if return value is unused

**2. Guard conditions that are always true**
```java
if (task != null) {
    // do something
}
```
- If the method contract guarantees `task != null`, the check is defensive
- **How to handle**: Either remove the check or add a test that passes `null` (if allowed)

**3. Post-increment vs pre-increment when value is unused**
```java
counter++; // vs ++counter
```
- If the expression result isn't used, these are equivalent
- **How to handle**: Accept the mutation or refactor to `counter += 1` (not mutated by IncrementsMutator)

**Documenting equivalent mutations:**
Add comments to the test or maintain a `pitest-exclusions.txt` explaining why certain mutations are ignored:
```
# TaskService.save() always returns true from inMemoryStore, so boolean mutations are equivalent
contactapp.service.TaskService.save:45
```

### The Mutation Testing Workflow

**1. Write the test**
Focus on observable behavior (return values, exceptions, state changes):
```java
@Test
void testUpdateTaskStatus() {
    Task task = service.save(new Task("Title", "Description"));
    task.setStatus(TaskStatus.COMPLETED);
    Task updated = service.update(task.getId(), task);
    assertThat(updated.getStatus()).isEqualTo(TaskStatus.COMPLETED);
}
```

**2. Run PITest**
```bash
mvn test-compile org.pitest:pitest-maven:mutationCoverage
```
Check `target/pit-reports/index.html` for mutation coverage.

**3. Verify mutation killed**
Look for the specific line/mutator in the report:
- Green: Mutation killed (test failed when code was mutated)
- Red: Mutation survived (test passed even with mutated code)

**4. Iterate on survivors**
For each surviving mutation:
- **Understand the mutation**: What code change did PITest make?
- **Check if equivalent**: Is the mutation actually detectable?
- **Add/strengthen test**: If not equivalent, write a test that fails with the mutation
- **Re-run PITest**: Verify the new test kills the mutation

**Example iteration:**
```
SURVIVED: Negated conditional at TaskService.java:67
Original: if (store != null)
Mutated:  if (store == null)
```
Add test:
```java
@Test
void testFallbackWhenStoreIsNull() {
    TaskService service = new TaskService(); // store is null
    Task task = service.save(new Task("Test", "Desc"));
    assertThat(service.findById(task.getId())).isPresent();
}
```
Re-run: Mutation now killed.

### Mutation Testing Best Practices

1. **Target 85%+ mutation coverage** for critical business logic (services, domain models)
2. **Run PITest incrementally** on modified classes to get fast feedback
3. **Don't chase 100%**: Some equivalent mutations are unavoidable
4. **Use mutation testing to find test gaps**: Surviving mutations reveal untested code paths
5. **Integrate with CI**: Run PITest on PRs to prevent coverage regression (optional, can be slow)
6. **Focus on high-value mutations**: Conditional negations and boolean flips are more important than math mutations in business logic

### Related Documentation
- **ADR-0046**: Test Coverage Improvements (mutation testing adoption)
- **testing-strategy-notes.md**: Overall testing philosophy and goals
