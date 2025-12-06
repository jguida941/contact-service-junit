import { z } from 'zod';

/**
 * Validation constants matching backend Validation.java
 * @see src/main/java/contactapp/domain/Validation.java
 */
export const ValidationLimits = {
  MAX_ID_LENGTH: 10,
  MAX_NAME_LENGTH: 10,
  MAX_ADDRESS_LENGTH: 30,
  MAX_TASK_NAME_LENGTH: 20,
  MAX_DESCRIPTION_LENGTH: 50,
  PHONE_LENGTH: 10,
} as const;

// ==================== Contact Schemas ====================

export const contactSchema = z.object({
  id: z
    .string()
    .min(1, 'ID is required')
    .max(ValidationLimits.MAX_ID_LENGTH, `ID must be at most ${ValidationLimits.MAX_ID_LENGTH} characters`),
  firstName: z
    .string()
    .min(1, 'First name is required')
    .max(ValidationLimits.MAX_NAME_LENGTH, `First name must be at most ${ValidationLimits.MAX_NAME_LENGTH} characters`),
  lastName: z
    .string()
    .min(1, 'Last name is required')
    .max(ValidationLimits.MAX_NAME_LENGTH, `Last name must be at most ${ValidationLimits.MAX_NAME_LENGTH} characters`),
  phone: z
    .string()
    .length(ValidationLimits.PHONE_LENGTH, `Phone must be exactly ${ValidationLimits.PHONE_LENGTH} digits`)
    .regex(/^\d+$/, 'Phone must only contain digits'),
  address: z
    .string()
    .min(1, 'Address is required')
    .max(ValidationLimits.MAX_ADDRESS_LENGTH, `Address must be at most ${ValidationLimits.MAX_ADDRESS_LENGTH} characters`),
});

export const contactRequestSchema = contactSchema;

export type Contact = z.infer<typeof contactSchema>;
export type ContactRequest = z.infer<typeof contactRequestSchema>;

// ==================== Task Schemas ====================

export const TaskStatusEnum = z.enum(['TODO', 'IN_PROGRESS', 'DONE']);
export type TaskStatus = z.infer<typeof TaskStatusEnum>;

export const taskSchema = z.object({
  id: z
    .string()
    .min(1, 'ID is required')
    .max(ValidationLimits.MAX_ID_LENGTH, `ID must be at most ${ValidationLimits.MAX_ID_LENGTH} characters`),
  name: z
    .string()
    .min(1, 'Name is required')
    .max(ValidationLimits.MAX_TASK_NAME_LENGTH, `Name must be at most ${ValidationLimits.MAX_TASK_NAME_LENGTH} characters`),
  description: z
    .string()
    .min(1, 'Description is required')
    .max(ValidationLimits.MAX_DESCRIPTION_LENGTH, `Description must be at most ${ValidationLimits.MAX_DESCRIPTION_LENGTH} characters`),
  status: TaskStatusEnum,
  dueDate: z.string().optional().nullable(),
  projectId: z.string().optional().nullable(),
  assigneeId: z.string().optional().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
  isOverdue: z.boolean().optional(),
  archived: z.boolean().optional(),
});

export const taskRequestSchema = z.object({
  id: z
    .string()
    .min(1, 'ID is required')
    .max(ValidationLimits.MAX_ID_LENGTH, `ID must be at most ${ValidationLimits.MAX_ID_LENGTH} characters`),
  name: z
    .string()
    .min(1, 'Name is required')
    .max(ValidationLimits.MAX_TASK_NAME_LENGTH, `Name must be at most ${ValidationLimits.MAX_TASK_NAME_LENGTH} characters`),
  description: z
    .string()
    .min(1, 'Description is required')
    .max(ValidationLimits.MAX_DESCRIPTION_LENGTH, `Description must be at most ${ValidationLimits.MAX_DESCRIPTION_LENGTH} characters`),
  status: TaskStatusEnum.default('TODO'),
  dueDate: z.string().optional().nullable(),
  projectId: z.string().optional().nullable(),
  assigneeId: z.string().optional().nullable(),
});

export type Task = z.infer<typeof taskSchema>;
export type TaskRequest = z.infer<typeof taskRequestSchema>;

// ==================== Appointment Schemas ====================

export const appointmentSchema = z.object({
  id: z
    .string()
    .min(1, 'ID is required')
    .max(ValidationLimits.MAX_ID_LENGTH, `ID must be at most ${ValidationLimits.MAX_ID_LENGTH} characters`),
  appointmentDate: z.string().datetime({ message: 'Invalid date format' }),
  description: z
    .string()
    .min(1, 'Description is required')
    .max(ValidationLimits.MAX_DESCRIPTION_LENGTH, `Description must be at most ${ValidationLimits.MAX_DESCRIPTION_LENGTH} characters`),
  projectId: z.string().optional().nullable(),
  taskId: z.string().optional().nullable(),
  isPast: z.boolean().optional(),
  archived: z.boolean().optional(),
});

export const appointmentRequestSchema = z.object({
  id: z
    .string()
    .min(1, 'ID is required')
    .max(ValidationLimits.MAX_ID_LENGTH, `ID must be at most ${ValidationLimits.MAX_ID_LENGTH} characters`),
  appointmentDate: z.string().min(1, 'Date is required'),
  description: z
    .string()
    .min(1, 'Description is required')
    .max(ValidationLimits.MAX_DESCRIPTION_LENGTH, `Description must be at most ${ValidationLimits.MAX_DESCRIPTION_LENGTH} characters`),
  projectId: z.string().optional().nullable(),
  taskId: z.string().optional().nullable(),
});

export type Appointment = z.infer<typeof appointmentSchema>;
export type AppointmentRequest = z.infer<typeof appointmentRequestSchema>;

// ==================== Project Schemas ====================

export const ProjectStatusEnum = z.enum(['ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED']);
export type ProjectStatus = z.infer<typeof ProjectStatusEnum>;

export const ValidationLimitsProject = {
  MAX_PROJECT_NAME_LENGTH: 50,
  MAX_PROJECT_DESCRIPTION_LENGTH: 100,
} as const;

export const projectSchema = z.object({
  id: z
    .string()
    .min(1, 'ID is required')
    .max(ValidationLimits.MAX_ID_LENGTH, `ID must be at most ${ValidationLimits.MAX_ID_LENGTH} characters`),
  name: z
    .string()
    .min(1, 'Name is required')
    .max(ValidationLimitsProject.MAX_PROJECT_NAME_LENGTH, `Name must be at most ${ValidationLimitsProject.MAX_PROJECT_NAME_LENGTH} characters`),
  description: z
    .string()
    .max(ValidationLimitsProject.MAX_PROJECT_DESCRIPTION_LENGTH, `Description must be at most ${ValidationLimitsProject.MAX_PROJECT_DESCRIPTION_LENGTH} characters`),
  status: ProjectStatusEnum,
});

export const projectRequestSchema = projectSchema;

export type Project = z.infer<typeof projectSchema>;
export type ProjectRequest = z.infer<typeof projectRequestSchema>;
