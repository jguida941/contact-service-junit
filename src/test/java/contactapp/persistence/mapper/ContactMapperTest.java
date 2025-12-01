package contactapp.persistence.mapper;

import contactapp.domain.Contact;
import contactapp.persistence.entity.ContactEntity;
import contactapp.security.User;
import contactapp.support.TestUserFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link ContactMapper} round-trips data and reuses domain validation.
 */
class ContactMapperTest {

    private final ContactMapper mapper = new ContactMapper();

    @Test
    void toEntityCopiesAllFields() {
        Contact contact = new Contact("1234567890", "Alice", "Smith", "1112223333", "123 Main St");
        User owner = TestUserFactory.createUser("contact-mapper");

        ContactEntity entity = mapper.toEntity(contact, owner);

        assertThat(entity.getContactId()).isEqualTo("1234567890");
        assertThat(entity.getFirstName()).isEqualTo("Alice");
        assertThat(entity.getLastName()).isEqualTo("Smith");
        assertThat(entity.getPhone()).isEqualTo("1112223333");
        assertThat(entity.getAddress()).isEqualTo("123 Main St");
        assertThat(entity.getUser()).isEqualTo(owner);
    }

    @Test
    void toDomainReusesValidation() {
        ContactEntity entity = new ContactEntity(
                "abc",
                "Bob",
                "Jones",
                "1234567890",
                "456 Elm",
                TestUserFactory.createUser("contact-mapper-domain"));

        Contact contact = mapper.toDomain(entity);

        assertThat(contact.getContactId()).isEqualTo("abc");
        assertThat(contact.getFirstName()).isEqualTo("Bob");
    }

    /**
     * Null values can surface from legacy persistence fallbacks, so returning null keeps
     * callers from receiving phantom entities.
     */
    @Test
    void toEntityReturnsNullWhenDomainIsNull() {
        assertThat(mapper.toEntity(null, TestUserFactory.createUser())).isNull();
    }

    @Test
    void toDomainRejectsInvalidDatabaseData() {
        ContactEntity entity = new ContactEntity(
                "abc",
                "Bob",
                "Jones",
                "invalid-phone",
                "456 Elm",
                TestUserFactory.createUser("contact-mapper-invalid"));

        assertThatThrownBy(() -> mapper.toDomain(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phone");
    }

    /**
     * Mapper should simply return null when a repository hands us a null row.
     */
    @Test
    void toDomainReturnsNullWhenEntityIsNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedToEntityReturnsNullWhenDomainNull() {
        // codeql[java/avoid-deprecated-apis] Legacy mapper overload kept so we verify it still returns null.
        assertThat(mapper.toEntity((Contact) null)).isNull();
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedToEntityThrowsWhenDomainProvided() {
        Contact contact = new Contact("legacy", "Amy", "Lee", "1234567890", "Addr");
        // codeql[java/avoid-deprecated-apis] Intentional invocation to assert the legacy overload still throws.
        assertThatThrownBy(() -> mapper.toEntity(contact))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void updateEntityCopiesMutableFields() {
        ContactEntity entity = new ContactEntity(
                "contact-1",
                "Old",
                "Name",
                "0000000000",
                "Old Address",
                TestUserFactory.createUser("contact-mapper-update"));
        Contact updated = new Contact("contact-1", "New", "Last", "1112223333", "New Address");

        mapper.updateEntity(entity, updated);

        assertThat(entity.getFirstName()).isEqualTo("New");
        assertThat(entity.getLastName()).isEqualTo("Last");
        assertThat(entity.getPhone()).isEqualTo("1112223333");
        assertThat(entity.getAddress()).isEqualTo("New Address");
    }

    @Test
    void updateEntityThrowsWhenTargetIsNull() {
        Contact source = new Contact("contact-1", "First", "Last", "1234567890", "Addr");
        assertThatThrownBy(() -> mapper.updateEntity(null, source))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target entity must not be null");
    }

    @Test
    void updateEntityThrowsWhenSourceIsNull() {
        ContactEntity entity = new ContactEntity(
                "contact-1",
                "Old",
                "Name",
                "0000000000",
                "Old Address",
                TestUserFactory.createUser("contact-mapper-null-source"));
        assertThatThrownBy(() -> mapper.updateEntity(entity, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source contact must not be null");
    }
}
