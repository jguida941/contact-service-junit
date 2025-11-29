package contactapp.service;

import contactapp.domain.Contact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the ContactService behavior.
 *
 * <p>Verifies:
 * <ul>
 *   <li>getInstance() returns a non-null singleton</li>
 *   <li>addContact() adds a new contact and rejects duplicate IDs</li>
 *   <li>deleteContact() removes existing contacts and throws for blank IDs</li>
 *   <li>updateContact() updates existing contacts and returns false if the ID is missing</li>
 * </ul>
 *
 * <p>Tests are in the same package as ContactService to access package-private methods.
 */
public class ContactServiceTest {

    /**
     * Clears the singleton map before each test run to keep scenarios isolated.
     */
    @BeforeEach
    void clearBeforeTest() {
        ContactService.getInstance().clearAllContacts();
    }

    /**
     * Ensures {@link ContactService#getInstance()} returns a concrete service.
     */
    @Test
    void testGetInstance() {
        assertThat(ContactService.getInstance()).isNotNull();
    }

    /**
     * Verifies repeated calls to {@link ContactService#getInstance()} return the same reference.
     */
    @Test
    void testGetInstanceReturnsSameReference() {
        ContactService first = ContactService.getInstance();
        ContactService second = ContactService.getInstance();
        assertThat(first).isSameAs(second);
    }

    /**
     * Confirms {@link ContactService#addContact(Contact)} inserts new contacts and stores them in the map.
     */
    @Test
    void testAddContact() {
        ContactService contactService = ContactService.getInstance();
        Contact contact = new Contact(
                "100",
                "Justin",
                "Guida",
                "1234567890",
                "7622 Main Street"
        );

        boolean added = contactService.addContact(contact);

        assertThat(added).isTrue();
        assertThat(contactService.getDatabase()).containsKey("100");
        Contact stored = contactService.getDatabase().get("100");
        assertThat(stored.getFirstName()).isEqualTo("Justin");
        assertThat(stored.getLastName()).isEqualTo("Guida");
        assertThat(stored.getPhone()).isEqualTo("1234567890");
        assertThat(stored.getAddress()).isEqualTo("7622 Main Street");
    }

    /**
     * Proves {@link ContactService#deleteContact(String)} removes existing contacts.
     */
    @Test
    void testDeleteContact() {
        ContactService contactService = ContactService.getInstance();
        Contact contact = new Contact(
                "100",
                "Justin",
                "Guida",
                "1234567890",
                "7622 Main Street"
        );

        boolean added = contactService.addContact(contact);

        assertThat(added).isTrue();
        assertThat(contactService.getDatabase()).containsKey("100");

        boolean deleted = contactService.deleteContact("100");

        assertThat(deleted).isTrue();
        assertThat(contactService.getDatabase()).doesNotContainKey("100");
    }

    /**
     * Ensures delete returns {@code false} when the contact ID is missing.
     */
    @Test
    void testDeleteMissingContactReturnsFalse() {
        ContactService contactService = ContactService.getInstance();
        assertThat(contactService.deleteContact("missing-id")).isFalse();
        assertThat(contactService.getDatabase()).isEmpty();
    }

    /**
     * Verifies {@link ContactService#updateContact} updates stored contacts.
     */
    @Test
    void testUpdateContact() {
        ContactService contactService = ContactService.getInstance();
        Contact contact = new Contact(
                "100",
                "Justin",
                "Guida",
                "1234567890",
                "7622 Main Street"
        );

        boolean added = contactService.addContact(contact);
        assertThat(added).isTrue();
        assertThat(contactService.getDatabase()).containsKey("100");

        boolean updated = contactService.updateContact(
                "100",
                "Sebastian",
                "Walker",
                "0987654321",
                "1234 Test Street"
        );

        assertThat(updated).isTrue();

        assertThat(contactService.getDatabase().get("100"))
                .hasFieldOrPropertyWithValue("firstName", "Sebastian")
                .hasFieldOrPropertyWithValue("lastName", "Walker")
                .hasFieldOrPropertyWithValue("phone", "0987654321")
                .hasFieldOrPropertyWithValue("address", "1234 Test Street");
    }

    /**
     * Confirms IDs are trimmed before lookups during updates.
     */
    @Test
    void testUpdateContactTrimsId() {
        ContactService service = ContactService.getInstance();
        Contact contact = new Contact(
                "200",
                "Justin",
                "Guida",
                "1234567890",
                "7622 Main Street"
        );

        service.addContact(contact);

        boolean updated = service.updateContact(
                " 200 ",
                "Sebastian",
                "Walker",
                "0987654321",
                "1234 Test Street"
        );

        assertThat(updated).isTrue();
        assertThat(service.getDatabase().get("200"))
                .hasFieldOrPropertyWithValue("firstName", "Sebastian")
                .hasFieldOrPropertyWithValue("lastName", "Walker")
                .hasFieldOrPropertyWithValue("phone", "0987654321")
                .hasFieldOrPropertyWithValue("address", "1234 Test Street");
    }

    /**
     * Ensures update throws when the ID is blank so validation mirrors delete().
     */
    @Test
    void testUpdateContactBlankIdThrows() {
        ContactService service = ContactService.getInstance();

        assertThatThrownBy(() -> service.updateContact(" ", "A", "B", "1234567890", "Somewhere"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contactId must not be null or blank");
    }

    /**
     * Ensures duplicate contact IDs are rejected and the original remains stored.
     */
    @Test
    void testAddDuplicateContactFails() {
        ContactService service = ContactService.getInstance();
        Contact contact1 = new Contact("100", "Justin", "Guida", "1234567890", "7622 Main Street");
        Contact contact2 = new Contact("100", "Other", "Person", "1112223333", "Other Address");

        boolean firstAdd = service.addContact(contact1);
        boolean secondAdd = service.addContact(contact2);

        assertThat(firstAdd).isTrue();
        assertThat(secondAdd).isFalse();  // duplicate id rejected
        // Verify original data is still stored
        Contact stored = service.getDatabase().get("100");
        assertThat(stored.getFirstName()).isEqualTo("Justin");
        assertThat(stored.getLastName()).isEqualTo("Guida");
    }

    /**
     * Validates update returns {@code false} when the contact ID does not exist.
     */
    @Test
    void testUpdateMissingContactReturnsFalse() {
        ContactService service = ContactService.getInstance();

        boolean updated = service.updateContact(
                "does-not-exist",
                "Sebastian",
                "Guida",
                "0987654321",
                "1234 Test Street"
        );

        assertThat(updated).isFalse();
    }

    /**
     * Ensures delete throws when passed a blank ID.
     */
    @Test
    void testDeleteContactBlankIdThrows() {
        ContactService service = ContactService.getInstance();

        assertThatThrownBy(() -> service.deleteContact(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contactId must not be null or blank");
    }

    /**
     * Verifies {@link ContactService#addContact(Contact)} guards against null input.
     */
    @Test
    void testAddContactNullThrows() {
        ContactService service = ContactService.getInstance();

        assertThatThrownBy(() -> service.addContact(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contact must not be null");
    }

    /**
     * Ensures getDatabase returns defensive copies so external mutation cannot alter service state.
     */
    @Test
    void testGetDatabaseReturnsDefensiveCopies() {
        ContactService service = ContactService.getInstance();
        Contact contact = new Contact("500", "Original", "Name", "1234567890", "Original Address");
        service.addContact(contact);

        // Get a snapshot and mutate it
        Contact snapshot = service.getDatabase().get("500");
        snapshot.setFirstName("Mutated");
        snapshot.setLastName("Person");
        snapshot.setPhone("0987654321");
        snapshot.setAddress("Mutated Address");

        // Fetch a fresh snapshot and verify the service state is unchanged
        Contact freshSnapshot = service.getDatabase().get("500");
        assertThat(freshSnapshot.getFirstName()).isEqualTo("Original");
        assertThat(freshSnapshot.getLastName()).isEqualTo("Name");
        assertThat(freshSnapshot.getPhone()).isEqualTo("1234567890");
        assertThat(freshSnapshot.getAddress()).isEqualTo("Original Address");
    }

    /**
     * Covers the cold-start branch in getInstance() where instance is null.
     *
     * <p>Uses reflection to reset the static instance field, then verifies
     * getInstance() creates a new instance. This ensures full branch coverage
     * of the lazy initialization pattern.
     */
    @Test
    void testGetInstanceColdStart() throws Exception {
        // Reset static instance to null via reflection
        Field instanceField = ContactService.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        // Now getInstance() should create a new instance
        ContactService service = ContactService.getInstance();
        assertThat(service).isNotNull();

        // Verify the instance was properly registered
        ContactService second = ContactService.getInstance();
        assertThat(second).isSameAs(service);
    }

    // ==================== getContactById Tests ====================

    /**
     * Verifies getContactById returns the contact when it exists.
     */
    @Test
    void testGetContactByIdReturnsContact() {
        ContactService service = ContactService.getInstance();
        Contact contact = new Contact("600", "Test", "User", "1234567890", "Test Address");
        service.addContact(contact);

        var result = service.getContactById("600");

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Test");
    }

    /**
     * Verifies getContactById returns empty when contact doesn't exist.
     */
    @Test
    void testGetContactByIdReturnsEmptyWhenNotFound() {
        ContactService service = ContactService.getInstance();

        var result = service.getContactById("nonexistent");

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getContactById throws when ID is blank.
     */
    @Test
    void testGetContactByIdBlankIdThrows() {
        ContactService service = ContactService.getInstance();

        assertThatThrownBy(() -> service.getContactById(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contactId must not be null or blank");
    }

    /**
     * Verifies getContactById trims the ID before lookup.
     */
    @Test
    void testGetContactByIdTrimsId() {
        ContactService service = ContactService.getInstance();
        Contact contact = new Contact("700", "Trimmed", "Test", "1234567890", "Test Address");
        service.addContact(contact);

        var result = service.getContactById(" 700 ");

        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("Trimmed");
    }

    /**
     * Verifies getContactById returns a defensive copy.
     */
    @Test
    void testGetContactByIdReturnsDefensiveCopy() {
        ContactService service = ContactService.getInstance();
        Contact contact = new Contact("800", "Original", "Name", "1234567890", "Original Addr");
        service.addContact(contact);

        var result = service.getContactById("800");
        assertThat(result).isPresent();
        result.get().setFirstName("Mutated");

        var freshResult = service.getContactById("800");
        assertThat(freshResult).isPresent();
        assertThat(freshResult.get().getFirstName()).isEqualTo("Original");
    }

    // ==================== getAllContacts Tests ====================

    /**
     * Verifies getAllContacts returns empty list when no contacts exist.
     */
    @Test
    void testGetAllContactsReturnsEmptyList() {
        ContactService service = ContactService.getInstance();

        var result = service.getAllContacts();

        assertThat(result).isEmpty();
    }

    /**
     * Verifies getAllContacts returns all contacts.
     */
    @Test
    void testGetAllContactsReturnsAllContacts() {
        ContactService service = ContactService.getInstance();
        service.addContact(new Contact("901", "First", "User", "1111111111", "First Addr"));
        service.addContact(new Contact("902", "Second", "User", "2222222222", "Second Addr"));

        var result = service.getAllContacts();

        assertThat(result).hasSize(2);
    }
}
