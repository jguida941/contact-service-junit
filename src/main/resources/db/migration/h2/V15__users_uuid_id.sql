-- V15: Migrate users.id from BIGINT IDENTITY to UUID (data-preserving)
-- ADR-0052 Phase 0.5: UUID migration to prevent user enumeration attacks
--
-- H2 version: Uses RANDOM_UUID() and subquery syntax for UPDATE
-- This migration safely handles existing data in any environment (dev, staging, production).
-- Pattern: add-column → backfill → drop-FK → alter-FK-columns → backfill-FKs → swap-PK → recreate-FK/indexes
--
-- IMPORTANT: Run during maintenance window with application stopped.
-- Concurrent writes during Steps 4-8 may cause orphaned references or NOT NULL violations.

-- Step 1: Add new UUID column to users table
ALTER TABLE users ADD COLUMN new_id UUID DEFAULT RANDOM_UUID();

-- Step 2: Backfill UUIDs for existing rows
UPDATE users SET new_id = RANDOM_UUID() WHERE new_id IS NULL;

-- Step 3: Add new UUID columns to child tables
ALTER TABLE contacts ADD COLUMN new_user_id UUID;
ALTER TABLE tasks ADD COLUMN new_user_id UUID;
ALTER TABLE tasks ADD COLUMN new_assignee_id UUID;
ALTER TABLE appointments ADD COLUMN new_user_id UUID;
ALTER TABLE projects ADD COLUMN new_user_id UUID;

-- Step 4: Backfill FK columns via subquery (H2 syntax)
UPDATE contacts SET new_user_id = (SELECT new_id FROM users WHERE users.id = contacts.user_id);
UPDATE tasks SET new_user_id = (SELECT new_id FROM users WHERE users.id = tasks.user_id);
UPDATE tasks SET new_assignee_id = (SELECT new_id FROM users WHERE users.id = tasks.assignee_id);
UPDATE appointments SET new_user_id = (SELECT new_id FROM users WHERE users.id = appointments.user_id);
UPDATE projects SET new_user_id = (SELECT new_id FROM users WHERE users.id = projects.user_id);

-- Step 5: Drop existing foreign key constraints
ALTER TABLE contacts DROP CONSTRAINT IF EXISTS fk_contacts_user_id;
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS fk_tasks_user_id;
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS fk_appointments_user_id;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS fk_projects_user_id;

-- Step 5b: Drop unique constraints that include user_id (will recreate after column rename)
-- H2 requires explicit drop before dropping the column
ALTER TABLE contacts DROP CONSTRAINT IF EXISTS uq_contacts_contact_id_user_id;
ALTER TABLE tasks DROP CONSTRAINT IF EXISTS uq_tasks_task_id_user_id;
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS uq_appointments_appointment_id_user_id;
ALTER TABLE projects DROP CONSTRAINT IF EXISTS uq_projects_project_id_user_id;

-- Step 6: Drop existing indexes on user_id columns
DROP INDEX IF EXISTS idx_contacts_user_id;
DROP INDEX IF EXISTS idx_tasks_user_id;
DROP INDEX IF EXISTS idx_tasks_assignee_id;
DROP INDEX IF EXISTS idx_appointments_user_id;
DROP INDEX IF EXISTS idx_projects_user_id;
DROP INDEX IF EXISTS idx_users_username;
DROP INDEX IF EXISTS idx_users_email;

-- Step 7: Drop old columns and rename new columns
ALTER TABLE contacts DROP COLUMN user_id;
ALTER TABLE contacts ALTER COLUMN new_user_id RENAME TO user_id;
ALTER TABLE contacts ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE tasks DROP COLUMN user_id;
ALTER TABLE tasks ALTER COLUMN new_user_id RENAME TO user_id;
ALTER TABLE tasks ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE tasks DROP COLUMN assignee_id;
ALTER TABLE tasks ALTER COLUMN new_assignee_id RENAME TO assignee_id;
-- assignee_id remains nullable

ALTER TABLE appointments DROP COLUMN user_id;
ALTER TABLE appointments ALTER COLUMN new_user_id RENAME TO user_id;
ALTER TABLE appointments ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE projects DROP COLUMN user_id;
ALTER TABLE projects ALTER COLUMN new_user_id RENAME TO user_id;
ALTER TABLE projects ALTER COLUMN user_id SET NOT NULL;

-- Step 8: Drop old users.id PK and promote new_id
ALTER TABLE users DROP PRIMARY KEY;
ALTER TABLE users DROP COLUMN id;
ALTER TABLE users ALTER COLUMN new_id RENAME TO id;
ALTER TABLE users ALTER COLUMN id SET NOT NULL;
ALTER TABLE users ADD PRIMARY KEY (id);

-- Step 9: Recreate foreign key constraints
ALTER TABLE contacts ADD CONSTRAINT fk_contacts_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE appointments ADD CONSTRAINT fk_appointments_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE projects ADD CONSTRAINT fk_projects_user_id
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
-- assignee_id references users but should SET NULL when user is deleted (nullable column)
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_assignee_id
    FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE SET NULL;

-- Step 10: Recreate indexes
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_contacts_user_id ON contacts(user_id);
CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);
CREATE INDEX idx_appointments_user_id ON appointments(user_id);
CREATE INDEX idx_projects_user_id ON projects(user_id);

-- Step 11: Recreate unique constraints (per-user uniqueness from V6)
ALTER TABLE contacts ADD CONSTRAINT uq_contacts_contact_id_user_id UNIQUE (contact_id, user_id);
ALTER TABLE tasks ADD CONSTRAINT uq_tasks_task_id_user_id UNIQUE (task_id, user_id);
ALTER TABLE appointments ADD CONSTRAINT uq_appointments_appointment_id_user_id UNIQUE (appointment_id, user_id);
ALTER TABLE projects DROP CONSTRAINT IF EXISTS uq_projects_project_id_user_id;
ALTER TABLE projects ADD CONSTRAINT uq_projects_project_id_user_id UNIQUE (project_id, user_id);
