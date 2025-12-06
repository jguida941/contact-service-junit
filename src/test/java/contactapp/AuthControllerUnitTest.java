package contactapp;

import contactapp.api.AuthController;
import contactapp.security.JwtService;
import contactapp.security.RefreshTokenService;
import contactapp.security.Role;
import contactapp.security.TokenFingerprintService;
import contactapp.security.User;
import contactapp.security.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Narrow tests for {@link AuthController} helpers that PIT flagged as uncovered.
 * Integration tests exercise the public endpoints, but we also validate the
 * private cookie extraction logic to prevent regressions when the implementation
 * changes.
 */
class AuthControllerUnitTest {

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private final AuthController controller = new AuthController(
            mock(AuthenticationManager.class),
            mock(UserRepository.class),
            mock(PasswordEncoder.class),
            mock(JwtService.class),
            mock(TokenFingerprintService.class),
            mock(RefreshTokenService.class));

    @Test
    void extractTokenFromCookiesReturnsValueWhenPresent() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("other", "noop"),
                new Cookie(AuthController.AUTH_COOKIE_NAME, "token-123")
        });

        final String token = ReflectionTestUtils.invokeMethod(controller, "extractTokenFromCookies", request);

        assertThat(token).isEqualTo("token-123");
    }

    @Test
    void extractTokenFromCookiesReturnsNullWhenMissing() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{
                new Cookie("random", "value")
        });

        final String token = ReflectionTestUtils.invokeMethod(controller, "extractTokenFromCookies", request);

        assertThat(token).isNull();
    }

    @Test
    void logoutWithoutRefreshCookieRevokesAllUserTokens() {
        // This guards the regression where legacy cookie path scoping omitted the refresh cookie on /logout.
        final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        final TokenFingerprintService fingerprintService = mock(TokenFingerprintService.class);
        final AuthController controllerWithMocks = new AuthController(
                mock(AuthenticationManager.class),
                mock(UserRepository.class),
                mock(PasswordEncoder.class),
                mock(JwtService.class),
                fingerprintService,
                refreshTokenService);

        final String validHash = "$2a$10$7EqJtq98hPqEX7fNZaFWoOBiZFn0eTD1yQbFq6Z9erjRzCQXDpG7W";
        final User principal = new User("principal", "p@example.com", validHash, Role.USER);
        final Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Controller always attempts to clear both current and legacy refresh cookies
        when(refreshTokenService.createClearRefreshTokenCookie())
                .thenReturn(ResponseCookie.from("refresh_token", "")
                        .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth").maxAge(0).build());
        when(refreshTokenService.createLegacyClearRefreshTokenCookie())
                .thenReturn(ResponseCookie.from("refresh_token", "")
                        .httpOnly(true).secure(true).sameSite("Lax").path("/api/auth/refresh").maxAge(0).build());
        when(fingerprintService.createClearCookie(true))
                .thenReturn(ResponseCookie.from("__Secure-Fgp", "")
                        .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(0).build());
        when(fingerprintService.createClearCookie(false))
                .thenReturn(ResponseCookie.from("Fgp", "")
                        .httpOnly(true).secure(false).sameSite("Lax").path("/").maxAge(0).build());

        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(null); // no refresh_token cookie
        final MockHttpServletResponse response = new MockHttpServletResponse();

        controllerWithMocks.logout(request, response);

        Mockito.verify(refreshTokenService).revokeAllUserTokens(principal);
        // Note: SecurityContextHolder cleanup handled by @AfterEach
    }
}
