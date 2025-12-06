package contactapp.persistence.store;

import contactapp.domain.Contact;
import contactapp.persistence.entity.ContactEntity;
import contactapp.persistence.mapper.ContactMapper;
import contactapp.persistence.repository.ContactRepository;
import contactapp.security.User;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Primary {@link ContactStore} implementation backed by Spring Data JPA.
 *
 * <p>Supports per-user data isolation by filtering contacts based on the authenticated user.
 */
@Component
public class JpaContactStore implements ContactStore {

    private final ContactRepository repository;
    private final ContactMapper mapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Spring-managed singleton stores repository and mapper references; "
                    + "these are framework-managed beans with controlled lifecycle")
    public JpaContactStore(final ContactRepository repository, final ContactMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Checks if a contact exists for a specific user.
     *
     * @param id the contact ID
     * @param user the user who owns the contact
     * @return true if the contact exists for the given user
     */
    public boolean existsById(final String id, final User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return repository.existsByContactIdAndUser(id, user);
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean existsById(final String id) {
        throw new UnsupportedOperationException(
                "Use existsById(id, User) to enforce per-user authorization");
    }

    /**
     * Saves a contact for a specific user.
     *
     * @param aggregate the contact to save
     * @param user the user who owns the contact
     */
    @Transactional
    @Override
    public void save(final Contact aggregate, final User user) {
        if (aggregate == null) {
            throw new IllegalArgumentException("contact aggregate must not be null");
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        // Check if entity already exists to preserve user association on updates
        final Optional<ContactEntity> existing = repository.findByContactIdAndUser(
                aggregate.getContactId(), user);
        existing.ifPresentOrElse(
                entity -> {
                    mapper.updateEntity(entity, aggregate);
                    repository.save(entity);
                },
                () -> repository.save(mapper.toEntity(aggregate, user)));
    }

    /**
     * Inserts a brand-new contact and relies on database constraints to detect duplicates.
     *
     * @param aggregate the contact to create
     * @param user the owner
     */
    @Transactional
    public void insert(final Contact aggregate, final User user) {
        if (aggregate == null) {
            throw new IllegalArgumentException("contact aggregate must not be null");
        }
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        repository.save(mapper.toEntity(aggregate, user));
    }

    @Override
    @Deprecated(forRemoval = true)
    public void save(final Contact aggregate) {
        throw new UnsupportedOperationException(
                "Use save(Contact, User) instead. This method requires a User parameter.");
    }

    /**
     * Finds a contact by ID for a specific user.
     *
     * @param id the contact ID
     * @param user the user who owns the contact
     * @return optional containing the contact if found
     */
    public Optional<Contact> findById(final String id, final User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return repository.findByContactIdAndUser(id, user).map(mapper::toDomain);
    }

    @Override
    @Deprecated(forRemoval = true)
    public Optional<Contact> findById(final String id) {
        throw new UnsupportedOperationException(
                "Use findById(String, User) to enforce per-user authorization");
    }

    /**
     * Finds all contacts for a specific user.
     *
     * @param user the user who owns the contacts
     * @return list of contacts for the given user
     */
    public List<Contact> findAll(final User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return repository.findByUser(user).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Deprecated(since = "1.0", forRemoval = true)
    public List<Contact> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * Deletes a contact by ID for a specific user.
     *
     * @param id the contact ID
     * @param user the user who owns the contact
     * @return true if the contact was deleted, false if not found
     */
    public boolean deleteById(final String id, final User user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return repository.deleteByContactIdAndUser(id, user) > 0;
    }

    @Override
    @Deprecated(forRemoval = true)
    public boolean deleteById(final String id) {
        throw new UnsupportedOperationException(
                "Use deleteById(String, User) to enforce per-user authorization");
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }
}
