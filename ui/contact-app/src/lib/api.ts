import type { Contact, ContactRequest, Task, TaskRequest, Appointment, AppointmentRequest, Project, ProjectRequest } from './schemas';
import { queryClient } from './queryClient';

const API_BASE = '/api/v1';
const AUTH_BASE = '/api/auth';

// ==================== Auth Types ====================

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token?: string | null; // omitted/null when using HttpOnly cookies
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  expiresIn: number;
}

// ==================== Session State (in-memory, not localStorage) ====================

const USER_KEY = 'auth_user';
const PROFILE_STORAGE_KEY = 'contact-app-profile';
const SESSION_TIMESTAMP_KEY = 'auth_session_timestamp';
const TOKEN_EXPIRES_AT_KEY = 'auth_token_expires_at';

// In-memory state for current user (not persisted to localStorage for security)
let currentUser: AuthResponse | null = null;
let tokenExpiresAt: number | null = null; // Absolute timestamp when token expires

/**
 * Session storage for user data. Uses sessionStorage instead of localStorage
 * so data is cleared when the browser tab closes.
 */
export const tokenStorage = {
  // Token is now in HttpOnly cookie - these methods are kept for backwards compat
  getToken: (): string | null => null, // Token is in HttpOnly cookie, not accessible to JS
  setToken: (_token: string): void => { /* no-op - token is in cookie */ },
  removeToken: (): void => { /* no-op - cookie cleared by backend */ },

  getUser: (): AuthResponse | null => {
    if (currentUser) return currentUser;
    // Try sessionStorage as fallback (page refresh within session)
    const user = sessionStorage.getItem(USER_KEY);
    if (user) {
      try {
        currentUser = JSON.parse(user);
        // Also restore tokenExpiresAt from sessionStorage
        const expiresAtStr = sessionStorage.getItem(TOKEN_EXPIRES_AT_KEY);
        if (expiresAtStr) {
          const parsed = parseInt(expiresAtStr, 10);
          tokenExpiresAt = isNaN(parsed) ? null : parsed;
        }
        return currentUser;
      } catch {
        sessionStorage.removeItem(USER_KEY);
        sessionStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
        currentUser = null;
        tokenExpiresAt = null;
        return null;
      }
    }
    return null;
  },
  setUser: (user: AuthResponse): void => {
    currentUser = user;
    // Store absolute expiration timestamp (ADR-0052 Phase C)
    tokenExpiresAt = Date.now() + user.expiresIn;
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    sessionStorage.setItem(SESSION_TIMESTAMP_KEY, Date.now().toString());
    sessionStorage.setItem(TOKEN_EXPIRES_AT_KEY, tokenExpiresAt.toString());
  },
  removeUser: (): void => {
    currentUser = null;
    tokenExpiresAt = null;
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(SESSION_TIMESTAMP_KEY);
    sessionStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
  },

  getTokenExpiresAt: (): number | null => {
    if (tokenExpiresAt) return tokenExpiresAt;
    const stored = sessionStorage.getItem(TOKEN_EXPIRES_AT_KEY);
    if (stored) {
      const parsed = parseInt(stored, 10);
      tokenExpiresAt = isNaN(parsed) ? null : parsed;
      return tokenExpiresAt;
    }
    return null;
  },

  clear: (): void => {
    currentUser = null;
    tokenExpiresAt = null;
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(PROFILE_STORAGE_KEY);
    sessionStorage.removeItem(SESSION_TIMESTAMP_KEY);
    sessionStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
    // Also clear any legacy localStorage items
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    localStorage.removeItem(PROFILE_STORAGE_KEY);
  },
};

/**
 * Normalized API error with message and optional field errors.
 */
export interface ApiError {
  message: string;
  status: number;
  errors?: Record<string, string>;
}

/**
 * Type guard to check if an error is an ApiError.
 */
export function isApiError(error: unknown): error is ApiError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'message' in error &&
    'status' in error &&
    typeof (error as ApiError).message === 'string' &&
    typeof (error as ApiError).status === 'number'
  );
}

/**
 * Extract error message from any error type (ApiError, Error, or unknown).
 * Use this in onError handlers to show the actual backend error message.
 */
export function getErrorMessage(error: unknown, fallback: string): string {
  if (isApiError(error)) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}

/**
 * Handles fetch response, throwing ApiError on non-2xx status.
 * Returns parsed JSON for successful responses.
 * Automatically logs out and redirects on 401 errors.
 */
async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    // ADR-0052 Phase 0: Proper 401 vs 403 handling
    // 401 = Unauthenticated (no/invalid/expired token) - clear session and redirect
    // 403 = Forbidden (authenticated but unauthorized) - do NOT clear session
    if (response.status === 401) {
      tokenStorage.clear();
      queryClient.clear(); // Clear cached data to prevent data leakage
      // Redirect to login page if not already there
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login?reason=sessionExpired';
      }
    }
    // 403 = Forbidden - user is authenticated but lacks permission
    // Do NOT clear session or redirect - just throw the error

    let message = `HTTP ${response.status}`;
    let errors: Record<string, string> | undefined;

    try {
      const body = await response.json();
      message = body.message || body.error || message;
      errors = body.errors;
    } catch {
      // Response body is not JSON, use status text
      message = response.statusText || message;
    }

    const error: ApiError = { message, status: response.status, errors };
    throw error;
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return undefined as T;
  }

  return response.json();
}

/**
 * Common fetch options with credentials for cookie-based auth.
 */
const fetchOptions: RequestInit = {
  credentials: 'include', // Include HttpOnly cookies in requests
};

const METHODS_REQUIRING_CSRF = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';

function readCookie(name: string): string | null {
  const value = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`));
  if (!value) return null;
  const separatorIndex = value.indexOf('=');
  if (separatorIndex === -1) return null;
  return decodeURIComponent(value.substring(separatorIndex + 1));
}

async function ensureCsrfToken(): Promise<string | null> {
  // Always read from cookie on every request (no caching)
  const cookieToken = readCookie(CSRF_COOKIE_NAME);
  if (cookieToken) {
    return cookieToken;
  }

  // No cookie found - fetch from endpoint to bootstrap the token
  try {
    const response = await fetch(`${AUTH_BASE}/csrf-token`, {
      credentials: 'include',
    });
    if (!response.ok) {
      return null;
    }
    // After the fetch, the server should have set the XSRF-TOKEN cookie
    // Read it from the cookie (don't trust the response body)
    return readCookie(CSRF_COOKIE_NAME);
  } catch {
    return null;
  }
}

async function fetchWithCsrf(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const finalInit: RequestInit = {
    ...fetchOptions,
    ...init,
  };
  const headers = new Headers(init.headers ?? {});
  finalInit.headers = headers;
  const method = (finalInit.method ?? 'GET').toUpperCase();
  if (METHODS_REQUIRING_CSRF.has(method)) {
    const token = await ensureCsrfToken();
    if (token) {
      headers.set(CSRF_HEADER_NAME, token);
    }
  }
  return fetch(input, finalInit);
}

// ==================== Auth API ====================

// Token refresh configuration
const REFRESH_MARGIN_MS = 5 * 60 * 1000; // Refresh 5 minutes before expiry
let refreshTimerId: ReturnType<typeof setTimeout> | null = null;

export const authApi = {
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await fetchWithCsrf(`${AUTH_BASE}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    const result = await handleResponse<AuthResponse>(response);
    // Token is in HttpOnly cookie, just store user info in session
    tokenStorage.setUser(result);
    syncProfileFromAuth(result);
    scheduleTokenRefresh(result.expiresIn);
    return result;
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await fetchWithCsrf(`${AUTH_BASE}/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    const result = await handleResponse<AuthResponse>(response);
    // Token is in HttpOnly cookie, just store user info in session
    tokenStorage.setUser(result);
    syncProfileFromAuth(result);
    scheduleTokenRefresh(result.expiresIn);
    return result;
  },

  /**
   * Refresh the JWT token. Called automatically before expiry.
   * Returns new auth response if successful, null if refresh failed.
   */
  refresh: async (): Promise<AuthResponse | null> => {
    try {
      const response = await fetchWithCsrf(`${AUTH_BASE}/refresh`, {
        method: 'POST',
      });
      if (!response.ok) {
        // Token expired or invalid - clear session
        cancelTokenRefresh();
        tokenStorage.clear();
        return null;
      }
      const result = await response.json() as AuthResponse;
      tokenStorage.setUser(result);
      syncProfileFromAuth(result);
      scheduleTokenRefresh(result.expiresIn);
      return result;
    } catch {
      // Network error or other failure - don't clear session yet
      // The next API call will trigger 401 if token is truly expired
      return null;
    }
  },

  logout: async (): Promise<void> => {
    // Cancel any pending refresh
    cancelTokenRefresh();
    // Call backend to clear the HttpOnly cookie
    try {
      await fetchWithCsrf(`${AUTH_BASE}/logout`, {
        method: 'POST',
      });
    } catch {
      // Ignore errors - proceed with client-side cleanup regardless
    }
    tokenStorage.clear();
    queryClient.clear(); // Clear cached data to prevent data leakage to next user
  },

  /**
   * Check if user is authenticated. With HttpOnly cookies, we can't
   * directly check the token, so we rely on stored user info and
   * check expiration based on the stored absolute timestamp (ADR-0052 Phase C).
   */
  isAuthenticated: (): boolean => {
    const user = tokenStorage.getUser();
    if (!user) return false;

    // Check if session has expired based on stored absolute timestamp
    const expiresAt = tokenStorage.getTokenExpiresAt();
    if (expiresAt && Date.now() >= expiresAt) {
      // Token expired - clear session
      tokenStorage.clear();
      return false;
    }

    return true;
  },

  getCurrentUser: (): AuthResponse | null => {
    return tokenStorage.getUser();
  },

  /**
   * Initialize token refresh on app load if user is authenticated.
   * Call this from App.tsx to restore refresh scheduling after page reload.
   * Uses stored absolute timestamp to calculate accurate remaining time (ADR-0052 Phase C).
   */
  initializeRefresh: (): void => {
    const user = tokenStorage.getUser();
    if (!user) return;

    const expiresAt = tokenStorage.getTokenExpiresAt();
    if (!expiresAt) {
      // No expiration timestamp - try immediate refresh
      authApi.refresh();
      return;
    }

    const remainingMs = expiresAt - Date.now();
    if (remainingMs <= 0) {
      // Token already expired - try refresh with refresh token
      authApi.refresh();
      return;
    }

    // Schedule refresh based on actual remaining time
    scheduleTokenRefresh(remainingMs);
  },
};

/**
 * Schedule a token refresh before expiry.
 * @param expiresIn Token lifetime in milliseconds
 */
function scheduleTokenRefresh(expiresIn: number): void {
  cancelTokenRefresh();
  // Refresh 5 minutes before expiry, or immediately if less than 5 min left
  const refreshIn = Math.max(expiresIn - REFRESH_MARGIN_MS, 1000);
  refreshTimerId = setTimeout(async () => {
    await authApi.refresh();
  }, refreshIn);
}

/**
 * Cancel any pending token refresh.
 */
function cancelTokenRefresh(): void {
  if (refreshTimerId !== null) {
    clearTimeout(refreshTimerId);
    refreshTimerId = null;
  }
}

function syncProfileFromAuth(response: AuthResponse) {
  const initials = computeInitials(response.username);
  // Use sessionStorage instead of localStorage for security (clears on tab close)
  sessionStorage.setItem(
    PROFILE_STORAGE_KEY,
    JSON.stringify({
      name: response.username,
      email: response.email,
      initials,
    })
  );
}

function computeInitials(name: string): string {
  if (!name?.trim()) {
    return 'U';
  }
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    return parts[0].charAt(0).toUpperCase();
  }
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
}

// ==================== Contacts API ====================

export const contactsApi = {
  getAll: async (): Promise<Contact[]> => {
    const response = await fetchWithCsrf(`${API_BASE}/contacts`);
    return handleResponse<Contact[]>(response);
  },

  getById: async (id: string): Promise<Contact> => {
    const response = await fetchWithCsrf(`${API_BASE}/contacts/${encodeURIComponent(id)}`);
    return handleResponse<Contact>(response);
  },

  create: async (data: ContactRequest): Promise<Contact> => {
    const response = await fetchWithCsrf(`${API_BASE}/contacts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Contact>(response);
  },

  update: async (id: string, data: Partial<ContactRequest>): Promise<Contact> => {
    const response = await fetchWithCsrf(`${API_BASE}/contacts/${encodeURIComponent(id)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Contact>(response);
  },

  delete: async (id: string): Promise<void> => {
    const response = await fetchWithCsrf(`${API_BASE}/contacts/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
    return handleResponse<void>(response);
  },
};

// ==================== Tasks API ====================

export const tasksApi = {
  getAll: async (): Promise<Task[]> => {
    const response = await fetchWithCsrf(`${API_BASE}/tasks`);
    return handleResponse<Task[]>(response);
  },

  getById: async (id: string): Promise<Task> => {
    const response = await fetchWithCsrf(`${API_BASE}/tasks/${encodeURIComponent(id)}`);
    return handleResponse<Task>(response);
  },

  create: async (data: TaskRequest): Promise<Task> => {
    const response = await fetchWithCsrf(`${API_BASE}/tasks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Task>(response);
  },

  update: async (id: string, data: Partial<TaskRequest>): Promise<Task> => {
    const response = await fetchWithCsrf(`${API_BASE}/tasks/${encodeURIComponent(id)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Task>(response);
  },

  delete: async (id: string): Promise<void> => {
    const response = await fetchWithCsrf(`${API_BASE}/tasks/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
    return handleResponse<void>(response);
  },

  archive: async (id: string): Promise<Task> => {
    const response = await fetchWithCsrf(`${API_BASE}/tasks/${encodeURIComponent(id)}/archive`, {
      method: 'PATCH',
    });
    return handleResponse<Task>(response);
  },

  unarchive: async (id: string): Promise<Task> => {
    const response = await fetchWithCsrf(`${API_BASE}/tasks/${encodeURIComponent(id)}/unarchive`, {
      method: 'PATCH',
    });
    return handleResponse<Task>(response);
  },
};

// ==================== Appointments API ====================

export const appointmentsApi = {
  getAll: async (): Promise<Appointment[]> => {
    const response = await fetchWithCsrf(`${API_BASE}/appointments`);
    return handleResponse<Appointment[]>(response);
  },

  getById: async (id: string): Promise<Appointment> => {
    const response = await fetchWithCsrf(`${API_BASE}/appointments/${encodeURIComponent(id)}`);
    return handleResponse<Appointment>(response);
  },

  create: async (data: AppointmentRequest): Promise<Appointment> => {
    const response = await fetchWithCsrf(`${API_BASE}/appointments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Appointment>(response);
  },

  update: async (id: string, data: Partial<AppointmentRequest>): Promise<Appointment> => {
    const response = await fetchWithCsrf(`${API_BASE}/appointments/${encodeURIComponent(id)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Appointment>(response);
  },

  delete: async (id: string): Promise<void> => {
    const response = await fetchWithCsrf(`${API_BASE}/appointments/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
    return handleResponse<void>(response);
  },

  archive: async (id: string): Promise<Appointment> => {
    const response = await fetchWithCsrf(`${API_BASE}/appointments/${encodeURIComponent(id)}/archive`, {
      method: 'PATCH',
    });
    return handleResponse<Appointment>(response);
  },

  unarchive: async (id: string): Promise<Appointment> => {
    const response = await fetchWithCsrf(`${API_BASE}/appointments/${encodeURIComponent(id)}/unarchive`, {
      method: 'PATCH',
    });
    return handleResponse<Appointment>(response);
  },
};

// ==================== Projects API ====================

export const projectsApi = {
  getAll: async (): Promise<Project[]> => {
    const response = await fetchWithCsrf(`${API_BASE}/projects`);
    return handleResponse<Project[]>(response);
  },

  getById: async (id: string): Promise<Project> => {
    const response = await fetchWithCsrf(`${API_BASE}/projects/${encodeURIComponent(id)}`);
    return handleResponse<Project>(response);
  },

  create: async (data: ProjectRequest): Promise<Project> => {
    const response = await fetchWithCsrf(`${API_BASE}/projects`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Project>(response);
  },

  update: async (id: string, data: Partial<ProjectRequest>): Promise<Project> => {
    const response = await fetchWithCsrf(`${API_BASE}/projects/${encodeURIComponent(id)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
    });
    return handleResponse<Project>(response);
  },

  delete: async (id: string): Promise<void> => {
    const response = await fetchWithCsrf(`${API_BASE}/projects/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
    return handleResponse<void>(response);
  },
};

// ==================== Admin API ====================

/**
 * User information returned by admin endpoints.
 */
export interface AdminUser {
  id: string;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  createdAt: string;
}

/**
 * System-wide statistics visible to admins.
 */
export interface SystemStats {
  totalUsers: number;
  totalContacts: number;
  totalTasks: number;
  totalAppointments: number;
  activeUsers: number;
}

/**
 * Audit log entry for tracking system activity.
 */
export interface AuditLogEntry {
  id: string;
  username: string;
  action: string;
  resourceType: string;
  resourceId: string;
  timestamp: string;
  details?: string;
}

/**
 * Admin-only API endpoints.
 * These endpoints require ADMIN role and are protected by @PreAuthorize on the backend.
 * Per ADR-0036, these provide aggregate metrics, user management, and audit trails.
 */
export const adminApi = {
  /**
   * Get all users in the system (admin only).
   */
  getAllUsers: async (): Promise<AdminUser[]> => {
    const response = await fetchWithCsrf(`${API_BASE}/admin/users`);
    return handleResponse<AdminUser[]>(response);
  },

  /**
   * Get system-wide statistics (admin only).
   */
  getSystemStats: async (): Promise<SystemStats> => {
    const response = await fetchWithCsrf(`${API_BASE}/admin/stats`);
    return handleResponse<SystemStats>(response);
  },

  /**
   * Get audit log entries (admin only).
   * Returns recent activity across all users.
   */
  getAuditLog: async (limit = 50): Promise<AuditLogEntry[]> => {
    const response = await fetchWithCsrf(`${API_BASE}/admin/audit-log?limit=${limit}`);
    return handleResponse<AuditLogEntry[]>(response);
  },
};
