
# CS230 - Contact Service Milestone 1 Requirements

## Contact Class Requirements

- The contact object shall have a required unique `contactId` String field
  that cannot be longer than 10 characters. The `contactId` field shall not
  be null and shall not be updatable.

- The contact object shall have a required `firstName` String field that
  cannot be longer than 10 characters. The `firstName` field shall not be null.

- The contact object shall have a required `lastName` String field that
  cannot be longer than 10 characters. The `lastName` field shall not be null.

- The contact object shall have a required `phone` String field that must be
  exactly 10 digits in length. The `phone` field shall not be null.

- The contact object shall have a required `address` String field that must
  be no longer than 30 characters. The `address` field shall not be null.


## Contact Service Requirements

- The contact service shall be able to add contacts with a unique `contactId`.
  Adding a contact with a duplicate `contactId` shall fail.

- The contact service shall be able to delete contacts by `contactId`.

- The contact service shall be able to update contact fields by `contactId`.
  The following fields are updatable, and all updates shall obey the same
  validation rules as the constructor:
    - `firstName`
    - `lastName`
    - `phone`
    - `address`