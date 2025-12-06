# ADR-0053: Timezone-Safe Date Parsing in Frontend

## Status

Accepted

## Date

2025-12-06

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

## References

- [date-fns parseISO documentation](https://date-fns.org/docs/parseISO)
- [MDN Date.parse() timezone behavior](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Date/parse)
- Stack Overflow: "JavaScript date off by one day" (common issue)
