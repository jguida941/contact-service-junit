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
            String contactId,
            String firstName,
            String lastName,
            String phone,
            String address) {

        // Use Validation utility for constructor field checks
        Validation.validateLength(contactId, "contactId", 1, 10);
        this.contactId = contactId;

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
    public void setFirstName(String firstName) {
        Validation.validateLength(firstName, "firstName", 1, 10);
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        Validation.validateLength(lastName, "lastName", 1, 10);
        this.lastName = lastName;
    }
    public void setPhone(String phone) {
        Validation.validateNumeric10(phone, "phone");
        this.phone = phone;
    }

    public void setAddress(String address) {
        Validation.validateLength(address, "address", 1, 30);
        this.address = address;
    }
}
