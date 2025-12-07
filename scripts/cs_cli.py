#!/usr/bin/env python3
"""
Contact Suite CLI - Unified developer experience.

One CLI (`cs`) that provides a small set of predictable commands for local dev,
local prod simulation, tests, and DB management.

Usage:
    ./scripts/run dev                    # Start development environment
    ./scripts/run test                   # Run all quality checks
    ./scripts/run db status              # Check database status
    ./scripts/run health                 # Check service health
    ./scripts/run qa-dashboard           # View quality metrics
    ./scripts/run prod-local             # Simulate production locally
    ./scripts/run ci-local               # Reproduce CI pipeline locally

See: docs/design-notes/notes/clitool.md for the full design document.
"""

from __future__ import annotations

import json
import os
import shutil
import signal
import subprocess
import sys
import time
import webbrowser
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.error import URLError
from urllib.request import urlopen

try:
    import typer
    from rich.console import Console
    from rich.table import Table
    from rich.panel import Panel
    from rich.progress import Progress, SpinnerColumn, TextColumn
except ImportError:
    print("Required dependencies not installed. Run:")
    print("  pip install -r scripts/requirements.txt")
    sys.exit(2)

# Import from our modules
from scripts.runtime_env import (
    DevEnvironment,
    ProdLocalEnvironment,
    CILocalEnvironment,
    get_project_root,
)
from scripts.dev_stack import (
    _wait_for_backend,
    _start_process,
    _ensure_postgres,
    _resolve_compose_command,
    _maybe_install_frontend,
    _attach_signal_handlers,
)

# Constants
ROOT = get_project_root()
FRONTEND_DIR = ROOT / "ui" / "contact-app"
COMPOSE_FILE = ROOT / "docker-compose.dev.yml"
HEALTH_URL = "http://localhost:8080/actuator/health"
FRONTEND_URL = "http://localhost:5173"

# CLI App
HELP_TEXT = """Contact Suite CLI - Unified developer experience

[bold yellow]Run commands with:[/bold yellow] ./scripts/run <command>

[bold cyan]Quick Examples:[/bold cyan]
  ./scripts/run dev            Start dev environment
  ./scripts/run test           Run all tests
  ./scripts/run health         Check services
  ./scripts/run db status      Database info
"""

app = typer.Typer(
    name="./scripts/run",
    help=HELP_TEXT,
    no_args_is_help=True,
    rich_markup_mode="rich",
)
console = Console()

# Sub-apps for grouped commands
db_app = typer.Typer(help="Database management commands")
app.add_typer(db_app, name="db")


# ==============================================================================
# Helper Functions
# ==============================================================================

def _check_health(url: str, timeout: int = 5) -> Tuple[bool, int, str]:
    """Check health of a service. Returns (is_up, latency_ms, status)."""
    start = time.time()
    try:
        with urlopen(url, timeout=timeout) as response:
            latency_ms = int((time.time() - start) * 1000)
            if response.status == 200:
                try:
                    payload = json.loads(response.read().decode("utf-8"))
                    status = payload.get("status", "UP")
                except json.JSONDecodeError:
                    status = "UP"
                return True, latency_ms, status
            return False, latency_ms, f"HTTP {response.status}"
    except URLError as e:
        latency_ms = int((time.time() - start) * 1000)
        return False, latency_ms, str(e.reason)
    except Exception as e:
        latency_ms = int((time.time() - start) * 1000)
        return False, latency_ms, str(e)


def _check_postgres() -> Tuple[bool, int, str]:
    """Check if Postgres container is running."""
    start = time.time()
    try:
        result = subprocess.run(
            ["docker", "ps", "--filter", "name=contactapp-postgres", "--format", "{{.Status}}"],
            capture_output=True,
            text=True,
            timeout=5,
        )
        latency_ms = int((time.time() - start) * 1000)
        if result.returncode == 0 and "Up" in result.stdout:
            return True, latency_ms, "UP"
        return False, latency_ms, "Not running"
    except FileNotFoundError:
        return False, 0, "Docker not found"
    except Exception as e:
        return False, 0, str(e)


def _check_frontend() -> Tuple[bool, int, str]:
    """Check if Vite dev server is running."""
    start = time.time()
    try:
        with urlopen(FRONTEND_URL, timeout=5) as response:
            latency_ms = int((time.time() - start) * 1000)
            if response.status == 200:
                return True, latency_ms, "UP"
            return False, latency_ms, f"HTTP {response.status}"
    except URLError:
        return False, int((time.time() - start) * 1000), "Not running"
    except Exception as e:
        return False, 0, str(e)


def _confirm(message: str, default: bool = False) -> bool:
    """Prompt for confirmation."""
    suffix = " [y/N]: " if not default else " [Y/n]: "
    response = input(message + suffix).strip().lower()
    if not response:
        return default
    return response in ("y", "yes")


def _run_maven(goals: List[str], env: Optional[Dict[str, str]] = None) -> int:
    """Run Maven with given goals."""
    cmd = ["mvn"] + goals
    console.print(f"[dim]Running: {' '.join(cmd)}[/dim]")
    # Merge with current environment to preserve PATH, JAVA_HOME, etc.
    merged_env = {**os.environ, **(env or {})}
    result = subprocess.run(cmd, cwd=str(ROOT), env=merged_env)
    return result.returncode


def _run_npm(args: List[str], cwd: Path = FRONTEND_DIR) -> int:
    """Run npm with given args."""
    cmd = ["npm"] + args
    console.print(f"[dim]Running: {' '.join(cmd)}[/dim]")
    result = subprocess.run(cmd, cwd=str(cwd))
    return result.returncode


# ==============================================================================
# cs dev - Start development environment
# ==============================================================================

@app.command()
def dev(
    db: str = typer.Option("h2", "--db", help="Database backend: h2 or postgres"),
    backend_only: bool = typer.Option(False, "--backend-only", help="Skip frontend"),
    frontend_only: bool = typer.Option(False, "--frontend-only", help="Skip backend (assumes backend running)"),
    skip_install: bool = typer.Option(False, "--skip-install", help="Skip npm install"),
    timeout: int = typer.Option(120, "--timeout", help="Backend startup timeout in seconds"),
):
    """
    Start everything for local development.

    [bold]Examples:[/bold]
        ./scripts/run dev                    # H2 in-memory database (fastest)
        ./scripts/run dev --db postgres      # Postgres via Docker
        ./scripts/run dev --backend-only     # Skip frontend
        ./scripts/run dev --frontend-only    # Skip backend (assumes already running)
    """
    if backend_only and frontend_only:
        console.print("[red]Cannot use --backend-only and --frontend-only together.[/red]")
        raise typer.Exit(1)

    console.print(Panel.fit(
        "[bold green]Contact Suite Development Environment[/bold green]",
        subtitle=f"Database: {db.upper()}"
    ))

    # Build environment
    env = DevEnvironment(database=db).build()
    running: List[Tuple[str, subprocess.Popen]] = []
    _attach_signal_handlers(running)

    try:
        # Start Postgres if needed
        if db.lower() == "postgres" and not frontend_only:
            with Progress(
                SpinnerColumn(),
                TextColumn("[progress.description]{task.description}"),
                console=console,
            ) as progress:
                progress.add_task("Starting Postgres container...", total=None)
                _ensure_postgres(COMPOSE_FILE)
            console.print("[green]Postgres container is running[/green]")

        # Start backend
        if not frontend_only:
            console.print("\n[bold]Starting Spring Boot backend...[/bold]")
            backend_cmd = ["mvn", "spring-boot:run"]
            backend = _start_process(backend_cmd, cwd=ROOT, env=env)
            running.append(("backend", backend))

            with Progress(
                SpinnerColumn(),
                TextColumn("[progress.description]{task.description}"),
                console=console,
            ) as progress:
                task = progress.add_task(f"Waiting for backend (timeout: {timeout}s)...", total=None)
                try:
                    _wait_for_backend(HEALTH_URL, timeout)
                except RuntimeError as e:
                    console.print(f"[red]Error: {e}[/red]")
                    for name, proc in running:
                        proc.terminate()
                    raise typer.Exit(1)

            console.print("[green]Backend is UP[/green]")

        # Start frontend
        if not backend_only:
            console.print("\n[bold]Starting Vite frontend...[/bold]")
            _maybe_install_frontend(skip_install)
            frontend_cmd = ["npm", "run", "dev", "--", "--port", "5173"]
            frontend = _start_process(frontend_cmd, cwd=FRONTEND_DIR)
            running.append(("frontend", frontend))
            console.print("[green]Frontend starting...[/green]")

        # Print URLs
        console.print("\n")
        table = Table(title="Services Running", show_header=True, header_style="bold cyan")
        table.add_column("Service", style="dim")
        table.add_column("URL")
        if not frontend_only:
            table.add_row("API", "http://localhost:8080")
            table.add_row("Swagger", "http://localhost:8080/swagger-ui.html")
        if not backend_only:
            table.add_row("UI", "http://localhost:5173")
        console.print(table)
        console.print("\n[dim]Press Ctrl+C to stop all services[/dim]\n")

        # Keep running until interrupted
        while True:
            for name, proc in running:
                if proc.poll() is not None:
                    console.print(f"[red]{name} exited with code {proc.returncode}[/red]")
                    raise typer.Exit(1)
            time.sleep(1)

    except KeyboardInterrupt:
        console.print("\n[yellow]Shutting down...[/yellow]")
    finally:
        for name, proc in running:
            if proc.poll() is None:
                proc.terminate()


# ==============================================================================
# cs prod-local - Simulate production locally
# ==============================================================================

@app.command("prod-local")
def prod_local(
    skip_build: bool = typer.Option(False, "--skip-build", help="Use existing JAR in target/"),
    https: bool = typer.Option(False, "--https", help="Enable HTTPS (requires self-signed cert)"),
):
    """
    Simulate production environment locally.

    [bold]Requirements:[/bold]
        - JWT_SECRET must be set (not the dev default)
        - Postgres container will be started

    [bold]Examples:[/bold]
        export JWT_SECRET=$(openssl rand -base64 32)
        ./scripts/run prod-local
        ./scripts/run prod-local --skip-build    # Use existing JAR
    """
    console.print(Panel.fit(
        "[bold yellow]Production Simulation Mode[/bold yellow]",
        subtitle="Secure cookies, strict CSP"
    ))

    # Validate environment
    try:
        env_builder = ProdLocalEnvironment()
        if https:
            env_builder.with_https()
        env = env_builder.build()
    except ValueError as e:
        console.print(f"[red]Configuration Error:[/red]\n{e}")
        raise typer.Exit(1)

    # Build JAR if needed
    if not skip_build:
        console.print("\n[bold]Building production JAR...[/bold]")
        result = _run_maven(["clean", "package", "-DskipTests"])
        if result != 0:
            console.print("[red]Build failed[/red]")
            raise typer.Exit(1)

    # Find JAR
    jar_files = list((ROOT / "target").glob("*.jar"))
    jar_files = [j for j in jar_files if not j.name.endswith("-sources.jar")]
    if not jar_files:
        console.print("[red]No JAR file found in target/. Run without --skip-build.[/red]")
        raise typer.Exit(1)
    jar_path = jar_files[0]

    # Start Postgres
    console.print("\n[bold]Starting Postgres...[/bold]")
    _ensure_postgres(COMPOSE_FILE)

    # Run JAR
    console.print(f"\n[bold]Running {jar_path.name}...[/bold]")
    console.print("[dim]Environment: Secure cookies, strict CSP, prod profile[/dim]")

    try:
        result = subprocess.run(
            ["java", "-jar", str(jar_path)],
            cwd=str(ROOT),
            env=env,
        )
        raise typer.Exit(result.returncode)
    except KeyboardInterrupt:
        console.print("\n[yellow]Stopped[/yellow]")


# ==============================================================================
# cs test - Run all quality checks
# ==============================================================================

@app.command()
def test(
    unit: bool = typer.Option(False, "--unit", help="JUnit unit tests only"),
    integration: bool = typer.Option(False, "--integration", help="Testcontainers integration tests"),
    frontend: bool = typer.Option(False, "--frontend", help="Vitest + Playwright"),
    mutation: bool = typer.Option(False, "--mutation", help="PITest mutation testing"),
    security: bool = typer.Option(False, "--security", help="OWASP + API fuzzing"),
    fast: bool = typer.Option(False, "--fast", help="Skip slow tests (mutation, fuzzing)"),
):
    """
    Run all quality checks.

    [bold]Examples:[/bold]
        ./scripts/run test                   # Run everything
        ./scripts/run test --unit            # JUnit unit tests only
        ./scripts/run test --fast            # Skip slow tests
        ./scripts/run test --mutation        # Mutation testing only
    """
    console.print(Panel.fit("[bold blue]Running Quality Checks[/bold blue]"))

    # Determine what to run
    run_all = not any([unit, integration, frontend, mutation, security])
    results: Dict[str, Tuple[str, int]] = {}

    # Unit tests
    if run_all or unit:
        console.print("\n[bold]Running JUnit unit tests...[/bold]")
        rc = _run_maven(["test"])
        results["Unit Tests"] = ("PASS" if rc == 0 else "FAIL", rc)

    # Integration tests
    if run_all or integration:
        console.print("\n[bold]Running integration tests...[/bold]")
        rc = _run_maven(["verify", "-DskipITs=false"])
        results["Integration Tests"] = ("PASS" if rc == 0 else "FAIL", rc)

    # Frontend tests
    if run_all or frontend:
        console.print("\n[bold]Running frontend tests...[/bold]")
        rc = _run_npm(["run", "test:run"], cwd=FRONTEND_DIR)
        results["Frontend Tests"] = ("PASS" if rc == 0 else "FAIL", rc)

    # Mutation testing (slow)
    if (run_all and not fast) or mutation:
        console.print("\n[bold]Running mutation testing...[/bold]")
        rc = _run_maven(["pitest:mutationCoverage"])
        results["Mutation Testing"] = ("PASS" if rc == 0 else "FAIL", rc)

    # Security checks (slow)
    if (run_all and not fast) or security:
        console.print("\n[bold]Running security checks...[/bold]")
        # OWASP dependency check
        rc = _run_maven(["dependency-check:check"])
        results["OWASP Dep-Check"] = ("PASS" if rc == 0 else "FAIL", rc)

        # API fuzzing
        console.print("\n[bold]Running API fuzzing...[/bold]")
        fuzzing_result = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "api_fuzzing.py"), "--start-app"],
            cwd=str(ROOT),
        )
        results["API Fuzzing"] = ("PASS" if fuzzing_result.returncode == 0 else "FAIL", fuzzing_result.returncode)

    # Summary
    console.print("\n")
    table = Table(title="Test Results Summary", show_header=True, header_style="bold")
    table.add_column("Check", style="dim")
    table.add_column("Status")
    table.add_column("Exit Code", justify="right")

    all_passed = True
    for name, (status, code) in results.items():
        style = "green" if status == "PASS" else "red"
        table.add_row(name, f"[{style}]{status}[/{style}]", str(code))
        if code != 0:
            all_passed = False

    console.print(table)

    # Report locations
    console.print("\n[bold]Reports:[/bold]")
    console.print("  JaCoCo:  target/site/jacoco/index.html")
    console.print("  PITest:  target/pit-reports/index.html")
    console.print("  OWASP:   target/dependency-check-report.html")

    raise typer.Exit(0 if all_passed else 1)


# ==============================================================================
# cs qa-dashboard - View quality metrics
# ==============================================================================

@app.command("qa-dashboard")
def qa_dashboard():
    """
    View quality metrics in browser.

    Builds reports if needed, then opens the QA dashboard.
    """
    dashboard_path = ROOT / "target" / "site" / "qa-dashboard" / "index.html"

    # Check if reports exist
    if not dashboard_path.exists():
        console.print("[yellow]QA dashboard not found. Building reports...[/yellow]")
        rc = _run_maven(["verify", "site", "-DskipITs"])
        if rc != 0:
            console.print("[red]Failed to build reports[/red]")
            raise typer.Exit(1)

        # Run metrics summary to generate dashboard
        subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "ci_metrics_summary.py")],
            cwd=str(ROOT),
        )

    # Serve dashboard
    console.print("\n[bold]Starting QA Dashboard server...[/bold]")
    serve_script = ROOT / "scripts" / "serve_quality_dashboard.py"

    # Note: serve_quality_dashboard.py handles browser opening internally
    subprocess.run([sys.executable, str(serve_script)], cwd=str(ROOT))


# ==============================================================================
# cs ci-local - Reproduce CI pipeline locally
# ==============================================================================

@app.command("ci-local")
def ci_local(
    fast: bool = typer.Option(False, "--fast", help="Skip slow steps (mutation, OWASP)"),
):
    """
    Reproduce CI pipeline locally.

    Runs the same steps as GitHub Actions (java-ci.yml).
    """
    console.print(Panel.fit(
        "[bold cyan]Reproducing CI Pipeline Locally[/bold cyan]",
        subtitle="Mirrors .github/workflows/java-ci.yml"
    ))

    # Build environment
    try:
        env = CILocalEnvironment().build()
    except ValueError as e:
        console.print(f"[red]{e}[/red]")
        raise typer.Exit(1)

    results: Dict[str, int] = {}

    # Full verify
    console.print("\n[bold]Step 1: mvn clean verify[/bold]")
    goals = ["clean", "verify"]
    if fast:
        goals.extend(["-DskipPitest=true", "-Ddependency-check.skip=true"])
    rc = _run_maven(goals, env=env)
    results["Build & Verify"] = rc

    if rc != 0:
        console.print("[red]Build failed, stopping[/red]")
        raise typer.Exit(1)

    # Generate metrics
    console.print("\n[bold]Step 2: Generate QA Dashboard[/bold]")
    subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "ci_metrics_summary.py")],
        cwd=str(ROOT),
        env=env,
    )

    # API fuzzing (unless fast)
    if not fast:
        console.print("\n[bold]Step 3: API Fuzzing[/bold]")
        fuzzing_result = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "api_fuzzing.py"), "--start-app"],
            cwd=str(ROOT),
            env=env,
        )
        results["API Fuzzing"] = fuzzing_result.returncode

    # Summary
    console.print("\n")
    table = Table(title="CI Pipeline Results", show_header=True)
    table.add_column("Step")
    table.add_column("Status")
    for name, code in results.items():
        status = "[green]PASS[/green]" if code == 0 else "[red]FAIL[/red]"
        table.add_row(name, status)
    console.print(table)

    all_passed = all(rc == 0 for rc in results.values())
    raise typer.Exit(0 if all_passed else 1)


# ==============================================================================
# cs health - Check service health
# ==============================================================================

@app.command()
def health(
    watch: bool = typer.Option(False, "--watch", help="Continuous monitoring (refresh every 5s)"),
):
    """
    Quick health check of all services.

    [bold]Examples:[/bold]
        ./scripts/run health              # Check all services once
        ./scripts/run health --watch      # Continuous monitoring
    """
    def print_health_table():
        table = Table(title="Contact Suite Health Check", show_header=True, header_style="bold cyan")
        table.add_column("Service")
        table.add_column("Status")
        table.add_column("Latency", justify="right")
        table.add_column("URL")

        # Backend API
        is_up, latency, status = _check_health(HEALTH_URL)
        status_display = "[green]UP[/green]" if is_up else f"[red]{status}[/red]"
        table.add_row("Backend API", status_display, f"{latency}ms", ":8080")

        # Frontend
        is_up, latency, status = _check_frontend()
        status_display = "[green]UP[/green]" if is_up else f"[yellow]{status}[/yellow]"
        table.add_row("Frontend", status_display, f"{latency}ms", ":5173")

        # PostgreSQL
        is_up, latency, status = _check_postgres()
        status_display = "[green]UP[/green]" if is_up else f"[yellow]{status}[/yellow]"
        table.add_row("PostgreSQL", status_display, f"{latency}ms", ":5432")

        # Actuator
        is_up, latency, status = _check_health("http://localhost:8080/actuator/info")
        status_display = "[green]UP[/green]" if is_up else f"[red]{status}[/red]"
        table.add_row("Actuator", status_display, f"{latency}ms", ":8080/actuator")

        console.print(table)

    if watch:
        try:
            while True:
                console.clear()
                print_health_table()
                console.print("\n[dim]Refreshing every 5s. Press Ctrl+C to stop.[/dim]")
                time.sleep(5)
        except KeyboardInterrupt:
            pass
    else:
        print_health_table()


# ==============================================================================
# cs db - Database management commands
# ==============================================================================

@db_app.command("start")
def db_start():
    """Start Postgres container."""
    console.print("[bold]Starting Postgres container...[/bold]")
    try:
        _ensure_postgres(COMPOSE_FILE)
        console.print("[green]Postgres is running[/green]")
    except Exception as e:
        console.print(f"[red]Failed to start Postgres: {e}[/red]")
        raise typer.Exit(1)


@db_app.command("stop")
def db_stop(
    force: bool = typer.Option(False, "--force", "-f", help="Skip confirmation"),
):
    """Stop Postgres container (data persists)."""
    if not force:
        if not _confirm("Stop Postgres container? Data will persist."):
            console.print("[yellow]Cancelled[/yellow]")
            raise typer.Exit(0)

    console.print("[bold]Stopping Postgres container...[/bold]")
    compose_cmd = _resolve_compose_command()
    result = subprocess.run(
        compose_cmd + ["-f", str(COMPOSE_FILE), "stop"],
        cwd=str(ROOT),
    )
    if result.returncode == 0:
        console.print("[green]Postgres stopped[/green]")
    else:
        console.print("[red]Failed to stop Postgres[/red]")
        raise typer.Exit(1)


@db_app.command("status")
def db_status():
    """Show container status and connection info."""
    is_up, latency, status = _check_postgres()

    table = Table(title="PostgreSQL Status", show_header=True)
    table.add_column("Property")
    table.add_column("Value")

    table.add_row("Container", "[green]Running[/green]" if is_up else "[red]Stopped[/red]")
    table.add_row("Host", "localhost")
    table.add_row("Port", "5432")
    table.add_row("Database", "contactapp")
    table.add_row("Username", "contactapp")
    table.add_row("JDBC URL", "jdbc:postgresql://localhost:5432/contactapp")

    console.print(table)


@db_app.command("reset")
def db_reset(
    force: bool = typer.Option(False, "--force", "-f", help="Skip confirmation"),
):
    """Drop and recreate database (DESTRUCTIVE)."""
    if not force:
        console.print("[bold red]WARNING: This will DELETE ALL DATA![/bold red]")
        if not _confirm("Are you sure you want to reset the database?"):
            console.print("[yellow]Cancelled[/yellow]")
            raise typer.Exit(0)

    console.print("[bold]Resetting database...[/bold]")
    compose_cmd = _resolve_compose_command()

    # Stop and remove container
    result = subprocess.run(
        compose_cmd + ["-f", str(COMPOSE_FILE), "down", "-v"],
        cwd=str(ROOT),
    )
    if result.returncode != 0:
        console.print("[red]Failed to reset database (docker compose down -v failed)[/red]")
        raise typer.Exit(1)

    # Restart
    _ensure_postgres(COMPOSE_FILE)
    console.print("[green]Database reset complete[/green]")


@db_app.command("logs")
def db_logs(
    follow: bool = typer.Option(False, "--follow", "-f", help="Follow log output"),
):
    """Tail Postgres logs."""
    compose_cmd = _resolve_compose_command()
    cmd = compose_cmd + ["-f", str(COMPOSE_FILE), "logs"]
    if follow:
        cmd.append("-f")
    cmd.append("postgres")
    subprocess.run(cmd, cwd=str(ROOT))


@db_app.command("migrate")
def db_migrate():
    """Run Flyway migrations manually."""
    console.print("[bold]Running Flyway migrations...[/bold]")
    rc = _run_maven(["flyway:migrate"])
    if rc == 0:
        console.print("[green]Migrations complete[/green]")
    else:
        console.print("[red]Migration failed[/red]")
        raise typer.Exit(1)


# ==============================================================================
# cs setup-ssl - Generate self-signed SSL certificate
# ==============================================================================

@app.command("setup-ssl")
def setup_ssl(
    force: bool = typer.Option(False, "--force", "-f", help="Overwrite existing keystore"),
    cn: str = typer.Option("localhost", "--cn", help="Common Name for certificate"),
):
    """
    Generate self-signed SSL certificate for local HTTPS development.

    Creates:
      - src/main/resources/local-ssl.p12 (PKCS12 keystore)
      - certs/local-cert.crt (exportable certificate)

    [bold]Usage after setup:[/bold]
        export SSL_ENABLED=true
        ./scripts/run dev

    [bold]Trust the certificate:[/bold]
        macOS: security add-trusted-cert -p ssl -k ~/Library/Keychains/login.keychain certs/local-cert.crt
        Linux: sudo cp certs/local-cert.crt /usr/local/share/ca-certificates/ && sudo update-ca-certificates
    """
    keystore_path = ROOT / "src" / "main" / "resources" / "local-ssl.p12"
    certs_dir = ROOT / "certs"
    cert_path = certs_dir / "local-cert.crt"

    # Check if keystore exists
    if keystore_path.exists() and not force:
        console.print(f"[yellow]Keystore already exists at {keystore_path}[/yellow]")
        console.print("Use --force to overwrite")
        raise typer.Exit(1)

    # Check for keytool
    if not shutil.which("keytool"):
        console.print("[red]keytool not found. Install Java JDK.[/red]")
        raise typer.Exit(1)

    console.print(Panel.fit("[bold cyan]SSL Certificate Setup[/bold cyan]"))

    # Create certs directory
    certs_dir.mkdir(exist_ok=True)

    # Generate keystore with self-signed certificate
    console.print("\n[bold]Step 1: Generating PKCS12 keystore...[/bold]")
    keystore_password = "changeit"  # Standard dev password

    keytool_gen_cmd = [
        "keytool", "-genkeypair",
        "-alias", "local-ssl",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "365",
        "-keystore", str(keystore_path),
        "-storetype", "PKCS12",
        "-storepass", keystore_password,
        "-keypass", keystore_password,
        "-dname", f"CN={cn}, OU=Development, O=ContactApp, L=Local, ST=Dev, C=US",
        "-ext", f"SAN=dns:{cn},ip:127.0.0.1",
    ]

    result = subprocess.run(keytool_gen_cmd, capture_output=True, text=True)
    if result.returncode != 0:
        console.print(f"[red]Failed to generate keystore:[/red]\n{result.stderr}")
        raise typer.Exit(1)
    console.print(f"[green]Created: {keystore_path}[/green]")

    # Export certificate
    console.print("\n[bold]Step 2: Exporting certificate...[/bold]")
    keytool_export_cmd = [
        "keytool", "-exportcert",
        "-alias", "local-ssl",
        "-keystore", str(keystore_path),
        "-storepass", keystore_password,
        "-rfc",
        "-file", str(cert_path),
    ]

    result = subprocess.run(keytool_export_cmd, capture_output=True, text=True)
    if result.returncode != 0:
        console.print(f"[red]Failed to export certificate:[/red]\n{result.stderr}")
        raise typer.Exit(1)
    console.print(f"[green]Created: {cert_path}[/green]")

    # Print usage instructions
    console.print("\n")
    table = Table(title="SSL Setup Complete", show_header=True, header_style="bold green")
    table.add_column("Item")
    table.add_column("Value")
    table.add_row("Keystore", str(keystore_path.relative_to(ROOT)))
    table.add_row("Certificate", str(cert_path.relative_to(ROOT)))
    table.add_row("Password", keystore_password)
    table.add_row("Alias", "local-ssl")
    table.add_row("Validity", "365 days")
    console.print(table)

    console.print("\n[bold]To enable HTTPS:[/bold]")
    console.print("  export SSL_ENABLED=true")
    console.print("  ./scripts/run dev")
    console.print("\n[bold]To trust the certificate (macOS):[/bold]")
    console.print(f"  security add-trusted-cert -p ssl -k ~/Library/Keychains/login.keychain {cert_path.relative_to(ROOT)}")
    console.print("\n[bold]To trust the certificate (Linux):[/bold]")
    console.print(f"  sudo cp {cert_path.relative_to(ROOT)} /usr/local/share/ca-certificates/")
    console.print("  sudo update-ca-certificates")

    console.print("\n[dim]Note: SSL is disabled by default (SSL_ENABLED=false)[/dim]")


# ==============================================================================
# cs dashboard - Open DevOps dashboard
# ==============================================================================

@app.command()
def dashboard():
    """
    Open the web-based DevOps dashboard.

    Opens the admin dashboard at /admin/devops.
    Requires ADMIN user login.
    """
    # Check if backend is running
    is_up, _, _ = _check_health(HEALTH_URL)
    if not is_up:
        console.print("[yellow]Backend not running. Start with: ./scripts/run dev[/yellow]")
        raise typer.Exit(1)

    url = "http://localhost:8080/admin"
    console.print(f"[bold]Opening dashboard at {url}[/bold]")
    console.print("[dim]Note: Requires ADMIN user login[/dim]")
    webbrowser.open(url)


# ==============================================================================
# Main entry point
# ==============================================================================

def main():
    """Main entry point for the CLI."""
    app()


if __name__ == "__main__":
    main()
