package contactapp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing {@link Contact} instances.
 * <p>
 * For this milestone it owns the in-memory storage for contacts
 * and will expose operations to add, update, and delete them.
 * <p>
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
     * In this project we use a single shared instance via getInstance(),
     * but this design still allows separate instances for tests if needed.
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
     * The method is synchronized so that only one instance is created
     * even if multiple threads call it at the same time.
     *
     * @return the singleton {@code ContactService} instance
     */
    public static synchronized ContactService getInstance() {
        if (instance == null) {
            instance = new ContactService();
        }
        return instance;

    }

    /**
     * Adds a contact if the contactId is not already present.
     * <br>
     * Using {@link java.util.concurrent.ConcurrentHashMap#putIfAbsent(Object, Object)}
     * makes the uniqueness check and insert a single atomic operation on the
     * underlying ConcurrentHashMap. This avoids the race condition you would get
     * with a separate containsKey(...) check followed by put(...).
     *
     * @param contact the contact to add; must not be {@code null}
     * @return {@code true} if the contact was added, {@code false} if the id already exists
     * @throws IllegalArgumentException if {@code contact} is {@code null}
     */
    public boolean addContact(Contact contact) {
        if (contact == null) {
            throw new IllegalArgumentException("contact must not be null");
        }
        return database.putIfAbsent(contact.getContactId(), contact) == null;
    }

    /**
     * Deletes a contact by id.
     *
     * @param contactId the id of the contact to remove; must not be null or blank
     * @return true if a contact was removed, false if no contact existed
     * @throws IllegalArgumentException if contactId is null or blank
     */
    public boolean deleteContact(String contactId) {
        Validation.validateNotBlank(contactId, "contactId");
        return database.remove(contactId) != null;
    }

    /**
     * Updates an existing contact's mutable fields by id.
     * Uses the Contact setters so all constructor validation rules still apply.
     *
     * @param contactId the id of the contact to update
     * @param firstName new first name
     * @param lastName  new last name
     * @param phone     new phone number
     * @param address   new address
     * @return true if the contact exists and was updated, false if no contact with that id exists
     * @throws IllegalArgumentException if any new field value is invalid (from Contact setters)
     */
    public boolean updateContact(
            String contactId,
            String firstName,
            String lastName,
            String phone,
            String address) {
        Contact contact = database.get(contactId);
        if (contact == null) {
            return false;
        }

        // Reuse Contact's setter validation
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setPhone(phone);
        contact.setAddress(address);
        return true;
    }

    /**
     * Returns the internal contact store (contacts keyed by contactId).
     * <p>
     * This exists so the CS320 tests can directly verify that
     * addContact, deleteContact, and updateContact change the
     * in-memory state as expected.
     * <p>
     * In a real application we would normally keep this map private
     * and only expose behavior through service methods, not through
     * a raw Map getter.
     */
    public Map<String, Contact> getDatabase() {
        return database;
    }
}