package contactapp.security;

import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * Factory that creates a SecurityContext containing a real {@link User} entity.
 *
 * <p>This factory is used by {@link WithMockAppUser} to populate the security
 * context with our concrete User type instead of Spring Security's generic
 * UserDetails stub. This is necessary because our services call
 * {@code (User) authentication.getPrincipal()} and expect the concrete type.
 *
 * @see WithMockAppUser
 */
public class WithMockAppUserSecurityContextFactory
        implements WithSecurityContextFactory<WithMockAppUser> {

    /**
     * BCrypt hash of "password123" for test users.
     * Generated with BCrypt cost factor 10.
     */
    private static final String TEST_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMye3.Jv8Q5J5YJO1gqRbC1I/pL.sZ5g5jC";

    @Override
    public SecurityContext createSecurityContext(final WithMockAppUser annotation) {
        final SecurityContext context = SecurityContextHolder.createEmptyContext();

        // Create a real User entity with the specified attributes
        final User user = new User(
                annotation.username(),
                annotation.email(),
                TEST_PASSWORD_HASH,
                annotation.role()
        );

        // Set the ID via reflection since it's normally set by JPA
        // Generate random UUID if not specified, otherwise parse provided string
        final UUID userId;
        if (annotation.id().isEmpty()) {
            userId = UUID.randomUUID();
        } else {
            try {
                userId = UUID.fromString(annotation.id());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Invalid UUID format in @WithMockAppUser.id(): '" + annotation.id()
                                + "'. Expected format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx", e);
            }
        }
        try {
            final var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set user ID for test", e);
        }

        // Create authentication with the real User as principal
        final Authentication authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                user.getAuthorities()
        );

        context.setAuthentication(authentication);
        return context;
    }
}
