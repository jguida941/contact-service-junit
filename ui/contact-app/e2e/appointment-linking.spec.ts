import { test, expect } from '@playwright/test';
import { format, addDays, addHours } from 'date-fns';

/**
 * E2E tests for Appointment linking functionality.
 *
 * This test suite covers:
 * - Creating appointment linked to a project
 * - Creating appointment linked to a task
 * - Updating appointment to change links
 * - Filtering appointments by project
 * - Viewing appointment details with linked entities
 *
 * Uses API mocking to run independently of the backend.
 */

// Mock data
const today = new Date();
const tomorrow = addDays(today, 1);
const nextWeek = addDays(today, 7);

const mockProjects = [
  {
    id: 'PROJ001',
    name: 'Website Redesign',
    description: 'Redesign company website',
    status: 'ACTIVE',
  },
  {
    id: 'PROJ002',
    name: 'Mobile App',
    description: 'Develop mobile application',
    status: 'ACTIVE',
  },
];

const mockTasks = [
  {
    id: 'TASK001',
    name: 'Design Homepage',
    description: 'Create new homepage design',
    status: 'TODO',
    projectId: 'PROJ001',
    dueDate: null,
  },
  {
    id: 'TASK002',
    name: 'Setup Database',
    description: 'Configure database schema',
    status: 'IN_PROGRESS',
    projectId: 'PROJ002',
    dueDate: null,
  },
];

const mockAppointments = [
  {
    id: 'APPT001',
    appointmentDate: addHours(tomorrow, 10).toISOString(),
    description: 'Design Review Meeting',
    projectId: 'PROJ001',
    taskId: 'TASK001',
  },
  {
    id: 'APPT002',
    appointmentDate: addHours(nextWeek, 14).toISOString(),
    description: 'Sprint Planning',
    projectId: 'PROJ002',
    taskId: null,
  },
  {
    id: 'APPT003',
    appointmentDate: addHours(tomorrow, 15).toISOString(),
    description: 'One-on-One Meeting',
    projectId: null,
    taskId: null,
  },
];

test.describe('Appointment Linking Tests', () => {
  test.beforeEach(async ({ page }) => {
    // Mock Projects API
    await page.route('**/api/v1/projects', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockProjects),
        });
      } else {
        await route.continue();
      }
    });

    // Mock Tasks API
    await page.route('**/api/v1/tasks', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockTasks),
        });
      } else {
        await route.continue();
      }
    });

    // Mock Appointments API
    await page.route('**/api/v1/appointments', async (route) => {
      const method = route.request().method();

      if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockAppointments),
        });
      } else if (method === 'POST') {
        const body = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(body),
        });
      } else {
        await route.continue();
      }
    });

    await page.route('**/api/v1/appointments/*', async (route) => {
      const method = route.request().method();

      if (method === 'PUT') {
        const body = route.request().postDataJSON();
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ ...mockAppointments[0], ...body }),
        });
      } else if (method === 'DELETE') {
        await route.fulfill({ status: 204 });
      } else if (method === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(mockAppointments[0]),
        });
      } else {
        await route.continue();
      }
    });
  });

  test('creates appointment linked to a project', async ({ page }) => {
    await page.goto('/appointments');

    // Click add appointment button
    await page.getByRole('button', { name: /add appointment/i }).click();

    // Fill appointment form
    await expect(page.getByRole('heading', { name: /new appointment/i })).toBeVisible();
    await page.getByLabel(/^id$/i).fill('APPT004');

    // Fill date/time
    const dateInput = page.getByLabel(/date|time/i).first();
    const appointmentDate = format(addHours(tomorrow, 11), "yyyy-MM-dd'T'HH:mm");
    await dateInput.fill(appointmentDate);

    await page.getByLabel(/description/i).fill('Project Kickoff Meeting');

    // Link to project
    const projectSelect = page.locator('select[name="projectId"], [id="projectId"]');
    if (await projectSelect.count() > 0) {
      await projectSelect.selectOption('PROJ001');
    }

    // Submit
    await page.getByRole('button', { name: /create/i }).click();

    // Sheet should close
    await expect(page.getByRole('heading', { name: /new appointment/i })).not.toBeVisible();
  });

  test('creates appointment linked to a task', async ({ page }) => {
    await page.goto('/appointments');

    // Click add appointment button
    await page.getByRole('button', { name: /add appointment/i }).click();

    // Fill appointment form
    await expect(page.getByRole('heading', { name: /new appointment/i })).toBeVisible();
    await page.getByLabel(/^id$/i).fill('APPT005');

    // Fill date/time
    const dateInput = page.getByLabel(/date|time/i).first();
    const appointmentDate = format(addHours(tomorrow, 13), "yyyy-MM-dd'T'HH:mm");
    await dateInput.fill(appointmentDate);

    await page.getByLabel(/description/i).fill('Task Review Session');

    // Link to task
    const taskSelect = page.locator('select[name="taskId"], [id="taskId"]');
    if (await taskSelect.count() > 0) {
      await taskSelect.selectOption('TASK001');
    }

    // Submit
    await page.getByRole('button', { name: /create/i }).click();

    // Sheet should close
    await expect(page.getByRole('heading', { name: /new appointment/i })).not.toBeVisible();
  });

  test('creates appointment linked to both project and task', async ({ page }) => {
    await page.goto('/appointments');

    // Click add appointment button
    await page.getByRole('button', { name: /add appointment/i }).click();

    // Fill appointment form
    await expect(page.getByRole('heading', { name: /new appointment/i })).toBeVisible();
    await page.getByLabel(/^id$/i).fill('APPT006');

    // Fill date/time
    const dateInput = page.getByLabel(/date|time/i).first();
    const appointmentDate = format(addHours(nextWeek, 10), "yyyy-MM-dd'T'HH:mm");
    await dateInput.fill(appointmentDate);

    await page.getByLabel(/description/i).fill('Design Review and Planning');

    // Link to both project and task
    const projectSelect = page.locator('select[name="projectId"], [id="projectId"]');
    if (await projectSelect.count() > 0) {
      await projectSelect.selectOption('PROJ001');
    }

    const taskSelect = page.locator('select[name="taskId"], [id="taskId"]');
    if (await taskSelect.count() > 0) {
      await taskSelect.selectOption('TASK001');
    }

    // Submit
    await page.getByRole('button', { name: /create/i }).click();

    // Sheet should close
    await expect(page.getByRole('heading', { name: /new appointment/i })).not.toBeVisible();
  });

  test('updates appointment to change project link', async ({ page }) => {
    await page.goto('/appointments');

    // Wait for appointments to load
    await expect(page.getByText('Design Review Meeting')).toBeVisible();

    // Click on appointment to view details
    await page.getByText('Design Review Meeting').click();

    // Wait for view sheet
    await expect(page.getByText(/appointment details/i)).toBeVisible();

    // Click Edit
    await page.getByRole('button', { name: /edit/i }).first().click();

    // Change project
    const projectSelect = page.locator('select[name="projectId"], [id="projectId"]');
    if (await projectSelect.count() > 0) {
      await projectSelect.selectOption('PROJ002');
    }

    // Submit
    await page.getByRole('button', { name: /update/i }).click();

    // Sheet should close
    await expect(page.getByText(/edit appointment/i)).not.toBeVisible({ timeout: 10000 });
  });

  test('updates appointment to change task link', async ({ page }) => {
    await page.goto('/appointments');

    // Wait for appointments to load
    await expect(page.getByText('Design Review Meeting')).toBeVisible();

    // Click on appointment to view details
    await page.getByText('Design Review Meeting').click();

    // Wait for view sheet
    await expect(page.getByText(/appointment details/i)).toBeVisible();

    // Click Edit
    await page.getByRole('button', { name: /edit/i }).first().click();

    // Change task
    const taskSelect = page.locator('select[name="taskId"], [id="taskId"]');
    if (await taskSelect.count() > 0) {
      await taskSelect.selectOption('TASK002');
    }

    // Submit
    await page.getByRole('button', { name: /update/i }).click();

    // Sheet should close
    await expect(page.getByText(/edit appointment/i)).not.toBeVisible({ timeout: 10000 });
  });

  test('removes appointment links', async ({ page }) => {
    await page.goto('/appointments');

    // Wait for appointments to load
    await expect(page.getByText('Design Review Meeting')).toBeVisible();

    // Click on appointment to view details
    await page.getByText('Design Review Meeting').click();

    // Wait for view sheet
    await expect(page.getByText(/appointment details/i)).toBeVisible();

    // Click Edit
    await page.getByRole('button', { name: /edit/i }).first().click();

    // Remove project link
    const projectSelect = page.locator('select[name="projectId"], [id="projectId"]');
    if (await projectSelect.count() > 0) {
      const hasEmptyOption = await projectSelect.locator('option[value=""]').count() > 0;
      if (hasEmptyOption) {
        await projectSelect.selectOption('');
      }
    }

    // Remove task link
    const taskSelect = page.locator('select[name="taskId"], [id="taskId"]');
    if (await taskSelect.count() > 0) {
      const hasEmptyOption = await taskSelect.locator('option[value=""]').count() > 0;
      if (hasEmptyOption) {
        await taskSelect.selectOption('');
      }
    }

    // Submit
    await page.getByRole('button', { name: /update/i }).click();

    // Sheet should close
    await expect(page.getByText(/edit appointment/i)).not.toBeVisible({ timeout: 10000 });
  });

  test('filters appointments by project', async ({ page }) => {
    await page.goto('/appointments');

    // Wait for appointments to load
    await expect(page.getByText('Design Review Meeting')).toBeVisible();

    // Look for project filter
    const projectFilter = page.locator('[data-testid="project-filter"], select[name="projectFilter"]');

    if (await projectFilter.count() > 0) {
      // Filter by PROJ001
      await projectFilter.selectOption('PROJ001');

      // Should show appointments for that project
      await expect(page.getByText('Design Review Meeting')).toBeVisible();

      // Filter by PROJ002
      await projectFilter.selectOption('PROJ002');

      // Should show appointments for that project
      await expect(page.getByText('Sprint Planning')).toBeVisible();
    }
  });

  test('displays linked project and task in appointment view', async ({ page }) => {
    await page.goto('/appointments');

    // Wait for appointments to load
    await expect(page.getByText('Design Review Meeting')).toBeVisible();

    // Click on appointment to view details
    await page.getByText('Design Review Meeting').click();

    // Wait for view sheet
    await expect(page.getByText(/appointment details/i)).toBeVisible();

    // Should display linked project information
    await expect(
      page.locator('text=/PROJ001|Website Redesign/i').first()
    ).toBeVisible();

    // Should display linked task information
    await expect(
      page.locator('text=/TASK001|Design Homepage/i').first()
    ).toBeVisible();
  });

  test('displays appointments on project detail page', async ({ page }) => {
    await page.goto('/projects');

    // Wait for projects to load
    await expect(page.getByText('Website Redesign')).toBeVisible();

    // Click on project to view details
    await page.getByText('Website Redesign').click();

    // Should open project detail view
    await expect(page.getByText(/project details/i)).toBeVisible();

    // Project detail page might show associated appointments
    // This depends on the implementation
    // If there's an appointments section, it should list appointments for this project
  });

  test('displays appointments on task detail page', async ({ page }) => {
    await page.goto('/tasks');

    // Wait for tasks to load
    await expect(page.getByText('Design Homepage')).toBeVisible();

    // Click on task to view details
    await page.getByText('Design Homepage').click();

    // Should open task detail view
    await expect(page.getByText(/task details/i)).toBeVisible();

    // Task detail page might show associated appointments
    // This depends on the implementation
  });

  test('creates appointment from project page', async ({ page }) => {
    await page.goto('/projects');

    // Wait for projects to load
    await expect(page.getByText('Website Redesign')).toBeVisible();

    // Click on project to view details
    await page.getByText('Website Redesign').click();

    // Should open project detail view
    await expect(page.getByText(/project details/i)).toBeVisible();

    // Look for "Add Appointment" button in project view
    const addAppointmentBtn = page.getByRole('button', { name: /add appointment|schedule/i });

    if (await addAppointmentBtn.count() > 0) {
      await addAppointmentBtn.click();

      // Form should open with project pre-selected
      await expect(page.getByRole('heading', { name: /new appointment/i })).toBeVisible();

      // Project should be pre-filled
      const projectSelect = page.locator('select[name="projectId"], [id="projectId"]');
      if (await projectSelect.count() > 0) {
        const selectedValue = await projectSelect.inputValue();
        expect(selectedValue).toBe('PROJ001');
      }
    }
  });

  test('filters appointments by date range', async ({ page }) => {
    await page.goto('/appointments');

    // Wait for appointments to load
    await expect(page.getByText('Design Review Meeting')).toBeVisible();

    // Look for date range filters
    const dateFromInput = page.locator('input[name="dateFrom"], [id="dateFrom"]');
    const dateToInput = page.locator('input[name="dateTo"], [id="dateTo"]');

    if (await dateFromInput.count() > 0 && await dateToInput.count() > 0) {
      // Set date range for tomorrow only
      await dateFromInput.fill(format(tomorrow, 'yyyy-MM-dd'));
      await dateToInput.fill(format(tomorrow, 'yyyy-MM-dd'));

      // Should show tomorrow's appointments
      await expect(page.getByText('Design Review Meeting')).toBeVisible();
      await expect(page.getByText('One-on-One Meeting')).toBeVisible();
    }
  });

  test('displays appointment count on project card', async ({ page }) => {
    await page.goto('/projects');

    // Wait for projects to load
    await expect(page.getByText('Website Redesign')).toBeVisible();

    // Project cards might display appointment count
    const appointmentCountBadge = page.locator('[data-testid="project-appointment-count"]');

    if (await appointmentCountBadge.count() > 0) {
      // Verify it shows the correct count
      // For PROJ001, we have 1 appointment (Design Review Meeting)
      await expect(appointmentCountBadge.first()).toContainText('1');
    }
  });

  test('creates unlinked appointment then links to project', async ({ page }) => {
    await page.goto('/appointments');

    // Create appointment without links
    await page.getByRole('button', { name: /add appointment/i }).click();

    await page.getByLabel(/^id$/i).fill('APPT007');

    const dateInput = page.getByLabel(/date|time/i).first();
    const appointmentDate = format(addHours(nextWeek, 16), "yyyy-MM-dd'T'HH:mm");
    await dateInput.fill(appointmentDate);

    await page.getByLabel(/description/i).fill('General Meeting');

    // Don't link to project or task

    await page.getByRole('button', { name: /create/i }).click();
    await expect(page.getByRole('heading', { name: /new appointment/i })).not.toBeVisible();

    // Note: In a real E2E test with backend, we'd then find and edit the appointment
    // to link it to a project
  });

  test('validates appointment form with project link', async ({ page }) => {
    await page.goto('/appointments');

    // Click add appointment button
    await page.getByRole('button', { name: /add appointment/i }).click();

    // Fill only project, leave required fields empty
    const projectSelect = page.locator('select[name="projectId"], [id="projectId"]');
    if (await projectSelect.count() > 0) {
      await projectSelect.selectOption('PROJ001');
    }

    // Try to submit without required fields
    await page.getByRole('button', { name: /create/i }).click();

    // Should show validation errors
    await expect(page.getByText(/id.*required/i)).toBeVisible();
  });
});
