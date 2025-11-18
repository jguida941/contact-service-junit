# CS-320 Module 3 - Requirements Checklist

## Contact Class

- [x] contactId is required (2025-11-14)
- [x] contactId is not null (2025-11-14)
- [x] contactId length <= 10 (2025-11-14)
- [x] contactId is not updatable after construction (2025-11-14)

- [x] firstName is required (2025-11-14)
- [x] firstName is not null (2025-11-14)
- [x] firstName length <= 10 (2025-11-14)

- [x] lastName is required (2025-11-14)
- [x] lastName is not null (2025-11-14)
- [x] lastName length <= 10 (2025-11-14)

- [x] phone is required (2025-11-14)
- [x] phone is not null (2025-11-14)
- [x] phone length == 10 (2025-11-14)

- [x] address is required (2025-11-14)
- [x] address is not null (2025-11-14)
- [x] address length <= 30 (2025-11-14)

## ContactService

- [ ] addContact adds a contact with a unique contactId
- [ ] addContact rejects duplicate contactId
- [ ] deleteContact removes contact by contactId
- [ ] updateFirstName updates firstName by contactId and enforces validation
- [ ] updateLastName updates lastName by contactId and enforces validation
- [ ] updatePhone updates phone by contactId and enforces validation
- [ ] updateAddress updates address by contactId and enforces validation

## Tests

- [x] Tests cover valid Contact creation (2025-11-14)
- [x] Tests cover invalid contactId (null and >10) (2025-11-14)
- [x] Tests cover invalid firstName, lastName, phone, address (2025-11-14)
- [ ] Tests cover adding a contact
- [ ] Tests cover rejecting duplicate contactId
- [ ] Tests cover deleting a contact
- [ ] Tests cover each update method
