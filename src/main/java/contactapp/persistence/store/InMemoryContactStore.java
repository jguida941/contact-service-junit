package contactapp.persistence.store;

import contactapp.domain.Contact;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Simple in-memory store used when {@link contactapp.service.ContactService#getInstance()}
 * is accessed before Spring initializes the JPA infrastructure.
 */
public class InMemoryContactStore implements ContactStore {

    private final Map<String, Contact> database = new ConcurrentHashMap<>();

    @Override
    public boolean existsById(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("contactId must not be null");
        }
        return database.containsKey(id);
    }

    @Override
    public void save(final Contact aggregate) {
        if (aggregate == null) {
            throw new IllegalArgumentException("contact aggregate must not be null");
        }
        final String contactId = aggregate.getContactId();
        if (contactId == null) {
            throw new IllegalArgumentException("contactId must not be null");
        }
        final Contact copy = Optional.ofNullable(aggregate.copy())
                .orElseThrow(() -> new IllegalStateException("contact copy must not be null"));
        final Contact existing = database.putIfAbsent(contactId, copy);
        if (existing != null) {
            throw new DataIntegrityViolationException("Contact with id '" + contactId + "' already exists");
        }
    }

    @Override
    public Optional<Contact> findById(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("contactId must not be null");
        }
        final Contact contact = database.get(id);
        return contact == null ? Optional.empty() : Optional.of(contact.copy());
    }

    /**
     * Returns all contacts without user filtering.
     *
     * @deprecated Since 1.0, will be removed in 2.0. This method bypasses user isolation
     *             and returns all records regardless of ownership. Use
     *             {@link #findAllByUserId(Long)} for user-scoped queries instead.
     *             See ADR-0054 M-8 for security rationale.
     * @return all contacts in the store
     */
    @Override
    @Deprecated(since = "1.0", forRemoval = true)
    public List<Contact> findAll() {
        final List<Contact> contacts = new ArrayList<>();
        database.values().forEach(contact -> contacts.add(contact.copy()));
        return contacts;
    }

    @Override
    public boolean deleteById(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("contactId must not be null");
        }
        return database.remove(id) != null;
    }

    @Override
    public void deleteAll() {
        database.clear();
    }
}
