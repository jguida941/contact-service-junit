-- Add archived column to tasks table
ALTER TABLE tasks ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
