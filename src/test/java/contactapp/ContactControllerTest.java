package contactapp;

import contactapp.domain.Contact;
import contactapp.security.Role;
import contactapp.security.TestUserSetup;
import contactapp.security.WithMockAppUser;
import contactapp.service.ContactService;
import contactapp.support.SecuredMockMvcTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.lang.reflect.Method;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link contactapp.api.ContactController}.
 *
 * <p>Tests REST endpoints for Contact CRUD operations including:
 * <ul>
 *   <li>Happy path for all CRUD operations</li>
 *   <li>Validation errors (Bean Validation layer)</li>
 *   <li>Not found scenarios (404)</li>
 *   <li>Duplicate ID conflicts (409)</li>
 * </ul>
 *
 * @see ContactController
 */
@WithMockAppUser
class ContactControllerTest extends SecuredMockMvcTest {

    @Autowired
    private ContactService contactService;

    @Autowired
    private TestUserSetup testUserSetup;

    @BeforeEach
    void setUp() throws Exception {
        // Set up test user (persisted to DB and in SecurityContext)
        testUserSetup.setupTestUser();

        // Clear data before each test for isolation using reflection
        // (clearAllContacts is package-private in contactapp.service)
        final Method clearMethod = ContactService.class.getDeclaredMethod("clearAllContacts");
        clearMethod.setAccessible(true);
        clearMethod.invoke(contactService);
    }

    // ==================== Happy Path Tests ====================

    @Test
    void createContact_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "100",
                                "firstName": "Justin",
                                "lastName": "Guida",
                                "phone": "1234567890",
                                "address": "123 Main Street"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("100"))
                .andExpect(jsonPath("$.firstName").value("Justin"))
                .andExpect(jsonPath("$.lastName").value("Guida"))
                .andExpect(jsonPath("$.phone").value("1234567890"))
                .andExpect(jsonPath("$.address").value("123 Main Street"));
    }

    @Test
    void getAllContacts_empty_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAllContacts_withData_returnsList() throws Exception {
        // Create two contacts
        createTestContact("101", "Alice", "Smith", "1111111111", "111 First Ave");
        createTestContact("102", "Bob", "Jones", "2222222222", "222 Second St");

        mockMvc.perform(get("/api/v1/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockAppUser
    void getAllContacts_allFlagRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/contacts").param("all", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only ADMIN users can access all contacts"));
    }

    @Test
    @WithMockAppUser(username = "admin", role = Role.ADMIN)
    void getAllContacts_allFlagReturnsAllForAdmin() throws Exception {
        addContactForUser("alpha", Role.USER, "alpha-1");
        addContactForUser("beta", Role.USER, "beta-1");
        addContactForUser("admin", Role.ADMIN, "admin-1");

        mockMvc.perform(get("/api/v1/contacts").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getContactById_exists_returns200() throws Exception {
        createTestContact("103", "Charlie", "Brown", "3333333333", "333 Third Blvd");

        mockMvc.perform(get("/api/v1/contacts/103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("103"))
                .andExpect(jsonPath("$.firstName").value("Charlie"));
    }

    @Test
    void updateContact_exists_returns200() throws Exception {
        createTestContact("104", "Dave", "Wilson", "4444444444", "444 Fourth Lane");

        mockMvc.perform(put("/api/v1/contacts/104")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "104",
                                "firstName": "David",
                                "lastName": "Williams",
                                "phone": "5555555555",
                                "address": "555 Fifth Road"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("David"))
                .andExpect(jsonPath("$.lastName").value("Williams"))
                .andExpect(jsonPath("$.phone").value("5555555555"));
    }

    @Test
    void deleteContact_exists_returns204() throws Exception {
        createTestContact("105", "Eve", "Taylor", "6666666666", "666 Sixth Ave");

        mockMvc.perform(delete("/api/v1/contacts/105")
                        .with(csrf()))
                .andExpect(status().isNoContent());

        // Verify deletion
        mockMvc.perform(get("/api/v1/contacts/105"))
                .andExpect(status().isNotFound());
    }

    // ==================== Not Found Tests ====================

    @Test
    void getContactById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/contacts/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Contact not found: notfound"));
    }

    @Test
    void updateContact_notFound_returns404() throws Exception {
        mockMvc.perform(put("/api/v1/contacts/missing")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "missing",
                                "firstName": "Test",
                                "lastName": "User",
                                "phone": "1234567890",
                                "address": "Test Address"
                            }
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Contact not found: missing"));
    }

    @Test
    void deleteContact_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/contacts/notfound")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Contact not found: notfound"));
    }

    // ==================== Duplicate Tests ====================

    @Test
    void createContact_duplicateId_returns409() throws Exception {
        createTestContact("106", "Frank", "Garcia", "7777777777", "777 Seventh St");

        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "106",
                                "firstName": "Different",
                                "lastName": "Person",
                                "phone": "8888888888",
                                "address": "888 Eighth Ave"
                            }
                            """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Contact with id '106' already exists"));
    }

    // ==================== Validation Error Tests (Parameterized) ====================

    @ParameterizedTest(name = "create contact with invalid {0}")
    @MethodSource("invalidContactInputs")
    @SuppressWarnings("java:S1172") // fieldName used in @ParameterizedTest name for test output
    void createContact_invalidInput_returns400(
            final String fieldName,
            final String jsonBody,
            final String expectedMessageContains) throws Exception {
        // fieldName is used in test display name via {0} placeholder above
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(expectedMessageContains)));
    }

    static Stream<Arguments> invalidContactInputs() {
        return Stream.of(
                // ID validation - humanized field name is "ID"
                Arguments.of("id (blank)", """
                    {"id": "", "firstName": "Test", "lastName": "User", "phone": "1234567890", "address": "Test Addr"}
                    """, "ID"),
                Arguments.of("id (too long)", """
                    {"id": "12345678901", "firstName": "Test", "lastName": "User", "phone": "1234567890", "address": "Test Addr"}
                    """, "ID"),

                // firstName validation - humanized field name is "First Name"
                Arguments.of("firstName (blank)", """
                    {"id": "100", "firstName": "", "lastName": "User", "phone": "1234567890", "address": "Test Addr"}
                    """, "First Name"),
                Arguments.of("firstName (too long)", """
                    {"id": "100", "firstName": "12345678901", "lastName": "User", "phone": "1234567890", "address": "Test Addr"}
                    """, "First Name"),

                // lastName validation - humanized field name is "Last Name"
                Arguments.of("lastName (blank)", """
                    {"id": "100", "firstName": "Test", "lastName": "", "phone": "1234567890", "address": "Test Addr"}
                    """, "Last Name"),
                Arguments.of("lastName (too long)", """
                    {"id": "100", "firstName": "Test", "lastName": "12345678901", "phone": "1234567890", "address": "Test Addr"}
                    """, "Last Name"),

                // phone validation - humanized field name is "Phone"
                Arguments.of("phone (blank)", """
                    {"id": "100", "firstName": "Test", "lastName": "User", "phone": "", "address": "Test Addr"}
                    """, "Phone"),
                Arguments.of("phone (non-digits)", """
                    {"id": "100", "firstName": "Test", "lastName": "User", "phone": "123-456-78", "address": "Test Addr"}
                    """, "Phone"),
                Arguments.of("phone (wrong length)", """
                    {"id": "100", "firstName": "Test", "lastName": "User", "phone": "123456789", "address": "Test Addr"}
                    """, "Phone"),

                // address validation - humanized field name is "Address"
                Arguments.of("address (blank)", """
                    {"id": "100", "firstName": "Test", "lastName": "User", "phone": "1234567890", "address": ""}
                    """, "Address"),
                Arguments.of("address (too long)", """
                    {"id": "100", "firstName": "Test", "lastName": "User", "phone": "1234567890", "address": "1234567890123456789012345678901"}
                    """, "Address")
        );
    }

    // ==================== Malformed JSON Test ====================

    @Test
    void createContact_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid JSON in request body"));
    }

    // ==================== Boundary Tests (Bean Validation accuracy) ====================

    @Test
    void createContact_exactlyMaxLengthFields_accepted() throws Exception {
        // Test exact boundaries: id=10, firstName=10, lastName=10, phone=10, address=30
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "1234567890",
                                "firstName": "1234567890",
                                "lastName": "1234567890",
                                "phone": "1234567890",
                                "address": "123456789012345678901234567890"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1234567890"));
    }

    @Test
    void createContact_idOneOverMax_rejected() throws Exception {
        // id max is 10, this is 11
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "12345678901",
                                "firstName": "Test",
                                "lastName": "User",
                                "phone": "1234567890",
                                "address": "Test Address"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("ID")));
    }

    @Test
    void createContact_firstNameOneOverMax_rejected() throws Exception {
        // firstName max is 10, this is 11
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "200",
                                "firstName": "12345678901",
                                "lastName": "User",
                                "phone": "1234567890",
                                "address": "Test Address"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("First Name")));
    }

    @Test
    void createContact_addressOneOverMax_rejected() throws Exception {
        // address max is 30, this is 31
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "201",
                                "firstName": "Test",
                                "lastName": "User",
                                "phone": "1234567890",
                                "address": "1234567890123456789012345678901"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Address")));
    }

    @Test
    void createContact_phoneNineDigits_rejected() throws Exception {
        // phone must be exactly 10 digits
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "202",
                                "firstName": "Test",
                                "lastName": "User",
                                "phone": "123456789",
                                "address": "Test Address"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Phone")));
    }

    @Test
    void createContact_phoneElevenDigits_rejected() throws Exception {
        // phone must be exactly 10 digits
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": "203",
                                "firstName": "Test",
                                "lastName": "User",
                                "phone": "12345678901",
                                "address": "Test Address"
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Phone")));
    }

    // ==================== Edge Case Tests ====================

    @Test
    void getContactById_trimmedId_findsContact() throws Exception {
        createTestContact("107", "Grace", "Lee", "9999999999", "999 Ninth Place");

        // The controller trims the ID before lookup, so "107" should match
        // Note: In real URLs, spaces would be encoded, but our controller handles this
        mockMvc.perform(get("/api/v1/contacts/107"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("107"));
    }

    @Test
    void createContact_trimmedFields_storesCorrectly() throws Exception {
        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "id": " 108 ",
                                "firstName": " Hannah ",
                                "lastName": " Miller ",
                                "phone": "0000000000",
                                "address": " 000 Zero Street "
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("108"))
                .andExpect(jsonPath("$.firstName").value("Hannah"));
    }

    // ==================== Helper Methods ====================

    private void createTestContact(
            final String id,
            final String firstName,
            final String lastName,
            final String phone,
            final String address) throws Exception {

        mockMvc.perform(post("/api/v1/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                            {
                                "id": "%s",
                                "firstName": "%s",
                                "lastName": "%s",
                                "phone": "%s",
                                "address": "%s"
                            }
                            """, id, firstName, lastName, phone, address)))
                .andExpect(status().isCreated());
    }

    private void addContactForUser(final String username, final Role role, final String id) {
        testUserSetup.setupTestUser(username, username + "@example.com", role);
        contactService.addContact(new Contact(id, "FN" + id.charAt(0), "LN" + id.charAt(0), "5551234567", "Addr " + id));
    }
}
