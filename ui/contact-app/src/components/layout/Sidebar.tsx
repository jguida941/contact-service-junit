import { NavLink } from 'react-router-dom';
import { LayoutDashboard, Users, CheckSquare, Calendar, FolderKanban, Settings, HelpCircle, Shield } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Separator } from '@/components/ui/separator';
import { ScrollArea } from '@/components/ui/scroll-area';
import { authApi } from '@/lib/api';

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Overview' },
  { to: '/contacts', icon: Users, label: 'Contacts' },
  { to: '/tasks', icon: CheckSquare, label: 'Tasks' },
  { to: '/appointments', icon: Calendar, label: 'Appointments' },
  { to: '/projects', icon: FolderKanban, label: 'Projects' },
];

const footerItems = [
  { to: '/settings', icon: Settings, label: 'Settings' },
  { to: '/help', icon: HelpCircle, label: 'Help' },
];

interface SidebarProps {
  collapsed?: boolean;
}

export function Sidebar({ collapsed = false }: SidebarProps) {
  // Check if current user is an admin (per ADR-0036)
  const currentUser = authApi.getCurrentUser();
  const isAdmin = currentUser?.role === 'ADMIN';
  return (
    <aside
      className={cn(
        'flex h-full flex-col border-r border-border bg-card transition-all duration-200',
        collapsed ? 'w-16' : 'w-56'
      )}
      aria-label="Main navigation"
    >
      {/* A11y: Application branding - not in navigation */}
      <div className="flex h-14 items-center border-b border-border px-4">
        <div className="flex items-center gap-2">
          <div
            className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground"
            role="img"
            aria-label="ContactApp logo"
          >
            <Users className="h-4 w-4" aria-hidden="true" />
          </div>
          {!collapsed && (
            <span className="font-semibold text-foreground">ContactApp</span>
          )}
        </div>
      </div>

      {/* A11y: Primary navigation with descriptive label */}
      <ScrollArea className="flex-1 px-2 py-4">
        <nav aria-label="Primary" className="flex flex-col gap-1">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  'hover:bg-accent hover:text-accent-foreground',
                  isActive
                    ? 'bg-accent text-accent-foreground'
                    : 'text-muted-foreground',
                  collapsed && 'justify-center px-2'
                )
              }
              aria-label={item.label}
            >
              <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          ))}
        </nav>
      </ScrollArea>

      {/* A11y: Secondary navigation for settings and help */}
      <div className="px-2 pb-4">
        <Separator className="mb-4" aria-hidden="true" />
        <nav aria-label="Secondary" className="flex flex-col gap-1">
          {/* Admin link - only visible to admin users (ADR-0036) */}
          {isAdmin && (
            <NavLink
              to="/admin"
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  'hover:bg-accent hover:text-accent-foreground',
                  isActive
                    ? 'bg-accent text-accent-foreground'
                    : 'text-muted-foreground',
                  collapsed && 'justify-center px-2'
                )
              }
              aria-label="Admin Dashboard"
            >
              <Shield className="h-4 w-4 shrink-0" aria-hidden="true" />
              {!collapsed && <span>Admin</span>}
            </NavLink>
          )}
          {footerItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                  'hover:bg-accent hover:text-accent-foreground',
                  isActive
                    ? 'bg-accent text-accent-foreground'
                    : 'text-muted-foreground',
                  collapsed && 'justify-center px-2'
                )
              }
              aria-label={item.label}
            >
              <item.icon className="h-4 w-4 shrink-0" aria-hidden="true" />
              {!collapsed && <span>{item.label}</span>}
            </NavLink>
          ))}
        </nav>
      </div>
    </aside>
  );
}
