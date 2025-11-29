package contactapp;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service responsible for managing {@link Contact} instances.
 *
 * It owns the in-memory storage for contacts and
 * exposes operations to add, update, and delete them.
 *
 * Implemented as a singleton so the application uses a single shared
 * instance, obtained through {@link #getInstance()}.
 */
public final class ContactService {

    /**
     * Singleton instance, created lazily on first access.
     */
    private static ContactService instance;

    /**
     * In-memory storage for contacts keyed by contactId.
     * Kept as an instance field so ContactService owns its own state.
     *
     * In this project we use a single shared instance via getInstance().
     * Because the state lives on the instance (not static), the class could be
     * refactored later to support multiple instances if requirements change.
     *
     * ConcurrentHashMap gives O(1) average time for add, lookup, update,
     * and delete, and is safe for concurrent access.
     */
    private final Map<String, Contact> database = new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent direct instantiation.
     * Future initialization logic for the in-memory store can go here.
     */
    private ContactService() {
        // Future placeholder for initialization logic
    }

    /**
     * Returns the global {@code ContactService} instance.
     *
     * The method is synchronized so that only one instance is created
     * even if multiple threads call it at the same time.
     *
     * SpotBugs warning MS_EXPOSE_REP is suppressed on purpose because this
     * method must return the shared instance. The internal {@code database}
     * map remains private and is never exposed directly.
     *
     * @return the singleton {@code ContactService} instance
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton intentionally exposes shared instance for application use")
    public static synchronized ContactService getInstance() {
        if (instance == null) {
            instance = new ContactService();
        }
        return instance;

    }

    /**
     * Adds a contact if the contactId is not already present.
     *
     * Using {@link java.util.concurrent.ConcurrentHashMap#putIfAbsent(Object, Object)}
     * makes the uniqueness check and insert a single atomic operation on the
     * underlying ConcurrentHashMap. This avoids the race condition you would get
     * with a separate containsKey(...) check followed by put(...).
     *
     * @param contact the contact to add; must not be {@code null}
     * @return {@code true} if the contact was added, {@code false} if the id already exists
     * @throws IllegalArgumentException if {@code contact} is {@code null}
     */
    public boolean addContact(final Contact contact) {
        if (contact == null) {
            throw new IllegalArgumentException("contact must not be null");
        }
        return database.putIfAbsent(contact.getContactId(), contact) == null;
    }

    /**
     * Deletes a contact by id.
     *
     * The provided id is validated and trimmed before removal so callers can pass
     * values like " 123 " and still target the stored entry for "123".
     *
     * @param contactId the id of the contact to remove; must not be null or blank
     * @return true if a contact was removed, false if no contact existed
     * @throws IllegalArgumentException if contactId is null or blank
     */
    public boolean deleteContact(final String contactId) {
        Validation.validateNotBlank(contactId, "contactId");
        return database.remove(contactId.trim()) != null;
    }

    /**
     * Updates an existing contact's mutable fields by id.
     *
     * @param contactId the id of the contact to update
     * @param firstName new first name
     * @param lastName  new last name
     * @param phone     new phone number
     * @param address   new address
     * @return true if the contact exists and was updated, false if no contact with that id exists
     *
     * Additional implementation notes:
     *  - The contactId is validated and trimmed before lookup, keeping behavior
     *    consistent with {@link #deleteContact(String)}.
     *  - Uses {@link ConcurrentHashMap#computeIfPresent} for thread-safe atomic lookup and update.
     *  - If any value is invalid, {@link Contact#update(String, String, String, String)} throws before
     *    the contact is changed, so callers never see a half-updated record (atomic update behavior).
     *
     * @throws IllegalArgumentException if any new field value is invalid
     *                                  (propagated from {@code Contact#update(String, String, String, String)})
     */
    public boolean updateContact(
            final String contactId,
            final String firstName,
            final String lastName,
            final String phone,
            final String address) {
        Validation.validateNotBlank(contactId, "contactId");
        final String normalizedId = contactId.trim();

        // computeIfPresent is atomic: lookup + update happen as one operation
        return database.computeIfPresent(normalizedId, (key, contact) -> {
            contact.update(firstName, lastName, phone, address);
            return contact;
        }) != null;
    }

    /**
     * Returns a read-only snapshot of the contact store (contacts keyed by contactId).
     *
     * Tests use this to confirm add, delete, and update methods mutate state as expected.
     * Returns defensive copies of each Contact to prevent external mutation of internal
     * state. Modifications to the returned contacts do not affect the contacts stored
     * in the service.
     *
     * In production code we would usually expose behaviors rather than the raw map,
     * but exposing the snapshot keeps the test suite simple for this milestone.
     */
    public Map<String, Contact> getDatabase() {
        return database.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().copy()));
    }

    /**
     * Removes every contact from the in-memory store.
     *
     * <p>Package-private to limit usage to test code within the same package.
     * This prevents accidental calls from production code outside the package.
     */
    void clearAllContacts() {
        database.clear();
    }
}
