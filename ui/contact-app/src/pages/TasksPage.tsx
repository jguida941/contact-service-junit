import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Pencil, Trash2, Archive, ArchiveRestore } from 'lucide-react';
import { formatDateSafe } from '@/lib/dateUtils';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { Pagination } from '@/components/ui/pagination';
import { SortableTableHead } from '@/components/ui/sortable-table-head';
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
import { Badge } from '@/components/ui/badge';
import { TaskForm } from '@/components/forms/TaskForm';
import { DeleteConfirmDialog } from '@/components/dialogs/DeleteConfirmDialog';
import { tasksApi, getErrorMessage } from '@/lib/api';
import { useFilteredSortedPaginatedData } from '@/lib/hooks/useTableState';
import { useToast } from '@/hooks/useToast';
import type { Task, TaskRequest } from '@/lib/schemas';

type SheetMode = 'view' | 'create' | 'edit';

/**
 * Format task due date for display.
 * @see ADR-0053 for timezone handling details.
 */
function formatDueDate(dateString: string | null | undefined): string {
  if (!dateString) return 'No due date';
  return formatDateSafe(dateString);
}

/**
 * TasksPage component with search, pagination, and sorting
 *
 * Features:
 * - Client-side search across all task fields
 * - Pagination with 15 items per page
 * - Sortable columns (ID, Name, Description)
 * - URL query params for bookmarkable state
 * - Create/Edit/View/Delete operations
 * - Visual badge for overdue tasks
 */
export function TasksPage() {
  const [selectedTask, setSelectedTask] = useState<Task | null>(null);
  const [sheetMode, setSheetMode] = useState<SheetMode>('view');
  const [deleteTarget, setDeleteTarget] = useState<Task | null>(null);
  const [showArchived, setShowArchived] = useState(false);

  const queryClient = useQueryClient();
  const toast = useToast();

  // Fetch all tasks from API
  const { data: tasks = [], isLoading, error } = useQuery({
    queryKey: ['tasks'],
    queryFn: tasksApi.getAll,
  });

  // Filter tasks based on archived status
  const filteredTasks = showArchived
    ? tasks
    : tasks.filter((task) => !task.archived);

  // Apply search, sorting, and pagination to the tasks
  // Search across: id, name, description
  const {
    items: paginatedTasks,
    totalItems,
    totalPages,
    currentPage,
    itemsPerPage,
    searchQuery,
    sortConfig,
    setSearch,
    setPage,
    setSort,
  } = useFilteredSortedPaginatedData<Task>(filteredTasks, [
    'id',
    'name',
    'description',
  ]);

  const createMutation = useMutation({
    mutationFn: tasksApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('Task created successfully');
      closeSheet();
    },
    onError: (error) => {
      console.error('Create task failed:', error);
      toast.error(getErrorMessage(error, 'Failed to create task'));
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<TaskRequest> }) =>
      tasksApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('Task updated successfully');
      closeSheet();
    },
    onError: (error) => {
      console.error('Update task failed:', error);
      toast.error(getErrorMessage(error, 'Failed to update task'));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: tasksApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('Task deleted successfully');
      setDeleteTarget(null);
      if (selectedTask?.id === deleteTarget?.id) {
        closeSheet();
      }
    },
    onError: (error) => {
      console.error('Delete task failed:', error);
      toast.error(getErrorMessage(error, 'Failed to delete task'));
    },
  });

  const archiveMutation = useMutation({
    mutationFn: tasksApi.archive,
    onSuccess: (_data, taskId) => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      if (selectedTask?.id === taskId) {
        setSelectedTask({ ...selectedTask, archived: true });
      }
    },
    onError: (error) => {
      console.error('Archive failed:', error);
      toast.error(getErrorMessage(error, 'Failed to archive task'));
    },
  });

  const unarchiveMutation = useMutation({
    mutationFn: tasksApi.unarchive,
    onSuccess: (_data, taskId) => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      if (selectedTask?.id === taskId) {
        setSelectedTask({ ...selectedTask, archived: false });
      }
    },
    onError: (error) => {
      console.error('Unarchive failed:', error);
      toast.error(getErrorMessage(error, 'Failed to unarchive task'));
    },
  });

  const handleArchiveToggle = (task: Task) => {
    if (task.archived) {
      unarchiveMutation.mutate(task.id);
    } else {
      archiveMutation.mutate(task.id);
    }
  };

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
    createMutation.mutate(data);
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

  if (error) {
    return (
      <div className="flex items-center justify-center p-8">
        <p className="text-destructive">Failed to load tasks</p>
      </div>
    );
  }

  const isSheetOpen = sheetMode === 'create' || !!selectedTask;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Tasks</h2>
          <p className="text-muted-foreground">
            Manage your tasks and to-do items.
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() => setShowArchived(!showArchived)}
          >
            {showArchived ? 'Hide Archived' : 'Show Archived'}
          </Button>
          <Button onClick={openCreateSheet}>
            <Plus className="mr-2 h-4 w-4" />
            Add Task
          </Button>
        </div>
      </div>

      {/* Search bar */}
      <SearchInput
        value={searchQuery}
        onChange={setSearch}
        placeholder="Search tasks by ID, name, or description..."
        className="max-w-md"
      />

      {/* Table */}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <SortableTableHead
                sortKey="id"
                currentSort={sortConfig}
                onSort={setSort}
              >
                ID
              </SortableTableHead>
              <SortableTableHead
                sortKey="name"
                currentSort={sortConfig}
                onSort={setSort}
              >
                Name
              </SortableTableHead>
              <SortableTableHead
                sortKey="description"
                currentSort={sortConfig}
                onSort={setSort}
              >
                Description
              </SortableTableHead>
              <SortableTableHead
                sortKey="dueDate"
                currentSort={sortConfig}
                onSort={setSort}
              >
                Due Date
              </SortableTableHead>
              <TableHead className="w-[130px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center">
                  Loading...
                </TableCell>
              </TableRow>
            ) : totalItems === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="text-center text-muted-foreground">
                  {searchQuery
                    ? `No tasks found matching "${searchQuery}".`
                    : 'No tasks found. Add your first task to get started.'}
                </TableCell>
              </TableRow>
            ) : (
              paginatedTasks.map((task) => (
                <TableRow
                  key={task.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => openViewSheet(task)}
                >
                  <TableCell>
                    <Badge variant="outline">{task.id}</Badge>
                  </TableCell>
                  <TableCell className="font-medium">{task.name}</TableCell>
                  <TableCell>{task.description}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <span className={task.isOverdue ? 'text-red-600 font-medium' : ''}>
                        {formatDueDate(task.dueDate)}
                      </span>
                      {task.archived && (
                        <Badge className="bg-gray-500 text-white border-transparent">
                          ARCHIVED
                        </Badge>
                      )}
                      {task.isOverdue && !task.archived && (
                        <Badge className="bg-red-600 text-white border-transparent">
                          OVERDUE
                        </Badge>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEditSheet(task)}
                        aria-label={`Edit task ${task.name}`}
                      >
                        <Pencil className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleArchiveToggle(task)}
                        aria-label={task.archived ? `Unarchive task ${task.name}` : `Archive task ${task.name}`}
                        title={task.archived ? 'Unarchive' : 'Archive'}
                      >
                        {task.archived ? (
                          <ArchiveRestore className="h-4 w-4 text-blue-600" aria-hidden="true" />
                        ) : (
                          <Archive className="h-4 w-4 text-gray-600" aria-hidden="true" />
                        )}
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setDeleteTarget(task)}
                        aria-label={`Delete task ${task.name}`}
                      >
                        <Trash2 className="h-4 w-4 text-destructive" aria-hidden="true" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          onPageChange={setPage}
          itemsPerPage={itemsPerPage}
          totalItems={totalItems}
        />
      )}

      {/* Create/Edit/View Sheet */}
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
                ? 'Add a new task to your list.'
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
                <label className="text-sm font-medium text-muted-foreground">Due Date</label>
                <div className="flex items-center gap-2">
                  <p className={`text-sm ${selectedTask.isOverdue ? 'text-red-600 font-medium' : ''}`}>
                    {formatDueDate(selectedTask.dueDate)}
                  </p>
                  {selectedTask.archived && (
                    <Badge className="bg-gray-500 text-white border-transparent">
                      ARCHIVED
                    </Badge>
                  )}
                  {selectedTask.isOverdue && !selectedTask.archived && (
                    <Badge className="bg-red-600 text-white border-transparent">
                      OVERDUE
                    </Badge>
                  )}
                </div>
              </div>
              <div className="flex gap-2 pt-4">
                <Button onClick={() => openEditSheet(selectedTask)}>
                  <Pencil className="mr-2 h-4 w-4" />
                  Edit
                </Button>
                <Button
                  variant="outline"
                  onClick={() => handleArchiveToggle(selectedTask)}
                >
                  {selectedTask.archived ? (
                    <>
                      <ArchiveRestore className="mr-2 h-4 w-4" />
                      Unarchive
                    </>
                  ) : (
                    <>
                      <Archive className="mr-2 h-4 w-4" />
                      Archive
                    </>
                  )}
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
