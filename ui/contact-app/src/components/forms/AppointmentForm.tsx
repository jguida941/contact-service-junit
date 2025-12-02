import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { appointmentRequestSchema, ValidationLimits, type AppointmentRequest, type Appointment } from '@/lib/schemas';
import { projectsApi, tasksApi } from '@/lib/api';

interface AppointmentFormProps {
  appointment?: Appointment;
  onSubmit: (data: AppointmentRequest) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

function toDateTimeLocalValue(isoString: string): string {
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    // Format: YYYY-MM-DDTHH:mm (for datetime-local input)
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  } catch {
    return '';
  }
}

function fromDateTimeLocalValue(localValue: string): string {
  if (!localValue) return '';

  try {
    // datetime-local gives us "YYYY-MM-DDTHH:mm" in local time
    // But user might type in various formats, so try to parse flexibly
    let date: Date;

    // If it looks like datetime-local format (YYYY-MM-DDTHH:mm)
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(localValue)) {
      date = new Date(localValue);
    }
    // If user typed date without time, add current time
    else if (/^\d{4}-\d{2}-\d{2}$/.test(localValue)) {
      const now = new Date();
      date = new Date(`${localValue}T${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`);
    }
    // Try parsing as-is
    else {
      date = new Date(localValue);
    }

    // Validate the date is valid
    if (isNaN(date.getTime())) {
      if (import.meta.env.DEV) {
        console.warn('Invalid date value:', localValue);
      }
      return localValue; // Return as-is, let backend validate
    }

    // Backend expects ISO 8601: "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
    return date.toISOString();
  } catch (e) {
    if (import.meta.env.DEV) {
      console.warn('Date parsing error:', e);
    }
    return localValue;
  }
}

export function AppointmentForm({ appointment, onSubmit, onCancel, isLoading }: AppointmentFormProps) {
  const isEdit = !!appointment;

  // Fetch projects for dropdown
  const { data: projects = [] } = useQuery({
    queryKey: ['projects'],
    queryFn: projectsApi.getAll,
  });

  // Fetch tasks for dropdown
  const { data: tasks = [] } = useQuery({
    queryKey: ['tasks'],
    queryFn: tasksApi.getAll,
  });

  const {
    register,
    handleSubmit,
    control,
    watch,
    formState: { errors },
  } = useForm<AppointmentRequest>({
    resolver: zodResolver(appointmentRequestSchema),
    defaultValues: appointment
      ? {
          id: appointment.id,
          appointmentDate: toDateTimeLocalValue(appointment.appointmentDate),
          description: appointment.description,
          projectId: appointment.projectId || undefined,
          taskId: appointment.taskId || undefined,
        }
      : undefined,
  });

  // Watch selected project to filter tasks
  const selectedProjectId = watch('projectId');

  const onFormSubmit = (data: AppointmentRequest) => {
    // Convert datetime-local value to ISO string
    onSubmit({
      ...data,
      appointmentDate: fromDateTimeLocalValue(data.appointmentDate),
    });
  };

  return (
    <form
      onSubmit={handleSubmit(onFormSubmit)}
      className="space-y-4"
      aria-label={isEdit ? 'Edit appointment form' : 'Create appointment form'}
    >
      {isEdit ? (
        // Hidden input to include ID in edit submissions
        <input type="hidden" {...register('id')} />
      ) : (
        <div className="space-y-2">
          <Label htmlFor="id">ID</Label>
          <Input
            id="id"
            {...register('id')}
            placeholder="Unique ID (max 10 chars)"
            maxLength={ValidationLimits.MAX_ID_LENGTH}
            aria-invalid={errors.id ? 'true' : 'false'}
            aria-describedby={errors.id ? 'id-error' : undefined}
          />
          {errors.id && (
            <p id="id-error" className="text-sm text-destructive" role="alert">
              {errors.id.message}
            </p>
          )}
        </div>
      )}

      <div className="space-y-2">
        <Label htmlFor="appointmentDate">Date & Time</Label>
        <Input
          id="appointmentDate"
          type="datetime-local"
          {...register('appointmentDate')}
          aria-invalid={errors.appointmentDate ? 'true' : 'false'}
          aria-describedby={errors.appointmentDate ? 'appointmentDate-error appointmentDate-help' : 'appointmentDate-help'}
          required
        />
        {errors.appointmentDate && (
          <p id="appointmentDate-error" className="text-sm text-destructive" role="alert">
            {errors.appointmentDate.message}
          </p>
        )}
        <p id="appointmentDate-help" className="text-xs text-muted-foreground">
          Must be in the future
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">Description</Label>
        <Input
          id="description"
          {...register('description')}
          placeholder="Enter appointment description"
          maxLength={ValidationLimits.MAX_DESCRIPTION_LENGTH}
          aria-invalid={errors.description ? 'true' : 'false'}
          aria-describedby={errors.description ? 'description-error description-help' : 'description-help'}
          required
        />
        {errors.description && (
          <p id="description-error" className="text-sm text-destructive" role="alert">
            {errors.description.message}
          </p>
        )}
        <p id="description-help" className="text-xs text-muted-foreground">
          Max {ValidationLimits.MAX_DESCRIPTION_LENGTH} characters
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="projectId">Project (Optional)</Label>
        <Controller
          name="projectId"
          control={control}
          render={({ field }) => (
            <Select onValueChange={field.onChange} defaultValue={field.value || undefined}>
              <SelectTrigger id="projectId">
                <SelectValue placeholder="Select project" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">None</SelectItem>
                {projects.map((project) => (
                  <SelectItem key={project.id} value={project.id}>
                    {project.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        />
        {errors.projectId && (
          <p className="text-sm text-destructive" role="alert">
            {errors.projectId.message}
          </p>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="taskId">Task (Optional)</Label>
        <Controller
          name="taskId"
          control={control}
          render={({ field }) => (
            <Select onValueChange={field.onChange} defaultValue={field.value || undefined}>
              <SelectTrigger id="taskId">
                <SelectValue placeholder="Select task" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="">None</SelectItem>
                {tasks
                  .filter((task) => !selectedProjectId || task.projectId === selectedProjectId)
                  .map((task) => (
                    <SelectItem key={task.id} value={task.id}>
                      {task.name}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          )}
        />
        {errors.taskId && (
          <p className="text-sm text-destructive" role="alert">
            {errors.taskId.message}
          </p>
        )}
        {selectedProjectId && (
          <p className="text-xs text-muted-foreground">
            Showing tasks from selected project
          </p>
        )}
      </div>

      <div className="flex justify-end gap-2 pt-4">
        <Button type="button" variant="outline" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" disabled={isLoading}>
          {isLoading ? 'Saving...' : isEdit ? 'Update' : 'Create'}
        </Button>
      </div>
    </form>
  );
}
