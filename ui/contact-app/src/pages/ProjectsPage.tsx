import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { Plus, Pencil, Trash2, Eye, Filter } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { SearchInput } from '@/components/ui/search-input';
import { Pagination } from '@/components/ui/pagination';
import { SortableTableHead } from '@/components/ui/sortable-table-head';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
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
import { ProjectForm } from '@/components/forms/ProjectForm';
import { DeleteConfirmDialog } from '@/components/dialogs/DeleteConfirmDialog';
import { EmptyState } from '@/components/ui/empty-state';
import { projectsApi, tasksApi } from '@/lib/api';
import { useFilteredSortedPaginatedData } from '@/lib/hooks/useTableState';
import type { Project, ProjectRequest, ProjectStatus } from '@/lib/schemas';

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

export function ProjectsPage() {
  const [selectedProject, setSelectedProject] = useState<Project | null>(null);
  const [sheetMode, setSheetMode] = useState<SheetMode>('view');
  const [deleteTarget, setDeleteTarget] = useState<Project | null>(null);
  const [statusFilter, setStatusFilter] = useState<ProjectStatus | 'ALL'>('ALL');
  const navigate = useNavigate();

  const queryClient = useQueryClient();

  // Fetch all projects from API
  const { data: projects = [], isLoading, error } = useQuery({
    queryKey: ['projects'],
    queryFn: projectsApi.getAll,
  });

  // Fetch all tasks to count tasks per project
  const { data: tasks = [] } = useQuery({
    queryKey: ['tasks'],
    queryFn: tasksApi.getAll,
  });

  // Count tasks per project
  const taskCountByProject = tasks.reduce((acc, task) => {
    if (task.projectId) {
      acc[task.projectId] = (acc[task.projectId] || 0) + 1;
    }
    return acc;
  }, {} as Record<string, number>);

  // Apply status filter
  const filteredProjects = statusFilter === 'ALL'
    ? projects
    : projects.filter((p) => p.status === statusFilter);

  // Apply search, sorting, and pagination
  const {
    items: paginatedProjects,
    totalItems,
    totalPages,
    currentPage,
    itemsPerPage,
    searchQuery,
    sortConfig,
    setSearch,
    setPage,
    setSort,
  } = useFilteredSortedPaginatedData<Project>(filteredProjects, [
    'id',
    'name',
    'description',
  ]);

  const createMutation = useMutation({
    mutationFn: projectsApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      closeSheet();
      toast.success('Project created successfully');
    },
    onError: (error: any) => {
      toast.error(error.message || 'Failed to create project');
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<ProjectRequest> }) =>
      projectsApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      closeSheet();
      toast.success('Project updated successfully');
    },
    onError: (error: any) => {
      toast.error(error.message || 'Failed to update project');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: projectsApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
      setDeleteTarget(null);
      if (selectedProject?.id === deleteTarget?.id) {
        closeSheet();
      }
      toast.success('Project deleted successfully');
    },
    onError: (error: any) => {
      toast.error(error.message || 'Failed to delete project');
    },
  });

  const openCreateSheet = () => {
    setSelectedProject(null);
    setSheetMode('create');
  };

  const openEditSheet = (project: Project) => {
    setSelectedProject(project);
    setSheetMode('edit');
  };

  const openViewSheet = (project: Project) => {
    setSelectedProject(project);
    setSheetMode('view');
  };

  const closeSheet = () => {
    setSelectedProject(null);
    setSheetMode('view');
  };

  const handleCreate = (data: ProjectRequest) => {
    createMutation.mutate(data);
  };

  const handleUpdate = (data: ProjectRequest) => {
    if (selectedProject) {
      updateMutation.mutate({ id: selectedProject.id, data });
    }
  };

  const handleDelete = () => {
    if (deleteTarget) {
      deleteMutation.mutate(deleteTarget.id);
    }
  };

  const handleViewDetails = (project: Project) => {
    navigate(`/projects/${project.id}`);
  };

  if (error) {
    return (
      <div className="flex items-center justify-center p-8">
        <p className="text-destructive">Failed to load projects</p>
      </div>
    );
  }

  const isSheetOpen = sheetMode === 'create' || !!selectedProject;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Projects</h2>
          <p className="text-muted-foreground">
            Manage your projects and track progress.
          </p>
        </div>
        <Button onClick={openCreateSheet}>
          <Plus className="mr-2 h-4 w-4" />
          New Project
        </Button>
      </div>

      {/* Filters and Search */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <SearchInput
          value={searchQuery}
          onChange={setSearch}
          placeholder="Search projects by ID, name, or description..."
          className="max-w-md"
        />

        <div className="flex items-center gap-2">
          <Filter className="h-4 w-4 text-muted-foreground" />
          <Select
            value={statusFilter}
            onValueChange={(value) => setStatusFilter(value as ProjectStatus | 'ALL')}
          >
            <SelectTrigger className="w-[150px]">
              <SelectValue placeholder="Filter by status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Status</SelectItem>
              <SelectItem value="ACTIVE">Active</SelectItem>
              <SelectItem value="ON_HOLD">On Hold</SelectItem>
              <SelectItem value="COMPLETED">Completed</SelectItem>
              <SelectItem value="ARCHIVED">Archived</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

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
                sortKey="status"
                currentSort={sortConfig}
                onSort={setSort}
              >
                Status
              </SortableTableHead>
              <TableHead>Tasks</TableHead>
              <TableHead className="w-[140px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center">
                  Loading...
                </TableCell>
              </TableRow>
            ) : totalItems === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-24 text-center">
                  {searchQuery || statusFilter !== 'ALL' ? (
                    <EmptyState
                      icon={Filter}
                      title="No projects found"
                      description="Try adjusting your search or filter criteria."
                    />
                  ) : (
                    <EmptyState
                      icon={Plus}
                      title="No projects yet"
                      description="Get started by creating your first project."
                      action={
                        <Button onClick={openCreateSheet}>
                          <Plus className="mr-2 h-4 w-4" />
                          New Project
                        </Button>
                      }
                    />
                  )}
                </TableCell>
              </TableRow>
            ) : (
              paginatedProjects.map((project) => (
                <TableRow
                  key={project.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => openViewSheet(project)}
                >
                  <TableCell>
                    <Badge variant="outline">{project.id}</Badge>
                  </TableCell>
                  <TableCell className="font-medium">{project.name}</TableCell>
                  <TableCell className="max-w-[300px] truncate">
                    {project.description || <span className="text-muted-foreground">-</span>}
                  </TableCell>
                  <TableCell>
                    <Badge className={STATUS_COLORS[project.status]}>
                      {STATUS_LABELS[project.status]}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary">
                      {taskCountByProject[project.id] || 0}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleViewDetails(project)}
                        title="View details"
                      >
                        <Eye className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEditSheet(project)}
                        title="Edit project"
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setDeleteTarget(project)}
                        title="Delete project"
                      >
                        <Trash2 className="h-4 w-4 text-destructive" />
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
                ? 'New Project'
                : sheetMode === 'edit'
                ? 'Edit Project'
                : 'Project Details'}
            </SheetTitle>
            <SheetDescription>
              {sheetMode === 'create'
                ? 'Add a new project to your workspace.'
                : sheetMode === 'edit'
                ? 'Update the project information.'
                : 'View project information.'}
            </SheetDescription>
          </SheetHeader>

          {sheetMode === 'create' && (
            <div className="mt-6">
              <ProjectForm
                onSubmit={handleCreate}
                onCancel={closeSheet}
                isLoading={createMutation.isPending}
              />
            </div>
          )}

          {sheetMode === 'edit' && selectedProject && (
            <div className="mt-6">
              <ProjectForm
                project={selectedProject}
                onSubmit={handleUpdate}
                onCancel={closeSheet}
                isLoading={updateMutation.isPending}
              />
            </div>
          )}

          {sheetMode === 'view' && selectedProject && (
            <div className="mt-6 space-y-4">
              <div>
                <label className="text-sm font-medium text-muted-foreground">ID</label>
                <p className="text-sm">{selectedProject.id}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Name</label>
                <p className="text-sm">{selectedProject.name}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Description</label>
                <p className="text-sm">{selectedProject.description || '-'}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Status</label>
                <div className="mt-1">
                  <Badge className={STATUS_COLORS[selectedProject.status]}>
                    {STATUS_LABELS[selectedProject.status]}
                  </Badge>
                </div>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Tasks</label>
                <p className="text-sm">{taskCountByProject[selectedProject.id] || 0} tasks</p>
              </div>
              <div className="flex gap-2 pt-4">
                <Button onClick={() => handleViewDetails(selectedProject)} className="flex-1">
                  <Eye className="mr-2 h-4 w-4" />
                  View Details
                </Button>
                <Button onClick={() => openEditSheet(selectedProject)} variant="outline">
                  <Pencil className="mr-2 h-4 w-4" />
                  Edit
                </Button>
                <Button
                  variant="destructive"
                  size="icon"
                  onClick={() => setDeleteTarget(selectedProject)}
                >
                  <Trash2 className="h-4 w-4" />
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
        title="Delete Project"
        description={`Are you sure you want to delete "${deleteTarget?.name}"? This action cannot be undone.`}
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}
