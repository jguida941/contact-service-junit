package contactapp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContactServiceTest {

    // @BeforeEach method to clear the ContactService database before each test
    @BeforeEach
    void clearBeforeTest() {
        ContactService.getInstance().getDatabase().clear();
    }

    // Check that ContactService singleton instance is not null
    @Test
    void testGetInstance() {
        assertThat(ContactService.getInstance()).isNotNull();
    }

    // Test adding a new contact to the ContactService
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
        // the internal map should now contain the entry: "1" -> contact
        assertThat(added).isTrue();
        assertThat(contactService.getDatabase())
                .containsEntry("100", contact);
    }

    // Test deleting an existing contact from the ContactService
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
        assertThat(contactService.getDatabase())
                .containsEntry("100", contact);

        boolean deleted = contactService.deleteContact("100");

        // deleteContact(...) should report success
        // and the map should no longer contain the entry "100" -> contact
        assertThat(deleted).isTrue();
        assertThat(contactService.getDatabase())
                .doesNotContainEntry("100", contact);
    }

    // Test updating an existing contact's mutable fields
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
        assertThat(contactService.getDatabase())
                .containsEntry("100", contact);

        // Update the contact's mutable fields
        boolean updated = contactService.updateContact(
                "100",
                "Sebastian",
                "Guida",
                "0987654321",
                "1234 Test Street"
        );

        // updateContact(...) should report success
        assertThat(updated).isTrue();

        assertThat(contactService.getDatabase().get("100"))
                .hasFieldOrPropertyWithValue("firstName", "Sebastian")
                .hasFieldOrPropertyWithValue("lastName", "Guida")
                .hasFieldOrPropertyWithValue("phone", "0987654321")
                   .hasFieldOrPropertyWithValue("address", "1234 Test Street");
        }

    // Test for Duplicate Contact IDs
    @Test
    void testAddDuplicateContactFails() {
        ContactService service = ContactService.getInstance();
        Contact contact1 = new Contact("100", "Justin", "Guida", "1234567890", "7622 Main Street");
        Contact contact2 = new Contact("100", "Other", "Person", "1112223333", "Other Address");

        boolean firstAdd = service.addContact(contact1);
        boolean secondAdd = service.addContact(contact2);

        assertThat(firstAdd).isTrue();
        assertThat(secondAdd).isFalse();                  // duplicate id rejected
        assertThat(service.getDatabase())
                .containsEntry("100", contact1);          // original remains
    }

    // Test updateContact returns false for missing contactId
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
    // Test deleteContact throws IllegalArgumentException for null contactId
    @Test
    void testDeleteContactBlankIdThrows() {
        ContactService service = ContactService.getInstance();

        assertThatThrownBy(() -> service.deleteContact(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contactId must not be null or blank");
    }
}