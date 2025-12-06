/**
 * Date formatting utilities with timezone-safe parsing.
 *
 * IMPORTANT: Always use parseISO() for API date strings, never raw new Date().
 * See ADR-0053 for details on why this matters.
 *
 * The Problem:
 *   new Date("2025-12-06") parses as UTC midnight → Dec 5th 7PM in EST
 *   parseISO("2025-12-06") parses as local midnight → Dec 6th in any timezone
 *
 * @see https://date-fns.org/docs/parseISO
 */

import { format, parseISO } from 'date-fns';

/**
 * Formats a date-only string (YYYY-MM-DD) for display.
 * Uses parseISO to prevent timezone shift bugs.
 *
 * @param dateString - ISO date string from API (e.g., "2025-12-06")
 * @returns Formatted date string (e.g., "December 6th, 2025") or fallback
 */
export function formatDateSafe(dateString: string | null | undefined): string {
  if (!dateString) return 'No date';
  try {
    return format(parseISO(dateString), 'PPP');
  } catch {
    return dateString;
  }
}

/**
 * Formats a datetime string (ISO 8601) for display.
 * Uses parseISO to handle both date-only and full datetime strings.
 *
 * @param dateString - ISO datetime string from API (e.g., "2025-12-06T10:00:00Z")
 * @returns Formatted datetime string (e.g., "December 6th, 2025 at 10:00 AM")
 */
export function formatDateTimeSafe(dateString: string | null | undefined): string {
  if (!dateString) return 'No date';
  try {
    return format(parseISO(dateString), 'PPpp');
  } catch {
    return dateString;
  }
}

/**
 * Parses an ISO date string safely without timezone shift.
 * Use this when you need a Date object for comparisons.
 *
 * @param dateString - ISO date string
 * @returns Date object or null if invalid
 */
export function parseDateSafe(dateString: string | null | undefined): Date | null {
  if (!dateString) return null;
  try {
    const date = parseISO(dateString);
    // parseISO returns Invalid Date for unparseable strings, check for it
    if (isNaN(date.getTime())) return null;
    return date;
  } catch {
    return null;
  }
}
