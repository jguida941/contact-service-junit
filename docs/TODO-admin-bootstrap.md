# TODO: Admin User Bootstrap Feature

**Created:** 2025-12-06
**Priority:** Medium
**Status:** Pending

## Problem

Currently there's no way to create an admin user except by manually running SQL against the database. The admin dashboard exists (`/admin`) but no one can access it without an ADMIN role.

## Proposed Solution

Implement environment variable-based admin bootstrap on application startup.

### How It Works

1. On startup, check for `ADMIN_EMAIL` and `ADMIN_PASSWORD` environment variables
2. If both are set AND no admin user exists, create one
3. If admin already exists, skip (idempotent)
4. Log that admin was created (without logging password)

### Implementation Steps

- [ ] Create `AdminBootstrapService` in `contactapp.security` package
- [ ] Add `@EventListener(ApplicationReadyEvent.class)` method
- [ ] Check for env vars: `ADMIN_EMAIL`, `ADMIN_PASSWORD`
- [ ] Validate password meets strength requirements
- [ ] Create user with `Role.ADMIN` if not exists
- [ ] Add configuration properties to `application.yml`
- [ ] Write unit tests for bootstrap logic
- [ ] Update README with admin setup instructions
- [ ] Create ADR documenting the decision

### Environment Variables

```bash
# Production deployment
export ADMIN_EMAIL=admin@example.com
export ADMIN_PASSWORD=SecurePassword123!

# Or in docker-compose.yml
environment:
  ADMIN_EMAIL: admin@example.com
  ADMIN_PASSWORD: ${ADMIN_PASSWORD}  # From .env file
```

### Security Considerations

- Password must meet minimum strength requirements
- Never log the password
- Admin creation only happens if NO admin exists (prevents hijacking)
- Consider adding `ADMIN_BOOTSTRAP_ENABLED=true` flag for extra safety

## References

- ADR-0036: Admin Dashboard Role-Based UI
- ADR-0052: Production Auth System
- `AdminDashboard.tsx` - The frontend that needs admin access
