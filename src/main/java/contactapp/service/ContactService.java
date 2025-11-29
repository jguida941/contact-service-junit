package contactapp.service;

import contactapp.domain.Contact;
import contactapp.domain.Validation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Service responsible for managing {@link Contact} instances.
 *
 * <p>Owns the in-memory storage for contacts and exposes operations
 * to add, update, and delete them.
 *
 * <h2>Spring Integration</h2>
 * <p>Annotated with {@link Service} so Spring can manage the bean lifecycle.
 * The singleton pattern via {@link #getInstance()} is retained for backward
 * compatibility with existing tests and non-Spring callers.
 *
 * <h2>Thread Safety</h2>
 * <p>Uses {@link ConcurrentHashMap} for O(1) thread-safe operations.
 * Updates use {@code computeIfPresent} for atomic lookup + update.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Singleton pattern retained for backward compatibility</li>
 *   <li>{@code @Service} added for Spring DI in controllers (Phase 2)</li>
 *   <li>Defensive copies returned from {@link #getDatabase()}</li>
 *   <li>{@link #clearAllContacts()} is package-private for test isolation</li>
 * </ul>
 *
 * @see Contact
 * @see Validation
 */
@Service
public final class ContactService {

    /**
     * Singleton instance, created lazily on first access.
     * Retained for backward compatibility with existing tests.
     */
    private static ContactService instance;

    /**
     * In-memory storage for contacts keyed by contactId.
     *
     * <p>Static so all access paths (Spring DI and {@link #getInstance()}) share
     * the same backing store regardless of how many instances are created.
     *
     * <p>ConcurrentHashMap provides O(1) average time for add, lookup, update,
     * and delete, and is safe for concurrent access without external locking.
     */
    private static final Map<String, Contact> DATABASE = new ConcurrentHashMap<>();

    /**
     * Default constructor for Spring bean creation.
     *
     * <p>Public to allow Spring to instantiate the service via reflection.
     * For non-Spring usage, prefer {@link #getInstance()}.
     *
     * <p>Thread-safe: Registers this instance as the static singleton only if none
     * exists, preserving any data already added through {@link #getInstance()}.
     * This ensures Spring-created beans and legacy callers share the same backing store
     * regardless of initialization order.
     */
    public ContactService() {
        synchronized (ContactService.class) {
            if (instance == null) {
                instance = this;
            }
        }
    }

    /**
     * Returns the global {@code ContactService} singleton instance.
     *
     * <p>The method is synchronized so that only one instance is created
     * even if multiple threads call it at the same time.
     *
     * <p>Note: When using Spring DI (e.g., in controllers), prefer constructor
     * injection over this method. This exists for backward compatibility.
     * Both access patterns share the same instance and backing store.
     *
     * @return the singleton {@code ContactService} instance
     */
    @SuppressFBWarnings(
            value = "MS_EXPOSE_REP",
            justification = "Singleton intentionally exposes shared instance for backward compatibility")
    public static synchronized ContactService getInstance() {
        if (instance == null) {
            new ContactService();
        }
        return instance;
    }

    /**
     * Adds a contact if the contactId is not already present.
     *
     * <p>Uses {@link ConcurrentHashMap#putIfAbsent(Object, Object)} for atomic
     * uniqueness check and insert, avoiding race conditions.
     *
     * @param contact the contact to add; must not be {@code null}
     * @return {@code true} if the contact was added, {@code false} if the id already exists
     * @throws IllegalArgumentException if {@code contact} is {@code null}
     */
    public boolean addContact(final Contact contact) {
        if (contact == null) {
            throw new IllegalArgumentException("contact must not be null");
        }
        return DATABASE.putIfAbsent(contact.getContactId(), contact) == null;
    }

    /**
     * Deletes a contact by id.
     *
     * <p>The provided id is validated and trimmed before removal so callers
     * can pass values like " 123 " and still target the stored entry for "123".
     *
     * @param contactId the id of the contact to remove; must not be null or blank
     * @return true if a contact was removed, false if no contact existed
     * @throws IllegalArgumentException if contactId is null or blank
     */
    public boolean deleteContact(final String contactId) {
        Validation.validateNotBlank(contactId, "contactId");
        return DATABASE.remove(contactId.trim()) != null;
    }

    /**
     * Updates an existing contact's mutable fields by id.
     *
     * <p>Implementation notes:
     * <ul>
     *   <li>The contactId is validated and trimmed before lookup</li>
     *   <li>Uses {@link ConcurrentHashMap#computeIfPresent} for thread-safe atomic lookup and update</li>
     *   <li>If any value is invalid, {@link Contact#update} throws before the contact is changed</li>
     * </ul>
     *
     * @param contactId the id of the contact to update
     * @param firstName new first name
     * @param lastName  new last name
     * @param phone     new phone number
     * @param address   new address
     * @return true if the contact exists and was updated, false if no contact with that id exists
     * @throws IllegalArgumentException if any new field value is invalid
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
        return DATABASE.computeIfPresent(normalizedId, (key, contact) -> {
            contact.update(firstName, lastName, phone, address);
            return contact;
        }) != null;
    }

    /**
     * Returns a read-only snapshot of the contact store.
     *
     * <p>Returns defensive copies of each Contact to prevent external mutation
     * of internal state. Modifications to the returned contacts do not affect
     * the contacts stored in the service.
     *
     * @return unmodifiable map of contact defensive copies
     */
    public Map<String, Contact> getDatabase() {
        return DATABASE.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().copy()));
    }

    /**
     * Returns all contacts as a list of defensive copies.
     *
     * <p>Encapsulates the internal storage structure so controllers don't
     * need to access getDatabase() directly.
     *
     * @return list of contact defensive copies
     */
    public List<Contact> getAllContacts() {
        return DATABASE.values().stream()
                .map(Contact::copy)
                .toList();
    }

    /**
     * Finds a contact by ID.
     *
     * <p>The ID is validated and trimmed before lookup so callers can pass
     * values like " 123 " and still find the contact stored as "123".
     *
     * @param contactId the contact ID to search for
     * @return Optional containing a defensive copy of the contact, or empty if not found
     * @throws IllegalArgumentException if contactId is null or blank
     */
    public Optional<Contact> getContactById(final String contactId) {
        Validation.validateNotBlank(contactId, "contactId");
        final Contact contact = DATABASE.get(contactId.trim());
        return contact == null ? Optional.empty() : Optional.of(contact.copy());
    }

    /**
     * Removes every contact from the in-memory store.
     *
     * <p>Package-private to limit usage to test code within the same package.
     * This prevents accidental calls from production code outside the package.
     */
    void clearAllContacts() {
        DATABASE.clear();
    }
}
