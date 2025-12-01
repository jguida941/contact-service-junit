package contactapp.persistence.store;

import contactapp.domain.Contact;
import contactapp.persistence.entity.ContactEntity;
import contactapp.persistence.mapper.ContactMapper;
import contactapp.persistence.repository.ContactRepository;
import contactapp.security.User;
import contactapp.support.TestUserFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link JpaContactStore}'s repository delegations so PIT can't remove them.
 */
@ExtendWith(MockitoExtension.class)
class JpaContactStoreTest {

    @Mock
    private ContactRepository repository;
    @Mock
    private ContactMapper mapper;

    private JpaContactStore store;

    @BeforeEach
    void setUp() {
        store = new JpaContactStore(repository, mapper);
    }

    @Test
    void existsById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] Validate the deprecated no-user method still throws to force callers to migrate.
        assertThatThrownBy(() -> store.existsById("c-1"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void existsById_withUserDelegatesToRepository() {
        final User owner = TestUserFactory.createUser("contact-store");
        when(repository.existsByContactIdAndUser("c-2", owner)).thenReturn(true);

        assertThat(store.existsById("c-2", owner)).isTrue();
    }

    /**
     * Covers the false branch so PIT can't flip the result of existsById(User).
     */
    @Test
    void existsById_withUserReturnsFalseWhenRepositoryReturnsFalse() {
        final User owner = TestUserFactory.createUser("contact-store-false");
        when(repository.existsByContactIdAndUser("missing", owner)).thenReturn(false);

        assertThat(store.existsById("missing", owner)).isFalse();
    }

    @Test
    void findById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] Guard rail for deprecated findById(String) until it is removed.
        assertThatThrownBy(() -> store.findById("c-3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void findById_withUserUsesMapper() {
        final User owner = TestUserFactory.createUser("contact-store-find");
        final ContactEntity entity = mock(ContactEntity.class);
        final Contact domain = new Contact("c-4", "Amy", "Lee", "1234567890", "Addr");
        when(repository.findByContactIdAndUser("c-4", owner)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(domain);

        assertThat(store.findById("c-4", owner)).contains(domain);
    }

    @Test
    void deleteById_withoutUserThrowsUnsupportedOperation() {
        // codeql[java/avoid-deprecated-apis] Legacy delete path intentionally throws to enforce tenant scoping.
        assertThatThrownBy(() -> store.deleteById("c-5"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deleteById_withUserUsesRepositoryReturnCode() {
        final User owner = TestUserFactory.createUser("contact-store-delete");
        when(repository.deleteByContactIdAndUser("c-6", owner)).thenReturn(1);

        assertThat(store.deleteById("c-6", owner)).isTrue();
    }

    @Test
    void deleteById_withUserReturnsFalseWhenNoRowsDeleted() {
        final User owner = TestUserFactory.createUser("contact-store-delete-none");
        when(repository.deleteByContactIdAndUser("nonexistent", owner)).thenReturn(0);

        assertThat(store.deleteById("nonexistent", owner)).isFalse();
    }

    // --- Null parameter validation tests ---

    @Test
    void existsById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.existsById("c-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void findById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.findById("c-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void findAll_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.findAll(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void deleteById_withNullUserThrowsIllegalArgument() {
        assertThatThrownBy(() -> store.deleteById("c-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    void save_withNullAggregateThrowsIllegalArgument() {
        final User owner = TestUserFactory.createUser("contact-store-null-agg");
        assertThatThrownBy(() -> store.save(null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contact aggregate must not be null");
    }

    @Test
    void save_withNullUserThrowsIllegalArgument() {
        final Contact contact = new Contact("c-1", "Amy", "Lee", "1234567890", "Addr");
        assertThatThrownBy(() -> store.save(contact, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    @Test
    @SuppressWarnings("deprecation")
    void save_withoutUserThrowsUnsupportedOperation() {
        final Contact contact = new Contact("c-1", "Amy", "Lee", "1234567890", "Addr");
        // codeql[java/avoid-deprecated-apis] Validate the deprecated no-user method still throws.
        assertThatThrownBy(() -> store.save(contact))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void insert_withNullAggregateThrowsIllegalArgument() {
        final User owner = TestUserFactory.createUser("contact-store-insert-null");
        assertThatThrownBy(() -> store.insert(null, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contact aggregate must not be null");
    }

    @Test
    void insert_withNullUserThrowsIllegalArgument() {
        final Contact contact = new Contact("c-1", "Amy", "Lee", "1234567890", "Addr");
        assertThatThrownBy(() -> store.insert(contact, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user must not be null");
    }

    // --- Save and insert behavior tests ---

    @Test
    void save_updatesExistingContact() {
        final User owner = TestUserFactory.createUser("contact-store-update");
        final Contact contact = new Contact("c-upd", "Updated", "Name", "1234567890", "New Addr");
        final ContactEntity existingEntity = mock(ContactEntity.class);
        when(repository.findByContactIdAndUser("c-upd", owner)).thenReturn(Optional.of(existingEntity));

        store.save(contact, owner);

        verify(mapper).updateEntity(existingEntity, contact);
        verify(repository).save(existingEntity);
    }

    @Test
    void save_insertsNewContact() {
        final User owner = TestUserFactory.createUser("contact-store-insert-new");
        final Contact contact = new Contact("c-new", "New", "Contact", "1234567890", "Addr");
        final ContactEntity newEntity = mock(ContactEntity.class);
        when(repository.findByContactIdAndUser("c-new", owner)).thenReturn(Optional.empty());
        when(mapper.toEntity(contact, owner)).thenReturn(newEntity);

        store.save(contact, owner);

        verify(repository).save(newEntity);
    }

    @Test
    void insert_savesNewEntity() {
        final User owner = TestUserFactory.createUser("contact-store-insert");
        final Contact contact = new Contact("c-ins", "Insert", "Test", "1234567890", "Addr");
        final ContactEntity entity = mock(ContactEntity.class);
        when(mapper.toEntity(contact, owner)).thenReturn(entity);

        store.insert(contact, owner);

        verify(repository).save(entity);
    }

    // --- findAll tests ---

    @Test
    void findAll_withUserReturnsMappedContacts() {
        final User owner = TestUserFactory.createUser("contact-store-findall");
        final ContactEntity entity1 = mock(ContactEntity.class);
        final ContactEntity entity2 = mock(ContactEntity.class);
        final Contact contact1 = new Contact("c-1", "A", "B", "1234567890", "Addr1");
        final Contact contact2 = new Contact("c-2", "C", "D", "0987654321", "Addr2");
        when(repository.findByUser(owner)).thenReturn(List.of(entity1, entity2));
        when(mapper.toDomain(entity1)).thenReturn(contact1);
        when(mapper.toDomain(entity2)).thenReturn(contact2);

        final List<Contact> result = store.findAll(owner);

        assertThat(result).containsExactly(contact1, contact2);
    }

    @Test
    void findAll_adminReturnsMappedContacts() {
        final ContactEntity entity = mock(ContactEntity.class);
        final Contact contact = new Contact("c-admin", "Admin", "View", "1234567890", "Addr");
        when(repository.findAll()).thenReturn(List.of(entity));
        when(mapper.toDomain(entity)).thenReturn(contact);

        final List<Contact> result = store.findAll();

        assertThat(result).containsExactly(contact);
    }

    @Test
    void findById_withUserReturnsEmptyWhenNotFound() {
        final User owner = TestUserFactory.createUser("contact-store-find-empty");
        when(repository.findByContactIdAndUser("missing", owner)).thenReturn(Optional.empty());

        assertThat(store.findById("missing", owner)).isEmpty();
    }

    @Test
    void deleteAll_delegatesToRepository() {
        store.deleteAll();

        verify(repository).deleteAll();
    }
}
