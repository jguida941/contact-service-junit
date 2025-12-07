# ADR-0053: Timezone-Safe Date Handling (Frontend and Backend)

## Status

Accepted

## Date

2025-12-06 (Updated: Backend Clock injection added)

## Context

A critical bug was discovered where task due dates were displaying one day earlier than selected. When a user selected December 6th, the task would show December 5th.

### Root Cause Analysis

The bug occurred in the date formatting functions:

```typescript
// BUGGY CODE - DO NOT USE
function formatDueDate(dateString: string): string {
  return format(new Date(dateString), 'PPP');
}
```

When JavaScript's `new Date()` receives a date-only string like `"2025-12-06"`, it parses it as **UTC midnight** (2025-12-06T00:00:00Z). When this Date object is then displayed in a timezone with a negative UTC offset (like EST = UTC-5), the local time becomes:

- UTC: 2025-12-06 00:00:00
- EST: 2025-12-05 19:00:00 (7 PM on December **5th**)

This caused dates to shift backwards by one day for users in the Americas and other negative UTC offset timezones.

### Why Tests Didn't Catch This

1. **CI runs in UTC**: Most CI servers use UTC timezone, where this bug doesn't manifest
2. **Valid but wrong**: The date is technically valid—just shifted by one day
3. **Developer timezone variance**: Developers in UTC+0 or positive offsets never see the bug
4. **Time-of-day dependency**: The bug is more noticeable at certain times of day

## Decision

Use `parseISO()` from date-fns instead of `new Date()` for all API date strings.

### Implementation

Created centralized date utilities in `ui/contact-app/src/lib/dateUtils.ts`:

```typescript
import { format, parseISO } from 'date-fns';

export function formatDateSafe(dateString: string | null | undefined): string {
  if (!dateString) return 'No date';
  try {
    return format(parseISO(dateString), 'PPP');
  } catch {
    return dateString;
  }
}
```

### Why parseISO Works

`parseISO("2025-12-06")` parses the date as **local midnight**, not UTC midnight:

- Input: "2025-12-06"
- Result: 2025-12-06 00:00:00 in the user's local timezone
- Display: December 6th (correct!)

## Consequences

### Positive

- Dates display correctly in all timezones
- Centralized utilities reduce code duplication
- Comprehensive tests guard against regression
- Educational documentation prevents future mistakes

### Negative

- Slightly larger bundle (parseISO import), negligible impact
- Developers must remember to use utilities, not raw `new Date()`

### Testing Strategy

Created `dateUtils.test.ts` with specific timezone regression tests:

```typescript
it('December 6th stays December 6th (not 5th)', () => {
  const date = parseDateSafe('2025-12-06');
  expect(date!.getDate()).toBe(6);
});

it('January 1st stays January 1st (not Dec 31st)', () => {
  const date = parseDateSafe('2025-01-01');
  expect(date!.getDate()).toBe(1);
});
```

## Guidelines for Future Development

### DO

- Use `formatDateSafe()` for date-only strings from APIs
- Use `formatDateTimeSafe()` for datetime strings
- Use `parseDateSafe()` when you need a Date object
- Add timezone regression tests for new date handling code

### DON'T

- Never use `new Date(isoString)` directly for display
- Don't assume CI test results cover timezone edge cases
- Don't parse user-facing dates without `parseISO()`

---

## Backend: Clock Injection for Date Validation

### Additional Context

A related bug was discovered in backend date validation. When validating that a task's due date is "not in the past", the original code used `Clock.systemUTC()`:

```java
// BUGGY CODE - DO NOT USE
public static LocalDate validateDateNotPast(LocalDate date, String label) {
    return validateDateNotPast(date, label, Clock.systemUTC());
}
```

This caused tasks with "today" as the due date to be rejected for users in timezones behind UTC. For example:
- User's local time: December 6, 5:00 PM PST
- UTC time: December 7, 1:00 AM UTC
- `LocalDate.now(Clock.systemUTC())` returns December 7
- User enters December 6 → **rejected as "in the past"**

### Backend Decision

Implement production-grade Clock dependency injection:

1. **ClockConfig.java** - Spring `@Configuration` providing a configurable `Clock` bean
2. **Service-layer validation** - Date validation moved from domain to services where Clock is injected
3. **Configurable timezone** - `app.timezone` in application.yml

### Implementation

**ClockConfig.java:**
```java
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock(@Value("${app.timezone:}") String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return Clock.systemDefaultZone();
        }
        return Clock.system(ZoneId.of(timezone));
    }
}
```

**TaskService.java** (with Clock injection):
```java
@Service
public class TaskService {
    private final Clock clock;

    @Autowired
    public TaskService(TaskStore store, Clock clock) {
        this.clock = clock;
    }

    public boolean addTask(Task task) {
        // Validate using injected clock
        Validation.validateOptionalDateNotPast(task.getDueDate(), "Due Date", clock);
        // ... persist task
    }
}
```

**application.yml:**
```yaml
app:
  # Business timezone for date validation
  # Examples: America/New_York, America/Los_Angeles, UTC
  timezone: ${APP_TIMEZONE:}
```

### Architecture: Separation of Concerns

| Layer | Responsibility | Clock Access |
|-------|---------------|--------------|
| **Domain** | Structural validation (length, null checks) | None |
| **Service** | Business rules (date not in past) | Injected Clock |
| **Config** | Clock bean configuration | Environment vars |

### Why This Approach?

1. **Testability** - Tests can inject fixed clocks for deterministic behavior
2. **Configurability** - Timezone can be set per environment via `APP_TIMEZONE`
3. **Proper DI** - No hardcoded time sources; Clock is a first-class dependency
4. **Multi-tenant ready** - Pattern extends to per-user timezones if needed

### Backend Guidelines

**DO:**
- Inject `Clock` via constructor in services that need time-sensitive validation
- Use `Validation.validateOptionalDateNotPast(date, label, clock)` (3-arg version)
- Configure `APP_TIMEZONE` environment variable in production

**DON'T:**
- Don't use `Clock.systemUTC()` for user-facing date validation
- Don't validate "not in past" in domain objects (no Clock access)
- Don't hardcode timezone; use configuration

## References

- [date-fns parseISO documentation](https://date-fns.org/docs/parseISO)
- [MDN Date.parse() timezone behavior](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/parse)
- Stack Overflow: "JavaScript date off by one day" (common issue)
- [Java Clock documentation](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/Clock.html)
- Spring Framework: Dependency Injection best practices
