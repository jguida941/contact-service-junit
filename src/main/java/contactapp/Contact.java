package contactapp;

/**
 * Contact domain object.
 *
 * Enforces all field constraints from the requirements:
 * - contactId: non-null, length 1–10, not updatable
 * - firstName/lastName: non-null, length 1–10
 * - phone: non-null, exactly 10 numeric digits
 * - address: non-null, length 1–30
 *
 * All violations result in {@link IllegalArgumentException} being thrown
 * by the underlying {@link Validation} helper.
 */
public class Contact {
    private static final int MIN_LENGTH = 1;
    private static final int ID_MAX_LENGTH = 10;
    private static final int NAME_MAX_LENGTH = 10;
    private static final int ADDRESS_MAX_LENGTH = 30;
    private static final int PHONE_LENGTH = 10;

    private final String contactId;
    private String firstName;
    private String lastName;
    private String phone;
    private String address;

    /**
     * Creates a new Contact with the given values.
     *
     * @throws IllegalArgumentException if any field violates the Contact constraints
     */
    public Contact(
            final String contactId,
            final String firstName,
            final String lastName,
            final String phone,
            final String address) {

        // Use Validation utility for constructor field checks
        Validation.validateLength(contactId, "contactId", MIN_LENGTH, ID_MAX_LENGTH);
        this.contactId = contactId.trim(); // normalize ID by trimming whitespace

        // Reuse setter validation for the mutable fields
        setFirstName(firstName);
        setLastName(lastName);
        setPhone(phone);
        setAddress(address);
    }

    // Getters
    public String getContactId() {
        return contactId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    // Setters
    public void setFirstName(final String firstName) {
        this.firstName = validateAndTrimText(firstName, "firstName", NAME_MAX_LENGTH);
    }

    public void setLastName(final String lastName) {
        this.lastName = validateAndTrimText(lastName, "lastName", NAME_MAX_LENGTH);
    }

    public void setPhone(final String phone) {
        this.phone = validatePhoneNumber(phone);
    }

    public void setAddress(final String address) {
        this.address = validateAndTrimText(address, "address", ADDRESS_MAX_LENGTH);
    }

    /**
     * Updates all mutable fields after validating every new value first.
     * If any value fails validation, nothing is changed, so callers never
     * see a partially updated contact. (Atomic update behavior.)
     *
     * @throws IllegalArgumentException if any new value violates the Contact constraints
     */
    public void update(
            final String newFirstName,
            final String newLastName,
            final String newPhone,
            final String newAddress) {
        // Validate all incoming values before mutating state so the update is all-or-nothing
        final String validatedFirst = validateAndTrimText(newFirstName, "firstName", NAME_MAX_LENGTH);
        final String validatedLast = validateAndTrimText(newLastName, "lastName", NAME_MAX_LENGTH);
        final String validatedPhone = validatePhoneNumber(newPhone);
        final String validatedAddress = validateAndTrimText(newAddress, "address", ADDRESS_MAX_LENGTH);

        this.firstName = validatedFirst;
        this.lastName = validatedLast;
        this.phone = validatedPhone;
        this.address = validatedAddress;
    }

    /**
     * Validates min/max length for a text field and returns the trimmed value.
     */
    private static String validateAndTrimText(
            final String value,
            final String label,
            final int maxLength) {
        Validation.validateLength(value, label, MIN_LENGTH, maxLength);
        return value.trim();
    }

    /**
     * Validates a phone entry (digits only, required length) and returns it unchanged.
     */
    private static String validatePhoneNumber(final String phone) {
        Validation.validateNumeric10(phone, "phone", PHONE_LENGTH);
        return phone;
    }
}
