-- V14: Add archived column to appointments table
-- Allows users to archive past appointments instead of deleting them
-- See ADR-0050 for the reconstitution pattern design decision

ALTER TABLE appointments ADD COLUMN archived BOOLEAN DEFAULT FALSE NOT NULL;

-- Index for efficient filtering of active vs archived appointments
CREATE INDEX idx_appointments_archived ON appointments(archived);
