package contactapp.domain;

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
 *
 * <p>Verifies:
 * <ul>
 *   <li>Successful creation with valid data</li>
 *   <li>Updates via setters with valid data</li>
 *   <li>Invalid inputs cause IllegalArgumentException with specific validation messages</li>
 *   <li>Atomic update behavior (all-or-nothing)</li>
 * </ul>
 *
 * <p>Tests are in the same package as Contact to access package-private methods.
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
                    "' ', firstName, lastName, 1234567890, '7622 Main Street', 'Contact ID must not be null or blank'"
                    , "'', firstName, lastName, 1234567890, '7622 Main Street', 'Contact ID must not be null or blank'"
                    , "null, firstName, lastName, 1234567890, '7622 Main Street', 'Contact ID must not be null or blank'"
                    , "12345678901, firstName, lastName, 1234567890, '7622 Main Street', 'Contact ID length must be between 1 and 10'"

                    // firstName validation
                    , "1, ' ', lastName, 1234567890, '7622 Main Street', 'First Name must not be null or blank'"
                    , "1, '', lastName, 1234567890, '7622 Main Street', 'First Name must not be null or blank'"
                    , "1, null, lastName, 1234567890, '7622 Main Street', 'First Name must not be null or blank'"
                    , "1, 'TooLongFirstName', lastName, 1234567890, '7622 Main Street', 'First Name length must be between 1 and 10'"

                    // lastName validation
                    , "1, firstName, ' ' , 1234567890, '7622 Main Street', 'Last Name must not be null or blank'"
                    , "1, firstName, '', 1234567890, '7622 Main Street', 'Last Name must not be null or blank'"
                    , "1, firstName, null, 1234567890, '7622 Main Street', 'Last Name must not be null or blank'"
                    , "1, firstName, 'TooLongLastName', 1234567890, '7622 Main Street', 'Last Name length must be between 1 and 10'"
                    , "1, ' firstNameWithSpaces ', lastName, 1234567890, '7622 Main Street', 'First Name length must be between 1 and 10'"

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
                    "' ', 'First Name must not be null or blank'"
                    , "'', 'First Name must not be null or blank'"
                    , "null, 'First Name must not be null or blank'"
                    , "TooLongFirstName, 'First Name length must be between 1 and 10'"
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
     * Supplies invalid arguments to {@link #testUpdateRejectsInvalidValuesAtomically}.
     */
    private static Stream<Arguments> invalidUpdateValues() {
        return Stream.of(
                Arguments.of(" ", "lastName", "1234567890", "7622 Main Street", "First Name must not be null or blank"),
                Arguments.of("firstName", " ", "1234567890", "7622 Main Street", "Last Name must not be null or blank"),
                Arguments.of("firstName", "lastName", "12345abcde", "7622 Main Street", "phone must only contain digits 0-9"),
                Arguments.of("firstName", "lastName", "1234567890", " ", "address must not be null or blank")
        );
    }

    /**
     * Ensures {@link Contact#update(String, String, String, String)} rejects invalid data
     * and leaves state unchanged (atomic update behavior).
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
     * <p>Added to kill PITest mutant: "removed call to validateCopySource" in Contact.copy().
     * This test uses reflection to corrupt each internal field and verify copy() throws.
     * Parameterized to achieve full branch coverage of the validateCopySource null checks.
     */
    @ParameterizedTest(name = "copy rejects null {0}")
    @MethodSource("nullFieldProvider")
    void testCopyRejectsNullInternalState(String fieldName) throws Exception {
        Contact contact = new Contact("901", "Alice", "Smith", "1234567890", "123 Main Street");

        // Use reflection to corrupt internal state (simulate memory corruption or serialization bugs)
        Field field = Contact.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(contact, null);

        assertThatThrownBy(contact::copy)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("contact copy source must not be null");
    }

    /**
     * Provides field names for the null internal state test.
     */
    static Stream<String> nullFieldProvider() {
        return Stream.of("contactId", "firstName", "lastName", "phone", "address");
    }

    // ==================== Additional Boundary and Edge Case Tests ====================

    /**
     * Tests Contact ID at exact maximum length boundary (10 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a contact ID with exactly 10 characters
     * (the maximum) is accepted. This catches mutants that change the comparison operator
     * from {@code >} to {@code >=} in the length validation.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in length check</li>
     *   <li>Replaced MAX_ID_LENGTH constant</li>
     * </ul>
     */
    @Test
    void constructor_acceptsContactIdAtMaximumLength() {
        // Create contact with ID exactly 10 chars long
        final Contact contact = new Contact("1234567890", "John", "Doe", "5551234567", "123 Main St");

        // Verify the ID was stored correctly
        assertThat(contact.getContactId()).isEqualTo("1234567890");
        assertThat(contact.getContactId()).hasSize(10);
    }

    /**
     * Tests Contact ID one character over the maximum length (11 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a contact ID with 11 characters
     * is rejected. Tests the boundary from the other side.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary</li>
     *   <li>Removed length validation check</li>
     * </ul>
     */
    @Test
    void constructor_rejectsContactIdOneOverMaximumLength() {
        assertThatThrownBy(() ->
                new Contact("12345678901", "John", "Doe", "5551234567", "123 Main St"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contact ID length must be between 1 and 10");
    }

    /**
     * Tests firstName at exact maximum length boundary (10 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a firstName with exactly 10 characters
     * is accepted. Catches mutants in the length comparison logic.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary: {@code >} to {@code >=}</li>
     *   <li>Negated conditional</li>
     * </ul>
     */
    @Test
    void constructor_acceptsFirstNameAtMaximumLength() {
        final Contact contact = new Contact("1", "ABCDEFGHIJ", "Doe", "5551234567", "123 Main St");

        // Verify firstName is exactly 10 characters
        assertThat(contact.getFirstName()).isEqualTo("ABCDEFGHIJ");
        assertThat(contact.getFirstName()).hasSize(10);
    }

    /**
     * Tests lastName at exact maximum length boundary (10 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that a lastName with exactly 10 characters
     * is accepted. Catches boundary condition mutations.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary</li>
     *   <li>Replaced constant value</li>
     * </ul>
     */
    @Test
    void constructor_acceptsLastNameAtMaximumLength() {
        final Contact contact = new Contact("1", "John", "ABCDEFGHIJ", "5551234567", "123 Main St");

        assertThat(contact.getLastName()).isEqualTo("ABCDEFGHIJ");
        assertThat(contact.getLastName()).hasSize(10);
    }

    /**
     * Tests address at exact maximum length boundary (30 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that an address with exactly 30 characters
     * is accepted. Tests the upper boundary of address validation.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in validateLength</li>
     *   <li>Replaced MAX_ADDRESS_LENGTH constant</li>
     * </ul>
     */
    @Test
    void constructor_acceptsAddressAtMaximumLength() {
        final String maxAddress = "123456789012345678901234567890"; // Exactly 30 chars
        final Contact contact = new Contact("1", "John", "Doe", "5551234567", maxAddress);

        assertThat(contact.getAddress()).isEqualTo(maxAddress);
        assertThat(contact.getAddress()).hasSize(30);
    }

    /**
     * Tests address one character over the maximum length (31 characters).
     *
     * <p><b>Why this test exists:</b> Ensures that an address with 31 characters
     * is rejected. Tests the boundary from the rejection side.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary</li>
     *   <li>Removed validation check</li>
     * </ul>
     */
    @Test
    void constructor_rejectsAddressOneOverMaximumLength() {
        final String tooLongAddress = "1234567890123456789012345678901"; // 31 chars

        assertThatThrownBy(() ->
                new Contact("1", "John", "Doe", "5551234567", tooLongAddress))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("address length must be between 1 and 30");
    }

    /**
     * Tests phone with exactly 10 digits (the required length).
     *
     * <p><b>Why this test exists:</b> Ensures the equality check {@code length != 10}
     * works correctly at the exact boundary. Catches mutants that change != to ==.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Negated conditional: {@code !=} to {@code ==}</li>
     *   <li>Changed conditional boundary</li>
     * </ul>
     */
    @Test
    void constructor_acceptsPhoneWithExactlyTenDigits() {
        final Contact contact = new Contact("1", "John", "Doe", "1234567890", "123 Main St");

        assertThat(contact.getPhone()).isEqualTo("1234567890");
        assertThat(contact.getPhone()).hasSize(10);
    }

    /**
     * Tests phone with 9 digits (one below required).
     *
     * <p><b>Why this test exists:</b> Ensures that a phone with 9 digits is rejected.
     * Tests the lower boundary of the equality check.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Removed conditional check</li>
     *   <li>Changed != to other operators</li>
     * </ul>
     */
    @Test
    void constructor_rejectsPhoneWithNineDigits() {
        assertThatThrownBy(() ->
                new Contact("1", "John", "Doe", "123456789", "123 Main St"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("phone must be exactly 10 digits");
    }

    /**
     * Tests phone with 11 digits (one above required).
     *
     * <p><b>Why this test exists:</b> Ensures that a phone with 11 digits is rejected.
     * Tests the upper boundary of the equality check.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Removed conditional check</li>
     *   <li>Changed != to other operators</li>
     * </ul>
     */
    @Test
    void constructor_rejectsPhoneWithElevenDigits() {
        assertThatThrownBy(() ->
                new Contact("1", "John", "Doe", "12345678901", "123 Main St"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("phone must be exactly 10 digits");
    }

    /**
     * Tests setFirstName with a name at the exact maximum length.
     *
     * <p><b>Why this test exists:</b> Ensures setFirstName validates the boundary
     * correctly and accepts exactly 10 characters.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in setter validation</li>
     * </ul>
     */
    @Test
    void setFirstName_acceptsNameAtMaximumLength() {
        final Contact contact = new Contact("1", "John", "Doe", "5551234567", "123 Main St");

        contact.setFirstName("ABCDEFGHIJ");

        assertThat(contact.getFirstName()).isEqualTo("ABCDEFGHIJ");
        assertThat(contact.getFirstName()).hasSize(10);
    }

    /**
     * Tests setLastName with a name at the exact maximum length.
     *
     * <p><b>Why this test exists:</b> Ensures setLastName validates the boundary
     * correctly and accepts exactly 10 characters.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in setter validation</li>
     * </ul>
     */
    @Test
    void setLastName_acceptsNameAtMaximumLength() {
        final Contact contact = new Contact("1", "John", "Doe", "5551234567", "123 Main St");

        contact.setLastName("ABCDEFGHIJ");

        assertThat(contact.getLastName()).isEqualTo("ABCDEFGHIJ");
        assertThat(contact.getLastName()).hasSize(10);
    }

    /**
     * Tests setAddress with an address at the exact maximum length.
     *
     * <p><b>Why this test exists:</b> Ensures setAddress validates the boundary
     * correctly and accepts exactly 30 characters.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Changed conditional boundary in setter validation</li>
     * </ul>
     */
    @Test
    void setAddress_acceptsAddressAtMaximumLength() {
        final Contact contact = new Contact("1", "John", "Doe", "5551234567", "123 Main St");
        final String maxAddress = "123456789012345678901234567890"; // Exactly 30 chars

        contact.setAddress(maxAddress);

        assertThat(contact.getAddress()).isEqualTo(maxAddress);
        assertThat(contact.getAddress()).hasSize(30);
    }

    /**
     * Tests setPhone with exactly 10 digits.
     *
     * <p><b>Why this test exists:</b> Ensures setPhone validates the exact length
     * correctly through the setter path (not just constructor).
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Negated conditional in phone validation</li>
     * </ul>
     */
    @Test
    void setPhone_acceptsExactlyTenDigits() {
        final Contact contact = new Contact("1", "John", "Doe", "5551234567", "123 Main St");

        contact.setPhone("9876543210");

        assertThat(contact.getPhone()).isEqualTo("9876543210");
        assertThat(contact.getPhone()).hasSize(10);
    }

    /**
     * Tests that update() successfully updates all fields when all values are valid.
     *
     * <p><b>Why this test exists:</b> Ensures the atomic update logic doesn't have
     * bugs where validation passes but assignment fails.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Removed field assignment in update method</li>
     *   <li>Swapped field assignments</li>
     * </ul>
     */
    @Test
    void update_successfullyUpdatesAllFieldsWithValidValues() {
        final Contact contact = new Contact("1", "John", "Doe", "5551234567", "123 Main St");

        contact.update("Jane", "Smith", "9876543210", "456 Oak Ave");

        // Verify all fields were updated correctly
        assertThat(contact.getFirstName()).isEqualTo("Jane");
        assertThat(contact.getLastName()).isEqualTo("Smith");
        assertThat(contact.getPhone()).isEqualTo("9876543210");
        assertThat(contact.getAddress()).isEqualTo("456 Oak Ave");
    }

    /**
     * Tests copy() creates an exact duplicate with same values.
     *
     * <p><b>Why this test exists:</b> Ensures copy() correctly copies all fields
     * and doesn't swap or omit any values.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Swapped constructor arguments in copy</li>
     *   <li>Returned 'this' instead of new instance</li>
     * </ul>
     */
    @Test
    void copy_createsExactDuplicateWithSameValues() {
        final Contact original = new Contact("99", "Alice", "Johnson", "1112223333", "789 Pine Rd");

        final Contact copy = original.copy();

        // Verify copy has exact same values
        assertThat(copy.getContactId()).isEqualTo("99");
        assertThat(copy.getFirstName()).isEqualTo("Alice");
        assertThat(copy.getLastName()).isEqualTo("Johnson");
        assertThat(copy.getPhone()).isEqualTo("1112223333");
        assertThat(copy.getAddress()).isEqualTo("789 Pine Rd");

        // Verify it's a different instance
        assertThat(copy).isNotSameAs(original);
    }

    /**
     * Tests that modifying a copy doesn't affect the original.
     *
     * <p><b>Why this test exists:</b> Ensures copy() creates a true independent copy
     * and doesn't share mutable state with the original.
     *
     * <p><b>Mutants killed:</b>
     * <ul>
     *   <li>Returned 'this' instead of new instance</li>
     * </ul>
     */
    @Test
    void copy_modificationsToopyDontAffectOriginal() {
        final Contact original = new Contact("99", "Alice", "Johnson", "1112223333", "789 Pine Rd");
        final Contact copy = original.copy();

        // Modify the copy
        copy.setFirstName("Bob");
        copy.setLastName("Williams");
        copy.setPhone("4445556666");
        copy.setAddress("321 Elm St");

        // Verify original is unchanged
        assertThat(original.getFirstName()).isEqualTo("Alice");
        assertThat(original.getLastName()).isEqualTo("Johnson");
        assertThat(original.getPhone()).isEqualTo("1112223333");
        assertThat(original.getAddress()).isEqualTo("789 Pine Rd");
    }
}
