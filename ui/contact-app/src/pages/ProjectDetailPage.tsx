import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Plus, Pencil, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { TaskForm } from '@/components/forms/TaskForm';
import { DeleteConfirmDialog } from '@/components/dialogs/DeleteConfirmDialog';
import { EmptyState } from '@/components/ui/empty-state';
import { projectsApi, tasksApi } from '@/lib/api';
import type { Project, Task, TaskRequest, ProjectStatus } from '@/lib/schemas';

type SheetMode = 'view' | 'create' | 'edit';

const STATUS_COLORS: Record<ProjectStatus, string> = {
  ACTIVE: 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-300',
  ON_HOLD: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-300',
  COMPLETED: 'bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-300',
  ARCHIVED: 'bg-gray-100 text-gray-800 dark:bg-gray-900 dark:text-gray-300',
};

const STATUS_LABELS: Record<ProjectStatus, string> = {
  ACTIVE: 'Active',
  ON_HOLD: 'On Hold',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
};

const TASK_STATUS_COLORS = {
  TODO: 'bg-slate-100 text-slate-800 dark:bg-slate-900 dark:text-slate-300',
  IN_PROGRESS: 'bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-300',
  DONE: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900 dark:text-emerald-300',
};

export function ProjectDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [sheetMode, setSheetMode] = useState<SheetMode>('view');
  const [deleteTarget, setDeleteTarget] = useState<Task | null>(null);

  const queryClient = useQueryClient();

  // Fetch project details
  const { data: project, isLoading: isLoadingProject, error: projectError } = useQuery({
    queryKey: ['projects', id],
    queryFn: () => projectsApi.getById(id!),
    enabled: !!id,
  });

  // Fetch all tasks
  const { data: allTasks = [], isLoading: isLoadingTasks } = useQuery({
    queryKey: ['tasks'],
    queryFn: tasksApi.getAll,
  });

  // Filter tasks for this project
  const projectTasks = allTasks.filter((task) => task.projectId === id);

  const createMutation = useMutation({
    mutationFn: tasksApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      closeSheet();
      toast.success('Task created successfully');
    },
    onError: (error: any) => {
      toast.error(error.message || 'Failed to create task');
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<TaskRequest> }) =>
      tasksApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      closeSheet();
      toast.success('Task updated successfully');
    },
    onError: (error: any) => {
      toast.error(error.message || 'Failed to update task');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: tasksApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      setDeleteTarget(null);
      if (selectedTask?.id === deleteTarget?.id) {
        closeSheet();
      }
      toast.success('Task deleted successfully');
    },
    onError: (error: any) => {
      toast.error(error.message || 'Failed to delete task');
    },
  });

  const openCreateSheet = () => {
    setSelectedTask(null);
    setSheetMode('create');
  };

  const openEditSheet = (task: Task) => {
    setSelectedTask(task);
    setSheetMode('edit');
  };

  const openViewSheet = (task: Task) => {
    setSelectedTask(task);
    setSheetMode('view');
  };

  const closeSheet = () => {
    setSelectedTask(null);
    setSheetMode('view');
  };

  const handleCreate = (data: TaskRequest) => {
    // Add the project ID to the task
    createMutation.mutate({
      ...data,
      projectId: id,
    });
  };

  const handleUpdate = (data: TaskRequest) => {
    if (selectedTask) {
      updateMutation.mutate({ id: selectedTask.id, data });
    }
  };

  const handleDelete = () => {
    if (deleteTarget) {
      deleteMutation.mutate(deleteTarget.id);
    }
  };

  if (projectError) {
    return (
      <div className="flex items-center justify-center p-8">
        <div className="text-center">
          <p className="text-destructive mb-4">Failed to load project</p>
          <Button asChild variant="outline">
            <Link to="/projects">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to Projects
            </Link>
          </Button>
        </div>
      </div>
    );
  }

  if (isLoadingProject) {
    return (
      <div className="flex items-center justify-center p-8">
        <p>Loading project...</p>
      </div>
    );
  }

  if (!project) {
    return (
      <div className="flex items-center justify-center p-8">
        <div className="text-center">
          <p className="mb-4">Project not found</p>
          <Button asChild variant="outline">
            <Link to="/projects">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back to Projects
            </Link>
          </Button>
        </div>
      </div>
    );
  }

  const isSheetOpen = sheetMode === 'create' || !!selectedTask;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <Button asChild variant="ghost" size="sm" className="mb-4">
          <Link to="/projects">
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back to Projects
          </Link>
        </Button>

        <div className="flex items-start justify-between">
          <div>
            <h2 className="text-2xl font-bold tracking-tight">{project.name}</h2>
            <p className="text-muted-foreground mt-1">
              {project.description || 'No description'}
            </p>
          </div>
          <Badge className={STATUS_COLORS[project.status]}>
            {STATUS_LABELS[project.status]}
          </Badge>
        </div>
      </div>

      {/* Project Info Card */}
      <Card>
        <CardHeader>
          <CardTitle>Project Information</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="text-sm font-medium text-muted-foreground">Project ID</label>
            <p className="text-sm mt-1">{project.id}</p>
          </div>
          <div>
            <label className="text-sm font-medium text-muted-foreground">Status</label>
            <div className="mt-1">
              <Badge className={STATUS_COLORS[project.status]}>
                {STATUS_LABELS[project.status]}
              </Badge>
            </div>
          </div>
          <div className="sm:col-span-2">
            <label className="text-sm font-medium text-muted-foreground">Total Tasks</label>
            <p className="text-sm mt-1">{projectTasks.length} tasks</p>
          </div>
        </CardContent>
      </Card>

      {/* Tasks Section */}
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>Tasks</CardTitle>
          <Button onClick={openCreateSheet} size="sm">
            <Plus className="mr-2 h-4 w-4" />
            Add Task
          </Button>
        </CardHeader>
        <CardContent>
          {isLoadingTasks ? (
            <div className="text-center py-8">
              <p className="text-muted-foreground">Loading tasks...</p>
            </div>
          ) : projectTasks.length === 0 ? (
            <EmptyState
              icon={Plus}
              title="No tasks yet"
              description="Add tasks to this project to get started."
              action={
                <Button onClick={openCreateSheet} size="sm">
                  <Plus className="mr-2 h-4 w-4" />
                  Add Task
                </Button>
              }
            />
          ) : (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>ID</TableHead>
                    <TableHead>Name</TableHead>
                    <TableHead>Description</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="w-[100px]">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {projectTasks.map((task) => (
                    <TableRow
                      key={task.id}
                      className="cursor-pointer hover:bg-muted/50"
                      onClick={() => openViewSheet(task)}
                    >
                      <TableCell>
                        <Badge variant="outline">{task.id}</Badge>
                      </TableCell>
                      <TableCell className="font-medium">{task.name}</TableCell>
                      <TableCell className="max-w-[300px] truncate">{task.description}</TableCell>
                      <TableCell>
                        <Badge className={TASK_STATUS_COLORS[task.status]}>
                          {task.status.replace('_', ' ')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => openEditSheet(task)}
                            title="Edit task"
                          >
                            <Pencil className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => setDeleteTarget(task)}
                            title="Delete task"
                          >
                            <Trash2 className="h-4 w-4 text-destructive" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Create/Edit/View Task Sheet */}
      <Sheet open={isSheetOpen} onOpenChange={(open) => !open && closeSheet()}>
        <SheetContent>
          <SheetHeader>
            <SheetTitle>
              {sheetMode === 'create'
                ? 'New Task'
                : sheetMode === 'edit'
                ? 'Edit Task'
                : 'Task Details'}
            </SheetTitle>
            <SheetDescription>
              {sheetMode === 'create'
                ? `Add a new task to ${project.name}.`
                : sheetMode === 'edit'
                ? 'Update the task information.'
                : 'View task information.'}
            </SheetDescription>
          </SheetHeader>

          {sheetMode === 'create' && (
            <div className="mt-6">
              <TaskForm
                onSubmit={handleCreate}
                onCancel={closeSheet}
                isLoading={createMutation.isPending}
              />
            </div>
          )}

          {sheetMode === 'edit' && selectedTask && (
            <div className="mt-6">
              <TaskForm
                task={selectedTask}
                onSubmit={handleUpdate}
                onCancel={closeSheet}
                isLoading={updateMutation.isPending}
              />
            </div>
          )}

          {sheetMode === 'view' && selectedTask && (
            <div className="mt-6 space-y-4">
              <div>
                <label className="text-sm font-medium text-muted-foreground">ID</label>
                <p className="text-sm">{selectedTask.id}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Name</label>
                <p className="text-sm">{selectedTask.name}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Description</label>
                <p className="text-sm">{selectedTask.description}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Status</label>
                <div className="mt-1">
                  <Badge className={TASK_STATUS_COLORS[selectedTask.status]}>
                    {selectedTask.status.replace('_', ' ')}
                  </Badge>
                </div>
              </div>
              <div className="flex gap-2 pt-4">
                <Button onClick={() => openEditSheet(selectedTask)}>
                  <Pencil className="mr-2 h-4 w-4" />
                  Edit
                </Button>
                <Button
                  variant="destructive"
                  onClick={() => setDeleteTarget(selectedTask)}
                >
                  <Trash2 className="mr-2 h-4 w-4" />
                  Delete
                </Button>
              </div>
            </div>
          )}
        </SheetContent>
      </Sheet>

      {/* Delete Confirmation Dialog */}
      <DeleteConfirmDialog
        open={!!deleteTarget}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        onConfirm={handleDelete}
        title="Delete Task"
        description={`Are you sure you want to delete "${deleteTarget?.name}"? This action cannot be undone.`}
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}
