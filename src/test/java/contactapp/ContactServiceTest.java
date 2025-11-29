package contactapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the ContactService behavior.
 *
 * Verifies:
 * - getInstance() returns a non-null singleton
 * - addContact() adds a new contact and rejects duplicate IDs
 * - deleteContact() removes existing contacts and throws for blank IDs
 * - updateContact() updates existing contacts and returns false if the ID is missing
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

        // Indicates whether the contact was added successfully
        boolean added = contactService.addContact(contact);

        // addContact(...) should return true for a new contactId
        // the internal map should now contain the entry with correct data
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

        // deleteContact(...) should report success
        // and the map should no longer contain the key "100"
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
     * Verifies {@link ContactService#updateContact(String, String, String, String, String)} updates stored contacts.
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

        // Update the contact's mutable fields
        boolean updated = contactService.updateContact(
                "100",
                "Sebastian",
                "Walker",
                "0987654321",
                "1234 Test Street"
        );

        // updateContact(...) should report success
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
        assertThat(secondAdd).isFalse();                  // duplicate id rejected
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
}
