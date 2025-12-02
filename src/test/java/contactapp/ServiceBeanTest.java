package contactapp;

import contactapp.service.AppointmentService;
import contactapp.service.ContactService;
import contactapp.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import contactapp.support.PostgresContainerSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying service beans are properly registered.
 *
 * <p>These tests ensure:
 * <ul>
 *   <li>All @Service classes are discovered by component scanning</li>
 *   <li>Services are injectable via @Autowired</li>
 *   <li>Bean definitions do not have ambiguity issues</li>
 * </ul>
 *
 * <p>Why this matters: If services are not properly annotated or scanned,
 * they won't be available for injection in controllers (Phase 2). This
 * test catches such issues before integration tests fail with cryptic
 * "NoSuchBeanDefinitionException" errors.
 */
@SpringBootTest
@ActiveProfiles("integration")
class ServiceBeanTest extends PostgresContainerSupport {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ContactService contactService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private AppointmentService appointmentService;

    /**
     * Verifies ContactService bean exists and is injectable.
     *
     * <p>The @Autowired field injection above would fail during context
     * loading if the bean were missing. This explicit assertion provides
     * clearer failure messages and documents the expected behavior.
     */
    @Test
    void contactServiceBeanExists() {
        assertThat(contactService).isNotNull();
        assertThat(context.getBean(ContactService.class)).isNotNull();
    }

    /**
     * Verifies TaskService bean exists and is injectable.
     */
    @Test
    void taskServiceBeanExists() {
        assertThat(taskService).isNotNull();
        assertThat(context.getBean(TaskService.class)).isNotNull();
    }

    /**
     * Verifies AppointmentService bean exists and is injectable.
     */
    @Test
    void appointmentServiceBeanExists() {
        assertThat(appointmentService).isNotNull();
        assertThat(context.getBean(AppointmentService.class)).isNotNull();
    }

    /**
     * Verifies all three service beans are singletons by default.
     *
     * <p>Spring beans are singletons unless explicitly scoped otherwise.
     * This test confirms the services share the same instance across
     * injection points, which is the expected behavior for stateful
     * in-memory services.
     */
    @Test
    void serviceBeansAreSingletons() {
        ContactService contact1 = context.getBean(ContactService.class);
        ContactService contact2 = context.getBean(ContactService.class);
        assertThat(contact1).isSameAs(contact2);

        TaskService task1 = context.getBean(TaskService.class);
        TaskService task2 = context.getBean(TaskService.class);
        assertThat(task1).isSameAs(task2);

        AppointmentService appt1 = context.getBean(AppointmentService.class);
        AppointmentService appt2 = context.getBean(AppointmentService.class);
        assertThat(appt1).isSameAs(appt2);
    }
}
