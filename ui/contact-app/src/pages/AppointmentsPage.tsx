import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Pencil, Trash2, Archive, ArchiveRestore } from 'lucide-react';
import { formatDateTimeSafe } from '@/lib/dateUtils';
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
import { AppointmentForm } from '@/components/forms/AppointmentForm';
import { DeleteConfirmDialog } from '@/components/dialogs/DeleteConfirmDialog';
import { appointmentsApi } from '@/lib/api';
import { useFilteredSortedPaginatedData } from '@/lib/hooks/useTableState';
import { useToast } from '@/hooks/useToast';
import type { Appointment, AppointmentRequest } from '@/lib/schemas';

type SheetMode = 'view' | 'create' | 'edit';

/**
 * Format appointment date for display.
 * @see ADR-0053 for timezone handling details.
 */
function formatAppointmentDate(dateString: string): string {
  return formatDateTimeSafe(dateString) || dateString;
}

/**
 * AppointmentsPage component with search, pagination, and sorting
 *
 * Features:
 * - Client-side search across all appointment fields
 * - Pagination with 15 items per page
 * - Sortable columns (ID, Date, Description)
 * - URL query params for bookmarkable state
 * - Create/Edit/View/Delete operations
 * - Visual badges for past and archived appointments
 */
export function AppointmentsPage() {
  const [selectedAppointment, setSelectedAppointment] = useState<Appointment | null>(null);
  const [sheetMode, setSheetMode] = useState<SheetMode>('view');
  const [deleteTarget, setDeleteTarget] = useState<Appointment | null>(null);
  const [showArchived, setShowArchived] = useState(false);

  const queryClient = useQueryClient();
  const toast = useToast();

  // Fetch all appointments from API
  const { data: appointments = [], isLoading, error } = useQuery({
    queryKey: ['appointments'],
    queryFn: appointmentsApi.getAll,
  });

  // Filter appointments based on archived status
  const filteredAppointments = showArchived
    ? appointments
    : appointments.filter((apt) => !apt.archived);

  // Apply search, sorting, and pagination to the appointments
  // Search across: id, appointmentDate, description
  const {
    items: paginatedAppointments,
    totalItems,
    totalPages,
    currentPage,
    itemsPerPage,
    searchQuery,
    sortConfig,
    setSearch,
    setPage,
    setSort,
  } = useFilteredSortedPaginatedData<Appointment>(filteredAppointments, [
    'id',
    'appointmentDate',
    'description',
  ]);

  const createMutation = useMutation({
    mutationFn: appointmentsApi.create,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      closeSheet();
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<AppointmentRequest> }) =>
      appointmentsApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      closeSheet();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: appointmentsApi.delete,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      setDeleteTarget(null);
      if (selectedAppointment?.id === deleteTarget?.id) {
        closeSheet();
      }
    },
  });

  const archiveMutation = useMutation({
    mutationFn: appointmentsApi.archive,
    onSuccess: (_, archivedId) => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      // Use functional update to avoid stale closure race condition
      setSelectedAppointment((prev) =>
        prev?.id === archivedId ? { ...prev, archived: true } : prev
      );
    },
    onError: (error) => {
      console.error('Archive failed:', error);
      toast.error(error instanceof Error ? error.message : 'Failed to archive appointment');
    },
  });

  const unarchiveMutation = useMutation({
    mutationFn: appointmentsApi.unarchive,
    onSuccess: (_, unarchivedId) => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] });
      // Use functional update to avoid stale closure race condition
      setSelectedAppointment((prev) =>
        prev?.id === unarchivedId ? { ...prev, archived: false } : prev
      );
    },
    onError: (error) => {
      console.error('Unarchive failed:', error);
      toast.error(error instanceof Error ? error.message : 'Failed to unarchive appointment');
    },
  });

  const handleArchiveToggle = (appointment: Appointment) => {
    if (appointment.archived) {
      unarchiveMutation.mutate(appointment.id);
    } else {
      archiveMutation.mutate(appointment.id);
    }
  };

  const openCreateSheet = () => {
    setSelectedAppointment(null);
    setSheetMode('create');
  };

  const openEditSheet = (appointment: Appointment) => {
    setSelectedAppointment(appointment);
    setSheetMode('edit');
  };

  const openViewSheet = (appointment: Appointment) => {
    setSelectedAppointment(appointment);
    setSheetMode('view');
  };

  const closeSheet = () => {
    setSelectedAppointment(null);
    setSheetMode('view');
  };

  const handleCreate = (data: AppointmentRequest) => {
    createMutation.mutate(data);
  };

  const handleUpdate = (data: AppointmentRequest) => {
    if (selectedAppointment) {
      updateMutation.mutate({ id: selectedAppointment.id, data });
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
        <p className="text-destructive">Failed to load appointments</p>
      </div>
    );
  }

  const isSheetOpen = sheetMode === 'create' || !!selectedAppointment;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Appointments</h2>
          <p className="text-muted-foreground">
            Manage your scheduled appointments.
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
            Add Appointment
          </Button>
        </div>
      </div>

      {/* Search bar */}
      <SearchInput
        value={searchQuery}
        onChange={setSearch}
        placeholder="Search appointments by ID, date, or description..."
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
                sortKey="appointmentDate"
                currentSort={sortConfig}
                onSort={setSort}
              >
                Date
              </SortableTableHead>
              <SortableTableHead
                sortKey="description"
                currentSort={sortConfig}
                onSort={setSort}
              >
                Description
              </SortableTableHead>
              <TableHead className="w-[130px]">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={4} className="text-center">
                  Loading...
                </TableCell>
              </TableRow>
            ) : totalItems === 0 ? (
              <TableRow>
                <TableCell colSpan={4} className="text-center text-muted-foreground">
                  {searchQuery
                    ? `No appointments found matching "${searchQuery}".`
                    : 'No appointments found. Schedule your first appointment to get started.'}
                </TableCell>
              </TableRow>
            ) : (
              paginatedAppointments.map((appointment) => (
                <TableRow
                  key={appointment.id}
                  className="cursor-pointer hover:bg-muted/50"
                  onClick={() => openViewSheet(appointment)}
                >
                  <TableCell>
                    <Badge variant="outline">{appointment.id}</Badge>
                  </TableCell>
                  <TableCell className="font-medium">
                    <div className="flex items-center gap-2">
                      {formatAppointmentDate(appointment.appointmentDate)}
                      {appointment.archived && (
                        <Badge className="bg-gray-500 text-white border-transparent">
                          ARCHIVED
                        </Badge>
                      )}
                      {appointment.isPast && !appointment.archived && (
                        <Badge className="bg-amber-700 text-white border-transparent">
                          PAST
                        </Badge>
                      )}
                    </div>
                  </TableCell>
                  <TableCell>{appointment.description}</TableCell>
                  <TableCell>
                    <div className="flex gap-1" onClick={(e) => e.stopPropagation()}>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEditSheet(appointment)}
                        aria-label={`Edit appointment ${appointment.description}`}
                      >
                        <Pencil className="h-4 w-4" aria-hidden="true" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleArchiveToggle(appointment)}
                        aria-label={appointment.archived ? `Unarchive appointment ${appointment.description}` : `Archive appointment ${appointment.description}`}
                        title={appointment.archived ? 'Unarchive' : 'Archive'}
                      >
                        {appointment.archived ? (
                          <ArchiveRestore className="h-4 w-4 text-blue-600" aria-hidden="true" />
                        ) : (
                          <Archive className="h-4 w-4 text-gray-600" aria-hidden="true" />
                        )}
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setDeleteTarget(appointment)}
                        aria-label={`Delete appointment ${appointment.description}`}
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
                ? 'New Appointment'
                : sheetMode === 'edit'
                ? 'Edit Appointment'
                : 'Appointment Details'}
            </SheetTitle>
            <SheetDescription>
              {sheetMode === 'create'
                ? 'Schedule a new appointment.'
                : sheetMode === 'edit'
                ? 'Update the appointment information.'
                : 'View appointment information.'}
            </SheetDescription>
          </SheetHeader>

          {sheetMode === 'create' && (
            <div className="mt-6">
              <AppointmentForm
                onSubmit={handleCreate}
                onCancel={closeSheet}
                isLoading={createMutation.isPending}
              />
            </div>
          )}

          {sheetMode === 'edit' && selectedAppointment && (
            <div className="mt-6">
              <AppointmentForm
                appointment={selectedAppointment}
                onSubmit={handleUpdate}
                onCancel={closeSheet}
                isLoading={updateMutation.isPending}
              />
            </div>
          )}

          {sheetMode === 'view' && selectedAppointment && (
            <div className="mt-6 space-y-4">
              <div>
                <label className="text-sm font-medium text-muted-foreground">ID</label>
                <p className="text-sm">{selectedAppointment.id}</p>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Date</label>
                <div className="flex items-center gap-2">
                  <p className="text-sm">{formatAppointmentDate(selectedAppointment.appointmentDate)}</p>
                  {selectedAppointment.archived && (
                    <Badge className="bg-gray-500 text-white border-transparent">
                      ARCHIVED
                    </Badge>
                  )}
                  {selectedAppointment.isPast && !selectedAppointment.archived && (
                    <Badge className="bg-amber-700 text-white border-transparent">
                      PAST
                    </Badge>
                  )}
                </div>
              </div>
              <div>
                <label className="text-sm font-medium text-muted-foreground">Description</label>
                <p className="text-sm">{selectedAppointment.description}</p>
              </div>
              <div className="flex gap-2 pt-4">
                <Button onClick={() => openEditSheet(selectedAppointment)}>
                  <Pencil className="mr-2 h-4 w-4" />
                  Edit
                </Button>
                <Button
                  variant="outline"
                  onClick={() => handleArchiveToggle(selectedAppointment)}
                >
                  {selectedAppointment.archived ? (
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
                  onClick={() => setDeleteTarget(selectedAppointment)}
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
        title="Delete Appointment"
        description={`Are you sure you want to delete this appointment? This action cannot be undone.`}
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}
