# ContactService singleton and storage

(Related: [ADR-0002](../../adrs/ADR-0002-contact-service-storage.md), [ContactService.java](../../src/main/java/contactapp/ContactService.java))

File: docs/design-notes/notes/contact-service-storage-notes.md

## What problem this solves
- The app needs one central list of contacts so every caller sees the same state.
- A singleton keeps the store in one place instead of forcing callers to pass maps around.

## What the design is
- `ContactService` is a singleton accessed via `getInstance()`.
- It owns a `ConcurrentHashMap<String, Contact>` called `database`.
- Service API:
    - `addContact(Contact)` uses `putIfAbsent` to enforce unique IDs and returns `boolean`.
    - `deleteContact(String id)` validates the id then calls `remove`, returning `boolean`.
    - `updateContact(String id, ...)` uses `computeIfPresent` for thread-safe atomic lookup + update, then delegates to `Contact.update(...)` so validation stays centralized.
    - `getDatabase()` returns defensive copies of each Contact (via `copy()`) in an unmodifiable map, preventing external mutation of internal state.
    - `clearAllContacts()` wipes the store so tests can reset between runs.

## Why this design
### Why `ConcurrentHashMap`
- Acts like a normal `HashMap` but allows concurrent access without explicit locking for get/put/remove.
- O(1) average time for add/update/delete operations.

### Why `putIfAbsent`
- Combines the uniqueness check and insert into one atomic call.
- If the id is new, it inserts and returns `null`, so we know the add succeeded.
- If the id exists, it leaves the map untouched and we return `false`.

### Why `computeIfPresent` for updates
- Using `get()` followed by `update()` creates a race condition window where another thread could delete the entry between the two calls.
- `computeIfPresent` performs the lookup and update as a single atomic operation inside the map's internal locking.
- Matches the pattern used by `AppointmentService` for consistency across all services.

### Why defensive copies and `clearAllContacts`
- If `getDatabase()` returned the real `database` or just a shallow `Map.copyOf`, a caller could mutate the Contact objects and bypass the service API rules.
  That is why we return defensive copies of each Contact (via `Contact.copy()`) in an unmodifiable map.
  Callers can read entries but any mutation only affects the copy, not the internal state; any attempt to `put` or `clear` throws `UnsupportedOperationException`.
- `clearAllContacts()` exists because the service is a singleton shared across tests.
  Without it, a contact added in one JUnit test would still be present in the next test.
  Tests call `ContactService.getInstance().clearAllContacts()` (usually in `@BeforeEach`) to guarantee a clean database every time.
  The method is **package-private** to prevent accidental calls from production code outside the `contactapp` package while still allowing test access (tests reside in the same package).

### Why booleans for service methods
- `add/delete/update` returning `boolean` keeps the API simple: success vs duplicate/missing.
- Tests assert on those booleans to cover the duplicate/missing branches without needing exceptions for normal control flow.
