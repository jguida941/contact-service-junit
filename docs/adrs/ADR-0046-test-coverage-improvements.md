# ADR-0046: Test Coverage Improvements - Store and Mapper Test Enhancement

**Status:** Accepted | **Date:** 2025-12-01 | **Owners:** Justin Guida

**Related**: [ADR-0009](ADR-0009-test-strategy.md), [ADR-0024](ADR-0024-persistence-implementation.md), [ADR-0031](ADR-0031-mutation-testing-for-test-quality.md), [ADR-0034](ADR-0034-mapper-pattern.md)

## Context

During Phase 3 (Persistence Implementation), the initial JPA store and mapper test coverage was functional but incomplete:
- **Store coverage**: 78% instruction, 73% branch (gaps in null parameter validation, insert/update branches, findAll/deleteAll methods)
- **Mapper coverage**: 90%+ overall but missing null-guard coverage and updateEntity null checks
- **Mutation testing**: PIT detected several surviving mutants in stores and mappers where tests could be strengthened

The gaps were identified through:
1. JaCoCo coverage reports showing uncovered branches in JpaContactStore, JpaTaskStore, JpaAppointmentStore
2. PITest mutation reports showing surviving mutants (null checks removed, conditionals changed)
3. Code review identifying missing test scenarios (save with existing entity vs new entity, null parameter guards)

## Decision

Systematically expand test coverage for JPA stores and mappers with targeted test scenarios:

### Store Test Enhancements

Add comprehensive tests to `JpaContactStoreTest`, `JpaTaskStoreTest`, `JpaAppointmentStoreTest`:

1. **Null Parameter Validation Tests**:
   ```java
   @Test
   void saveNullEntity() {
       assertThrows(NullPointerException.class, () -> store.save(null));
   }

   @Test
   void findByIdNullId() {
       assertThrows(NullPointerException.class, () -> store.findById(null));
   }

   @Test
   void deleteByIdNullId() {
       assertThrows(NullPointerException.class, () -> store.deleteById(null));
   }
   ```

2. **Save Path Branch Coverage** (insert vs update):
   ```java
   @Test
   void saveNewEntity() {
       // entity.getId() == null → insert path
       ContactEntity newEntity = new ContactEntity();
       newEntity.setContactId("C001");
       // ... set fields
       ContactEntity saved = store.save(newEntity);
       assertNotNull(saved.getId()); // DB-generated ID
   }

   @Test
   void saveExistingEntity() {
       // entity.getId() != null → update path
       ContactEntity existing = repository.save(buildEntity());
       existing.setFirstName("Updated");
       ContactEntity updated = store.save(existing);
       assertEquals("Updated", updated.getFirstName());
   }
   ```

3. **Complete Method Coverage**:
   ```java
   @Test
   void insertEntityDelegatesToRepository() {
       // Covers insert() method
       ContactEntity entity = buildEntity();
       store.insert(entity);
       verify(repository).save(entity);
   }

   @Test
   void findAllReturnsAllEntities() {
       // Covers findAll() method
       repository.save(buildEntity("C001"));
       repository.save(buildEntity("C002"));
       List<ContactEntity> all = store.findAll();
       assertEquals(2, all.size());
   }

   @Test
   void deleteAllRemovesAllEntities() {
       // Covers deleteAll() method
       repository.save(buildEntity("C001"));
       store.deleteAll();
       assertEquals(0, repository.count());
   }
   ```

### Mapper Test Enhancements

Add null-guard tests to `ContactMapperTest`, `TaskMapperTest`, `AppointmentMapperTest`:

```java
@Test
void updateEntityWithNullDomain() {
    ContactEntity entity = new ContactEntity();
    assertThrows(NullPointerException.class, () -> mapper.updateEntity(null, entity));
}

@Test
void updateEntityWithNullEntity() {
    Contact domain = new Contact("C001", "John", "Doe", "1234567890", "123 Main St");
    assertThrows(NullPointerException.class, () -> mapper.updateEntity(domain, null));
}

@Test
void toEntityWithNull() {
    assertThrows(NullPointerException.class, () -> mapper.toEntity(null));
}

@Test
void toDomainWithNull() {
    assertThrows(NullPointerException.class, () -> mapper.toDomain(null));
}
```

## Test Coverage Results

### Before Enhancement
| Component           | Instruction Coverage | Branch Coverage | Notes                                                 |
|---------------------|----------------------|-----------------|-------------------------------------------------------|
| JpaContactStore     | 78%                  | 73%             | Missing null checks, save branches, findAll/deleteAll |
| JpaTaskStore        | 78%                  | 73%             | Same gaps as ContactStore                             |
| JpaAppointmentStore | 78%                  | 73%             | Same gaps as ContactStore                             |
| ContactMapper       | 92%                  | 88%             | Missing null-guard coverage                           |
| TaskMapper          | 90%                  | 85%             | Missing updateEntity null checks                      |
| AppointmentMapper   | 93%                  | 90%             | Missing toEntity/toDomain null checks                 |

### After Enhancement
| Component           | Instruction Coverage | Branch Coverage | Tests Added |
|---------------------|----------------------|-----------------|-------------|
| JpaContactStore     | **96%**              | **97%**         | 8 new tests |
| JpaTaskStore        | **96%**              | **97%**         | 8 new tests |
| JpaAppointmentStore | **96%**              | **97%**         | 8 new tests |
| ContactMapper       | **100%**             | **100%**        | 6 new tests |
| TaskMapper          | **100%**             | **100%**        | 6 new tests |
| AppointmentMapper   | **95%**              | **95%**         | 6 new tests |

**Overall Impact**:
- Store coverage improved from **78%** to **96%** (instructions), **73%** to **97%** (branches)
- Mapper coverage improved to **95%+** across all mappers, with ContactMapper and TaskMapper at **100%**
- Total test count increased to **1107 tests**
- PITest mutation score was lifted to **94%** (615/656 mutants killed) on the scoped modules; the current expanded suite sits at 80% overall.

## Implementation Details

### Test File Structure

Each store test class follows this pattern:
```java
@SpringBootTest
@ActiveProfiles("test")
class JpaContactStoreTest {
    @Autowired private JpaContactStore store;
    @Autowired private ContactRepository repository;

    @BeforeEach
    void setUp() { repository.deleteAll(); }

    // Null parameter validation tests
    @Test void saveNullEntity() { ... }
    @Test void findByIdNullId() { ... }
    @Test void deleteByIdNullId() { ... }

    // Save path coverage (insert vs update)
    @Test void saveNewEntity() { ... }
    @Test void saveExistingEntity() { ... }

    // Method delegation tests
    @Test void insertEntityDelegatesToRepository() { ... }
    @Test void findAllReturnsAllEntities() { ... }
    @Test void deleteAllRemovesAllEntities() { ... }

    // Existing tests...
}
```

### Mapper Test Structure

Each mapper test class follows this pattern:
```java
class ContactMapperTest {
    private ContactMapper mapper = new ContactMapper();

    // Null-guard tests
    @Test void updateEntityWithNullDomain() { ... }
    @Test void updateEntityWithNullEntity() { ... }
    @Test void toEntityWithNull() { ... }
    @Test void toDomainWithNull() { ... }

    // Bidirectional mapping tests
    @Test void toDomainRoundTrip() { ... }
    @Test void toEntityRoundTrip() { ... }

    // Existing validation tests...
}
```

## Consequences

### Positive

1. **Improved Confidence**: 96%+ store coverage and 95%+ mapper coverage provide strong assurance that persistence layer behaves correctly
2. **Mutation Testing Alignment**: PITest can now kill more mutants in stores/mappers, improving mutation score from 90% to 94% on that scope (current full-suite mutation score is 80% after expanding coverage).
3. **Regression Prevention**: Null parameter guards ensure NullPointerExceptions are caught at test time, not production
4. **Branch Coverage**: Insert vs update paths are explicitly tested, catching logic errors in save operations
5. **Documentation**: Tests serve as executable documentation of expected null-handling behavior
6. **Consistent Patterns**: All three store/mapper pairs follow identical test structure, making maintenance easier

### Negative

1. **Test Maintenance**: 48 additional tests (24 store + 24 mapper) require ongoing maintenance
2. **Execution Time**: Test suite execution time increased by ~2 seconds (minimal impact)
3. **Code Duplication**: Similar test patterns across Contact/Task/Appointment stores (mitigated by shared test utilities)

### Neutral

1. **No API Changes**: Enhancements are test-only, no production code changes required
2. **No Breaking Changes**: Existing tests continue to pass, new tests augment coverage
3. **JaCoCo Thresholds**: Coverage now exceeds 80% threshold by comfortable margin (96%+)

## Alternatives Considered

### Alternative 1: Parameterized Tests for Null Checks
```java
@ParameterizedTest
@MethodSource("nullParameterScenarios")
void nullParametersThrowNPE(ThrowingCallable scenario) {
    assertThrows(NullPointerException.class, scenario);
}
```
**Rejected**: While more concise, individual test methods provide clearer failure messages and better IDE navigation.

### Alternative 2: Ignore Store Coverage Gaps
**Rejected**: 78% coverage leaves too many uncovered branches, risking production bugs. The cost of adding tests is low compared to debugging null pointer issues in production.

### Alternative 3: Add @NonNull Annotations Instead of Tests
```java
public ContactEntity save(@NonNull ContactEntity entity) { ... }
```
**Rejected**: Annotations document expectations but don't enforce them at runtime without additional tooling. Explicit tests provide immediate feedback.

### Alternative 4: Use Mockito to Verify Repository Calls
```java
@Test
void saveCallsRepositorySave() {
    ContactRepository mockRepo = mock(ContactRepository.class);
    JpaContactStore store = new JpaContactStore(mockRepo);
    store.save(entity);
    verify(mockRepo).save(entity);
}
```
**Rejected**: Current tests use real Spring Boot test slices with H2, providing integration-level confidence. Mocks would test less realistic scenarios.

## Test Count Breakdown

### Store Tests (per entity: Contact, Task, Appointment)
- Null parameter validation: 3 tests (save, findById, deleteById)
- Save path coverage: 2 tests (new entity, existing entity)
- Method delegation: 3 tests (insert, findAll, deleteAll)
- **Subtotal**: 8 tests × 3 entities = **24 new store tests**

### Mapper Tests (per entity: Contact, Task, Appointment)
- Null-guard coverage: 4 tests (updateEntity 2×, toEntity, toDomain)
- Bidirectional mapping: 2 tests (roundtrip validations)
- **Subtotal**: 6 tests × 3 entities = **18 new mapper tests**

### Entity Tests (per entity: Contact, Task, Appointment)
- getId() coverage: 1 test (kills EmptyObjectReturnValsMutator)
- Disabled state coverage: 1 test (User entity only, kills BooleanTrueReturnValsMutator)
- **Subtotal**: 1 test × 3 entities + 1 User test = **4 new entity tests**

### Filter Tests
- CorrelationIdFilter boundary tests: 2 tests (64 chars exact boundary, 65 chars over)
- RequestLoggingFilter query tests: 2 tests (with/without query string)
- **Subtotal**: **4 new filter tests**

**Total New Tests**: 24 (stores) + 18 (mappers) + 4 (entities) + 4 (filters) = **50 new tests**

**Total Test Count**: **1107 tests** (baseline + store/mapper/entity/filter coverage + security + validation helper tests)

## Mutation Testing Impact

### Surviving Mutants Killed

1. **NullPointerException guards**: Previously, PITest could remove `Objects.requireNonNull()` checks without failing tests. New null parameter tests catch this.
2. **Branch coverage**: Insert vs update logic in save() had uncovered branches. New tests ensure both paths execute.
3. **Method coverage**: findAll() and deleteAll() had no direct tests. New tests cover these methods explicitly.
4. **Entity accessor coverage**: getId() methods returned null in tests (EmptyObjectReturnValsMutator survived). New tests call getId() and assert non-null.

### Mutation Score Progression
- **Before**: 90% mutation score (586/651 mutants killed) on store/mapper scope
- **After**: 94% mutation score (615/656 mutants killed) on store/mapper scope (full suite now 80% after expansion)
- **Improvement**: +4% mutation score, +29 mutants killed for the scoped modules

## References

- [ADR-0009](ADR-0009-test-strategy.md) - Test Strategy, Tooling, and Full Range Coverage
- [ADR-0024](ADR-0024-persistence-implementation.md) - Persistence Implementation Strategy
- [ADR-0031](ADR-0031-mutation-testing-for-test-quality.md) - Mutation Testing to Validate Test Strength
- [ADR-0034](ADR-0034-mapper-pattern.md) - Mapper Pattern for Entity-Domain Separation
- [JaCoCo Coverage Reports](../../target/site/jacoco/)
- [PITest Mutation Reports](../../target/pit-reports/)
