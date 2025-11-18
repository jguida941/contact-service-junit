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

- [x] addContact adds a contact with a unique contactId (2025-11-18)
- [x] addContact rejects duplicate contactId (2025-11-18)
- [x] deleteContact removes contact by contactId (2025-11-18)
- [x] updateFirstName updates firstName by contactId and enforces validation (2025-11-18)
- [x] updateLastName updates lastName by contactId and enforces validation (2025-11-18)
- [x] updatePhone updates phone by contactId and enforces validation (2025-11-18)
- [x] updateAddress updates address by contactId and enforces validation (2025-11-18)

## Tests

- [x] Tests cover valid Contact creation (2025-11-14)
- [x] Tests cover invalid contactId (null and >10) (2025-11-14)
- [x] Tests cover invalid firstName, lastName, phone, address (2025-11-14)
- [x] Tests cover adding a contact (2025-11-18)
- [x] Tests cover rejecting duplicate contactId (2025-11-18)
- [x] Tests cover deleting a contact (2025-11-18)
- [x] Tests cover each update method (2025-11-18)
