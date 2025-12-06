/**
 * Tests for date formatting utilities.
 *
 * These tests specifically catch timezone shift bugs where:
 *   new Date("2025-12-06") → Dec 5th in EST (WRONG!)
 *   parseISO("2025-12-06") → Dec 6th in any timezone (CORRECT)
 *
 * @see ADR-0053 for the full explanation of this bug and fix.
 */

import { describe, it, expect } from 'vitest';
import { formatDateSafe, formatDateTimeSafe, parseDateSafe } from './dateUtils';

describe('dateUtils', () => {
  describe('formatDateSafe', () => {
    it('formats date-only string without timezone shift', () => {
      // This is the critical test - "2025-12-06" must display as December 6th
      // NOT December 5th (which happens with raw new Date() in negative UTC offsets)
      const result = formatDateSafe('2025-12-06');
      expect(result).toContain('December');
      expect(result).toContain('6');
      // Verify it's the 6th, not the 5th (timezone shift bug)
      expect(result).toMatch(/6(th|,)/); // "6th" or "6," depending on locale
    });

    it('formats various date strings correctly', () => {
      expect(formatDateSafe('2025-01-15')).toContain('January');
      expect(formatDateSafe('2025-01-15')).toContain('15');

      expect(formatDateSafe('2024-07-04')).toContain('July');
      expect(formatDateSafe('2024-07-04')).toContain('4');
    });

    it('handles null and undefined', () => {
      expect(formatDateSafe(null)).toBe('No date');
      expect(formatDateSafe(undefined)).toBe('No date');
    });

    it('handles empty string', () => {
      // parseISO on empty string throws, should fallback
      const result = formatDateSafe('');
      expect(result).toBe('No date');
    });

    it('returns original string for invalid dates', () => {
      expect(formatDateSafe('not-a-date')).toBe('not-a-date');
      expect(formatDateSafe('2025-13-45')).toBe('2025-13-45'); // Invalid month/day
    });
  });

  describe('formatDateTimeSafe', () => {
    it('formats ISO datetime with timezone', () => {
      const result = formatDateTimeSafe('2025-12-06T10:30:00Z');
      // PPpp format uses abbreviated month (Dec) not full (December)
      expect(result).toContain('Dec');
      expect(result).toContain('6');
      expect(result).toContain('2025');
    });

    it('formats datetime without timezone (treated as local)', () => {
      const result = formatDateTimeSafe('2025-12-06T14:00:00');
      expect(result).toContain('Dec');
      expect(result).toContain('6');
    });

    it('handles null and undefined', () => {
      expect(formatDateTimeSafe(null)).toBe('No date');
      expect(formatDateTimeSafe(undefined)).toBe('No date');
    });
  });

  describe('parseDateSafe', () => {
    it('parses date-only string to correct date', () => {
      const date = parseDateSafe('2025-12-06');
      expect(date).not.toBeNull();
      expect(date!.getDate()).toBe(6); // Must be 6th, not 5th!
      expect(date!.getMonth()).toBe(11); // December (0-indexed)
      expect(date!.getFullYear()).toBe(2025);
    });

    it('parses ISO datetime string', () => {
      const date = parseDateSafe('2025-12-06T10:30:00Z');
      expect(date).not.toBeNull();
      expect(date!.getFullYear()).toBe(2025);
    });

    it('returns null for null/undefined/empty', () => {
      expect(parseDateSafe(null)).toBeNull();
      expect(parseDateSafe(undefined)).toBeNull();
      expect(parseDateSafe('')).toBeNull();
    });

    it('returns null for invalid date strings', () => {
      expect(parseDateSafe('not-a-date')).toBeNull();
    });
  });

  describe('timezone shift regression tests', () => {
    /**
     * These tests specifically guard against the bug where:
     * new Date("YYYY-MM-DD") is parsed as UTC midnight, causing
     * the date to shift backwards in negative UTC offset timezones.
     */

    it('December 6th stays December 6th (not 5th)', () => {
      const date = parseDateSafe('2025-12-06');
      expect(date!.getDate()).toBe(6);
    });

    it('January 1st stays January 1st (not Dec 31st)', () => {
      const date = parseDateSafe('2025-01-01');
      expect(date!.getDate()).toBe(1);
      expect(date!.getMonth()).toBe(0); // January
    });

    it('formatted date contains correct day number', () => {
      // Test multiple dates to ensure no timezone shift
      const testCases = [
        { input: '2025-12-01', expectedDay: '1' },
        { input: '2025-12-15', expectedDay: '15' },
        { input: '2025-12-31', expectedDay: '31' },
      ];

      for (const { input, expectedDay } of testCases) {
        const result = formatDateSafe(input);
        expect(result).toContain(expectedDay);
      }
    });
  });
});