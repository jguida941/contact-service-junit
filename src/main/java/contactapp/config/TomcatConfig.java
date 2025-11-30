package contactapp.config;

import org.apache.catalina.Container;
import org.apache.catalina.core.StandardHost;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat configuration that registers the custom JSON error valve.
 *
 * <p>This configuration ensures that ALL Tomcat-level errors return JSON responses,
 * including URL decoding failures that occur before requests reach Spring MVC.
 *
 * <p>The {@link JsonErrorReportValve} replaces Tomcat's default HTML error page
 * generation with JSON output, ensuring API consumers always receive parseable
 * responses regardless of where the error occurred.
 */
@Configuration
public class TomcatConfig {

    /**
     * Customizes the Tomcat web server to use our JSON error valve.
     *
     * @return the web server factory customizer
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> factory.addContextCustomizers(context -> {
            final Container parent = context.getParent();
            if (parent instanceof StandardHost host) {
                // Replace the default ErrorReportValve with our JSON version
                host.setErrorReportValveClass(JsonErrorReportValve.class.getName());
            }
        });
    }
}
