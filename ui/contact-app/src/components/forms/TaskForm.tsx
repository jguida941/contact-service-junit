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
import { taskRequestSchema, ValidationLimits, type TaskRequest, type Task } from '@/lib/schemas';
import { projectsApi, adminApi, authApi } from '@/lib/api';

interface TaskFormProps {
  task?: Task;
  onSubmit: (data: TaskRequest) => void;
  onCancel: () => void;
  isLoading?: boolean;
}

export function TaskForm({ task, onSubmit, onCancel, isLoading }: TaskFormProps) {
  const isEdit = !!task;
  const currentUser = authApi.getCurrentUser();
  const isAdmin = currentUser?.role === 'ADMIN';

  // Fetch projects for dropdown
  const { data: projects = [] } = useQuery({
    queryKey: ['projects'],
    queryFn: projectsApi.getAll,
  });

  // Fetch users for assignee dropdown (admin only)
  const { data: users = [] } = useQuery({
    queryKey: ['admin-users'],
    queryFn: adminApi.getAllUsers,
    enabled: isAdmin,
  });

  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<TaskRequest>({
    resolver: zodResolver(taskRequestSchema),
    defaultValues: task
      ? {
          id: task.id,
          name: task.name,
          description: task.description,
          status: task.status,
          dueDate: task.dueDate || undefined,
          projectId: task.projectId || undefined,
          assigneeId: task.assigneeId || undefined,
        }
      : {
          status: 'TODO',
        },
  });

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="space-y-4"
      aria-label={isEdit ? 'Edit task form' : 'Create task form'}
      noValidate
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
        <Label htmlFor="name">Name</Label>
        <Input
          id="name"
          {...register('name')}
          placeholder="Enter task name"
          maxLength={ValidationLimits.MAX_TASK_NAME_LENGTH}
          aria-invalid={errors.name ? 'true' : 'false'}
          aria-describedby={errors.name ? 'name-error name-help' : 'name-help'}
        />
        {errors.name && (
          <p id="name-error" className="text-sm text-destructive" role="alert">
            {errors.name.message}
          </p>
        )}
        <p id="name-help" className="text-xs text-muted-foreground">
          Max {ValidationLimits.MAX_TASK_NAME_LENGTH} characters
        </p>
      </div>

      <div className="space-y-2">
        <Label htmlFor="description">Description</Label>
        <Input
          id="description"
          {...register('description')}
          placeholder="Enter task description"
          maxLength={ValidationLimits.MAX_DESCRIPTION_LENGTH}
          aria-invalid={errors.description ? 'true' : 'false'}
          aria-describedby={errors.description ? 'description-error description-help' : 'description-help'}
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
        <Label htmlFor="status">Status</Label>
        <Controller
          name="status"
          control={control}
          render={({ field }) => (
            <Select onValueChange={field.onChange} defaultValue={field.value}>
              <SelectTrigger id="status" aria-invalid={errors.status ? 'true' : 'false'}>
                <SelectValue placeholder="Select status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="TODO">To Do</SelectItem>
                <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
                <SelectItem value="DONE">Done</SelectItem>
              </SelectContent>
            </Select>
          )}
        />
        {errors.status && (
          <p className="text-sm text-destructive" role="alert">
            {errors.status.message}
          </p>
        )}
      </div>

      <div className="space-y-2">
        <Label htmlFor="dueDate">Due Date (Optional)</Label>
        <Input
          id="dueDate"
          type="date"
          {...register('dueDate')}
          aria-invalid={errors.dueDate ? 'true' : 'false'}
        />
        {errors.dueDate && (
          <p className="text-sm text-destructive" role="alert">
            {errors.dueDate.message}
          </p>
        )}
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

      {isAdmin && (
        <div className="space-y-2">
          <Label htmlFor="assigneeId">Assignee (Optional)</Label>
          <Controller
            name="assigneeId"
            control={control}
            render={({ field }) => (
              <Select
                onValueChange={(value: string) => field.onChange(value || undefined)}
                defaultValue={field.value || undefined}
              >
                <SelectTrigger id="assigneeId">
                  <SelectValue placeholder="Select assignee" />
                </SelectTrigger>
                <SelectContent>
                  {users.map((user) => (
                    <SelectItem key={user.id} value={user.id}>
                      {user.username} ({user.email})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          />
          {errors.assigneeId && (
            <p className="text-sm text-destructive" role="alert">
              {errors.assigneeId.message}
            </p>
          )}
        </div>
      )}

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
