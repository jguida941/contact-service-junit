package contactapp.api;

import contactapp.api.dto.ContactResponse;
import contactapp.api.dto.ErrorResponse;
import contactapp.api.dto.ProjectContactRequest;
import contactapp.api.dto.ProjectRequest;
import contactapp.api.dto.ProjectResponse;
import contactapp.api.exception.ResourceNotFoundException;
import contactapp.domain.Contact;
import contactapp.domain.Project;
import contactapp.domain.ProjectStatus;
import contactapp.service.ProjectService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import static contactapp.domain.Validation.MAX_ID_LENGTH;

/**
 * REST controller for Project CRUD operations.
 *
 * <p>Provides endpoints at {@code /api/v1/projects} per ADR-0016.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>POST /api/v1/projects - Create a new project (201 Created)</li>
 *   <li>GET /api/v1/projects - List all projects (200 OK)</li>
 *   <li>GET /api/v1/projects/{id} - Get project by ID (200 OK / 404 Not Found)</li>
 *   <li>PUT /api/v1/projects/{id} - Update project (200 OK / 404 Not Found)</li>
 *   <li>DELETE /api/v1/projects/{id} - Delete project (204 No Content / 404 Not Found)</li>
 * </ul>
 *
 * <h2>Validation</h2>
 * <p>Uses two layers of validation:
 * <ol>
 *   <li>Bean Validation on request DTOs ({@code @Valid})</li>
 *   <li>Domain validation via {@link contactapp.domain.Validation} in Project constructor</li>
 * </ol>
 *
 * @see ProjectRequest
 * @see ProjectResponse
 * @see ProjectService
 */
@RestController
@RequestMapping(value = "/api/v1/projects", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Projects", description = "Project CRUD operations")
@SecurityRequirement(name = "bearerAuth")
@Validated
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring-managed singleton service is intentionally stored without copy"
)
public class ProjectController {

    private final ProjectService projectService;

    /**
     * Creates a new ProjectController with the given service.
     *
     * @param projectService the service for project operations
     */
    public ProjectController(final ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * Creates a new project.
     *
     * <p>Requires USER or ADMIN role.
     *
     * @param request the project data
     * @return the created project
     * @throws contactapp.api.exception.DuplicateResourceException if a project with the given ID already exists
     */
    @Operation(summary = "Create a new project")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Project created",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Project with this ID already exists",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ProjectResponse create(@Valid @RequestBody final ProjectRequest request) {
        // Domain constructor validates via Validation.java
        final Project project = new Project(
                request.id(),
                request.name(),
                request.description(),
                request.status()
        );

        projectService.addProject(project);

        return ProjectResponse.from(project);
    }

    /**
     * Returns all projects.
     *
     * <p>Requires USER or ADMIN role.
     * <p>For USER role: returns only the user's projects.
     * <p>For ADMIN role: with ?all=true, returns all projects across all users.
     * <p>Supports optional status filtering via ?status parameter.
     *
     * @param all if true and user is ADMIN, returns all projects (optional, defaults to false)
     * @param status optional status filter (e.g., ACTIVE, COMPLETED)
     * @param authentication the authentication context
     * @return list of projects
     */
    @Operation(summary = "Get all projects",
            description = "Returns projects for the authenticated user. "
                    + "ADMIN users can pass ?all=true to see all projects across all users. "
                    + "Optional status filter can be applied via ?status parameter.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of projects"),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden - ADMIN role required for ?all=true",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<ProjectResponse> getAll(
            @Parameter(description = "If true (ADMIN only), returns all projects across all users")
            @RequestParam(required = false, defaultValue = "false") final boolean all,
            @Parameter(description = "Optional status filter (ACTIVE, ON_HOLD, COMPLETED, ARCHIVED)")
            @RequestParam(required = false) final ProjectStatus status,
            final Authentication authentication) {

        // Handle ?all=true parameter (ADMIN only)
        if (all) {
            if (!isAdmin(authentication)) {
                throw new AccessDeniedException("Only ADMIN users can access all projects");
            }
            final List<Project> projects = status != null
                    ? projectService.getAllProjectsAllUsers().stream()
                            .filter(p -> p.getStatus() == status)
                            .toList()
                    : projectService.getAllProjectsAllUsers();
            return projects.stream()
                    .map(ProjectResponse::from)
                    .toList();
        }

        // Regular user - return their projects only
        if (status != null) {
            return projectService.getProjectsByStatus(status).stream()
                    .map(ProjectResponse::from)
                    .toList();
        }

        return projectService.getAllProjects().stream()
                .map(ProjectResponse::from)
                .toList();
    }

    /**
     * Returns a project by ID.
     *
     * <p>Requires USER or ADMIN role.
     *
     * @param id the project ID
     * @return the project
     * @throws ResourceNotFoundException if no project with the given ID exists
     */
    @Operation(summary = "Get project by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project found",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ProjectResponse getById(
            @Parameter(
                    description = "Project ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        return projectService.getProjectById(id)
                .map(ProjectResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project not found: " + id));
    }

    /**
     * Updates an existing project.
     *
     * <p>Requires USER or ADMIN role.
     *
     * @param id      the project ID (from path)
     * @param request the updated project data
     * @return the updated project
     * @throws ResourceNotFoundException if no project with the given ID exists
     */
    @Operation(summary = "Update an existing project")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Project updated",
                    content = @Content(schema = @Schema(implementation = ProjectResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ProjectResponse update(
            @Parameter(
                    description = "Project ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id,
            @Valid @RequestBody final ProjectRequest request) {

        if (!projectService.updateProject(
                id,
                request.name(),
                request.description(),
                request.status())) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }

        return getById(id);
    }

    /**
     * Deletes a project by ID.
     *
     * <p>Requires USER or ADMIN role.
     *
     * @param id the project ID
     * @throws ResourceNotFoundException if no project with the given ID exists
     */
    @Operation(summary = "Delete a project")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Project deleted"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void delete(
            @Parameter(
                    description = "Project ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String id) {
        if (!projectService.deleteProject(id)) {
            throw new ResourceNotFoundException("Project not found: " + id);
        }
    }

    private boolean isAdmin(final Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> "ROLE_ADMIN".equals(grantedAuthority.getAuthority()));
    }

    // ==================== Contact Linking Endpoints ====================

    /**
     * Adds a contact to a project with an optional role.
     *
     * <p>Requires USER or ADMIN role. Both the project and contact must belong
     * to the authenticated user.
     *
     * @param projectId the project ID
     * @param request the contact ID and optional role
     * @throws ResourceNotFoundException if project or contact not found
     */
    @Operation(summary = "Add contact to project",
            description = "Links a contact to a project with an optional role (e.g., CLIENT, STAKEHOLDER)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contact added to project"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project or contact not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/{projectId}/contacts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void addContactToProject(
            @Parameter(
                    description = "Project ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String projectId,
            @Valid @RequestBody final ProjectContactRequest request) {
        projectService.addContactToProject(projectId, request.contactId(), request.role());
    }

    /**
     * Removes a contact from a project.
     *
     * <p>Requires USER or ADMIN role. Both the project and contact must belong
     * to the authenticated user.
     *
     * @param projectId the project ID
     * @param contactId the contact ID
     * @throws ResourceNotFoundException if project or contact not found
     */
    @Operation(summary = "Remove contact from project",
            description = "Removes the link between a project and a contact")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contact removed from project"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project or contact not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{projectId}/contacts/{contactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void removeContactFromProject(
            @Parameter(
                    description = "Project ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String projectId,
            @Parameter(
                    description = "Contact ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String contactId) {
        if (!projectService.removeContactFromProject(projectId, contactId)) {
            throw new ResourceNotFoundException(
                    "No link exists between project " + projectId + " and contact " + contactId);
        }
    }

    /**
     * Gets all contacts linked to a project.
     *
     * <p>Requires USER or ADMIN role. The project must belong to the authenticated user.
     *
     * @param projectId the project ID
     * @return list of contacts
     * @throws ResourceNotFoundException if project not found
     */
    @Operation(summary = "Get project contacts",
            description = "Returns all contacts linked to a project")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of contacts"),
            @ApiResponse(responseCode = "400", description = "Invalid ID format",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Project not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{projectId}/contacts")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<ContactResponse> getProjectContacts(
            @Parameter(
                    description = "Project ID",
                    schema = @Schema(
                            minLength = 1,
                            maxLength = MAX_ID_LENGTH,
                            pattern = "\\S+"))
            @NotBlank @Size(min = 1, max = MAX_ID_LENGTH) @PathVariable final String projectId) {
        final List<Contact> contacts = projectService.getProjectContacts(projectId);
        return contacts.stream()
                .map(ContactResponse::from)
                .toList();
    }
}
