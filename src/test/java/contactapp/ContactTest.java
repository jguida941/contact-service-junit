package contactapp;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the Contact class.
 * Verifies:
 * - successful creation with valid data,
 * - updates via setters with valid data,
 * - and that invalid inputs cause the constructor/setters to throw IllegalArgumentException
 * with the specific validation messages emitted by the Validation helper.
 */
public class ContactTest {

    /**
     * Verifies the constructor stores every field when valid data is supplied.
     */
    @Test
    void testSuccessfulCreation() {
        Contact contact = new Contact(
                "1",
                "firstName",
                "lastName",
                "1234567890",
                "7622 Main Street"
        );

        // Check that each field has the expected value for the contact object
        assertThat(contact)
                .hasFieldOrPropertyWithValue("contactId", "1")
                .hasFieldOrPropertyWithValue("firstName", "firstName")
                .hasFieldOrPropertyWithValue("lastName", "lastName")
                .hasFieldOrPropertyWithValue("phone", "1234567890")
                .hasFieldOrPropertyWithValue("address", "7622 Main Street");
    }

    /**
     * Ensures setters accept valid data and mutate the stored state accordingly.
     */
    @Test
    void testValidSetters() {
        Contact contact = new Contact("1", "firstName", "lastName", "1234567890", "7622 Main Street");
        contact.setFirstName("Justin");
        contact.setLastName("Guida");
        contact.setPhone("0987654321");
        contact.setAddress("1234 Taro Street");

        // Check that each field has the updated value for the contact object
        assertThat(contact)
                .hasFieldOrPropertyWithValue("firstName", "Justin")
                .hasFieldOrPropertyWithValue("lastName", "Guida")
                .hasFieldOrPropertyWithValue("phone", "0987654321")
                .hasFieldOrPropertyWithValue("address", "1234 Taro Street");
    }

    /**
     * Confirms the constructor trims text inputs before persisting them.
     */
    @Test
    void testConstructorTrimsStoredValues() {
        Contact contact = new Contact(
                " 100 ",
                "  Alice ",
                "  Smith  ",
                "1234567890",
                " 742 Evergreen Terrace  "
        );

        assertThat(contact.getContactId()).isEqualTo("100");
        assertThat(contact.getFirstName()).isEqualTo("Alice");
        assertThat(contact.getLastName()).isEqualTo("Smith");
        assertThat(contact.getAddress()).isEqualTo("742 Evergreen Terrace");
    }

    /**
     * Enumerates invalid constructor inputs (id, names, phone, address).
     */
    @CsvSource(
            value = {
                    // contactId validation
                    "' ', firstName, lastName, 1234567890, '7622 Main Street', 'contactId must not be null or blank'"
                    , "'', firstName, lastName, 1234567890, '7622 Main Street', 'contactId must not be null or blank'"
                    , "null, firstName, lastName, 1234567890, '7622 Main Street', 'contactId must not be null or blank'"
                    , "12345678901, firstName, lastName, 1234567890, '7622 Main Street', 'contactId length must be between 1 and 10'"

                    // firstName validation
                    , "1, ' ', lastName, 1234567890, '7622 Main Street', 'firstName must not be null or blank'"
                    , "1, '', lastName, 1234567890, '7622 Main Street', 'firstName must not be null or blank'"
                    , "1, null, lastName, 1234567890, '7622 Main Street', 'firstName must not be null or blank'"
                    , "1, 'TooLongFirstName', lastName, 1234567890, '7622 Main Street', 'firstName length must be between 1 and 10'"

                    // lastName validation
                    , "1, firstName, ' ' , 1234567890, '7622 Main Street', 'lastName must not be null or blank'"
                    , "1, firstName, '', 1234567890, '7622 Main Street', 'lastName must not be null or blank'"
                    , "1, firstName, null, 1234567890, '7622 Main Street', 'lastName must not be null or blank'"
                    , "1, firstName, 'TooLongLastName', 1234567890, '7622 Main Street', 'lastName length must be between 1 and 10'"
                    , "1, ' firstNameWithSpaces ', lastName, 1234567890, '7622 Main Street', 'firstName length must be between 1 and 10'"

                    // phone validation
                    , "1, firstName, lastName, ' ' , '7622 Main Street', 'phone must not be null or blank'"
                    , "1, firstName, lastName, '', '7622 Main Street', 'phone must not be null or blank'"
                    , "1, firstName, lastName, null, '7622 Main Street', 'phone must not be null or blank'"
                    , "1, firstName, lastName, 123456789, '7622 Main Street', 'phone must be exactly 10 digits'"
                    , "1, firstName, lastName, 12345678901, '7622 Main Street', 'phone must be exactly 10 digits'"
                    , "1, firstName, lastName, 12345abcde, '7622 Main Street', 'phone must only contain digits 0-9'"
                    , "1, firstName, lastName, 123-456-7890, '7622 Main Street', 'phone must only contain digits 0-9'"
                    , "1, firstName, lastName, 123 555 7855, '7622 Main Street', 'phone must only contain digits 0-9'"

                    // address validation
                    , "1, firstName, lastName, 1234567890, ' ', 'address must not be null or blank'"
                    , "1, firstName, lastName, 1234567890, '', 'address must not be null or blank'"
                    , "1, firstName, lastName, 1234567890, null, 'address must not be null or blank'"
                    , "1, firstName, lastName, 1234567890, 'This address is way too long to be valid', 'address length must be between 1 and 30'"
            },

            // Specify that the string "null" should be treated as a null value
            nullValues = "null"
    )
    /**
     * Verifies the constructor rejects each invalid input combination defined above.
     */
    @ParameterizedTest
    void testFailedCreation(
            String contactId,
            String firstName,
            String lastName,
            String phone,
            String address,
            String expectedMessage // expected exception message
    ) {

        // Check that creating a Contact with invalid inputs throws an exception with the expected message
        assertThatThrownBy(() -> new Contact(contactId, firstName, lastName, phone, address))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Drives invalid first-name values for the setter validation test.
     */
    @CsvSource(
            value = {
                    "' ', 'firstName must not be null or blank'"
                    , "'', 'firstName must not be null or blank'"
                    , "null, 'firstName must not be null or blank'"
                    , "TooLongFirstName, 'firstName length must be between 1 and 10'"
            },
            nullValues = "null"
    )

    /**
     * Ensures {@link Contact#setFirstName(String)} throws for blank/null/too-long names.
     */
    @ParameterizedTest
    void testFailedSetFirstName(String invalidFirstName, String expectedMessage) {
        Contact contact = new Contact("1", "firstName", "lastName", "1234567890", "7622 Main Street");

        // Check that invalid firstName updates throw the proper exception message
        assertThatThrownBy(() -> contact.setFirstName(invalidFirstName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);
    }

    /**
     * Supplies invalid arguments to {@link #testUpdateRejectsInvalidValuesAtomically(String, String, String, String, String)}.
     */
    private static Stream<Arguments> invalidUpdateValues() {
        return Stream.of(
                Arguments.of(" ", "lastName", "1234567890", "7622 Main Street", "firstName must not be null or blank"),
                Arguments.of("firstName", " ", "1234567890", "7622 Main Street", "lastName must not be null or blank"),
                Arguments.of("firstName", "lastName", "12345abcde", "7622 Main Street", "phone must only contain digits 0-9"),
                Arguments.of("firstName", "lastName", "1234567890", " ", "address must not be null or blank")
        );
    }

    /**
     * Ensures {@link Contact#update(String, String, String, String)} rejects invalid data and leaves state unchanged.
     */
    @ParameterizedTest
    @MethodSource("invalidUpdateValues")
    void testUpdateRejectsInvalidValuesAtomically(
            String newFirst,
            String newLast,
            String newPhone,
            String newAddress,
            String expectedMessage) {
        Contact contact = new Contact("1", "firstName", "lastName", "1234567890", "7622 Main Street");

        assertThatThrownBy(() -> contact.update(newFirst, newLast, newPhone, newAddress))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(expectedMessage);

        assertThat(contact)
                .hasFieldOrPropertyWithValue("firstName", "firstName")
                .hasFieldOrPropertyWithValue("lastName", "lastName")
                .hasFieldOrPropertyWithValue("phone", "1234567890")
                .hasFieldOrPropertyWithValue("address", "7622 Main Street");
    }

    /**
     * Ensures the copy guard rejects corrupted state (null internal fields).
     *
     * Added to kill PITest mutant: "removed call to validateCopySource" in Contact.copy().
     * Without this test, removing the validateCopySource call would survive mutation
     * since normal code paths never produce null internal fields.
     * This test uses reflection to corrupt internal state and verify copy() throws.
     */
    @Test
    void testCopyRejectsNullInternalState() throws Exception {
        Contact contact = new Contact("901", "Alice", "Smith", "1234567890", "123 Main Street");

        // Use reflection to corrupt internal state (simulate memory corruption or serialization bugs)
        Field firstNameField = Contact.class.getDeclaredField("firstName");
        firstNameField.setAccessible(true);
        firstNameField.set(contact, null);

        assertThatThrownBy(contact::copy)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contact copy source must not be null");
    }
}
