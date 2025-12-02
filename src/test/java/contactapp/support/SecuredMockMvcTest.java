package contactapp.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for integration tests that require authenticated MockMvc requests.
 *
 * <p>In Spring Boot 4.0 / Spring Security 7, MockMvc needs to be explicitly
 * configured with {@code springSecurity()} to properly handle security annotations
 * like {@code @WithMockUser} and {@code @WithSecurityContext}.
 *
 * <p>Usage:
 * <pre>{@code
 * class MyControllerTest extends SecuredMockMvcTest {
 *     @Test
 *     @WithMockAppUser
 *     void myTest() throws Exception {
 *         mockMvc.perform(get("/api/v1/resource"))
 *             .andExpect(status().isOk());
 *     }
 * }
 * }</pre>
 */
@SpringBootTest
@ActiveProfiles("integration")
public abstract class SecuredMockMvcTest extends PostgresContainerSupport {

    @Autowired
    private WebApplicationContext context;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }
}
