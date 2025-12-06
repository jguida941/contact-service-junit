package contactapp.config;

import org.apache.catalina.Container;
import org.apache.catalina.core.StandardHost;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.apache.tomcat.util.http.SameSiteCookies;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat configuration for JSON error handling and SameSite cookie support.
 *
 * <p>This configuration:
 * <ul>
 *   <li>Registers {@link JsonErrorReportValve} for JSON error responses</li>
 *   <li>Configures RFC 6265 cookie processing with SameSite=Lax default</li>
 * </ul>
 *
 * <p>The Rfc6265CookieProcessor ensures all cookies (including CSRF tokens)
 * are sent with the SameSite attribute, providing CSRF protection at the browser level.
 *
 * @see <a href="https://owasp.org/www-community/SameSite">OWASP SameSite</a>
 */
@Configuration
public class TomcatConfig {

    /**
     * Customizes Tomcat for JSON errors and SameSite cookie support.
     *
     * @return the web server factory customizer
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            // Configure JSON error responses
            final Container parent = context.getParent();
            if (parent instanceof StandardHost host) {
                host.setErrorReportValveClass(JsonErrorReportValve.class.getName());
            }

            // Configure RFC 6265 cookie processor with SameSite=Lax
            // This ensures all cookies (including CSRF) have SameSite attribute
            final Rfc6265CookieProcessor cookieProcessor = new Rfc6265CookieProcessor();
            cookieProcessor.setSameSiteCookies(SameSiteCookies.LAX.getValue());
            context.setCookieProcessor(cookieProcessor);
        });
    }
}
