package contactapp.domain;

/**
 * Contact domain object.
 *
 * <p>Enforces all field constraints from the requirements:
 * <ul>
 *   <li>contactId: non-null, length 1-10, not updatable</li>
 *   <li>firstName/lastName: non-null, length 1-10</li>
 *   <li>phone: non-null, exactly 10 numeric digits</li>
 *   <li>address: non-null, length 1-30</li>
 * </ul>
 *
 * <p>All violations result in {@link IllegalArgumentException} being thrown
 * by the underlying {@link Validation} helper.
 *
 * <h2>Design Decisions</h2>
 * <ul>
 *   <li>Class is {@code final} to prevent subclassing that could bypass validation</li>
 *   <li>ID is immutable after construction (stable map keys)</li>
 *   <li>All inputs are trimmed before storage for consistent normalization</li>
 *   <li>{@link #update} validates all fields atomically before mutation</li>
 * </ul>
 *
 * @see Validation
 */
public final class Contact {
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
     * @param contactId unique identifier (required, length 1-10, immutable)
     * @param firstName first name (required, length 1-10)
     * @param lastName  last name (required, length 1-10)
     * @param phone     phone number (required, exactly 10 digits)
     * @param address   address (required, length 1-30)
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
     *
     * <p>If any value fails validation, nothing is changed, so callers never
     * see a partially updated contact. (Atomic update behavior.)
     *
     * @param newFirstName new first name
     * @param newLastName  new last name
     * @param newPhone     new phone number
     * @param newAddress   new address
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
        Validation.validateDigits(phone, "phone", PHONE_LENGTH);
        return phone;
    }

    /**
     * Creates a defensive copy of this Contact.
     *
     * <p>Validates the source state, then reuses the public constructor so
     * defensive copies and validation stay aligned.
     *
     * @return a new Contact with the same field values
     * @throws IllegalArgumentException if internal state is corrupted (null fields)
     */
    public Contact copy() {
        validateCopySource(this);
        return new Contact(this.contactId, this.firstName, this.lastName, this.phone, this.address);
    }

    /**
     * Ensures the source contact has valid internal state before copying.
     */
    private static void validateCopySource(final Contact source) {
        // Note: source cannot be null here because copy() passes 'this'
        if (source.contactId == null
                || source.firstName == null
                || source.lastName == null
                || source.phone == null
                || source.address == null) {
            throw new IllegalArgumentException("contact copy source must not be null");
        }
    }
}
