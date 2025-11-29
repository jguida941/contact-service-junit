package contactapp.api.dto;

import contactapp.domain.Contact;
import java.util.Objects;

/**
 * Response DTO for Contact data returned by the API.
 *
 * <p>Provides a clean separation between the domain object and the API contract.
 * Uses a factory method to convert from the domain object.
 *
 * @param id        the contact's unique identifier
 * @param firstName the contact's first name
 * @param lastName  the contact's last name
 * @param phone     the contact's phone number
 * @param address   the contact's address
 */
public record ContactResponse(
        String id,
        String firstName,
        String lastName,
        String phone,
        String address
) {
    /**
     * Creates a ContactResponse from a Contact domain object.
     *
     * @param contact the domain object to convert (must not be null)
     * @return a new ContactResponse with the contact's data
     * @throws NullPointerException if contact is null
     */
    public static ContactResponse from(final Contact contact) {
        Objects.requireNonNull(contact, "contact must not be null");
        return new ContactResponse(
                contact.getContactId(),
                contact.getFirstName(),
                contact.getLastName(),
                contact.getPhone(),
                contact.getAddress()
        );
    }
}
