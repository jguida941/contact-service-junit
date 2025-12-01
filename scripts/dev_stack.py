#!/usr/bin/env python3
"""
Helper for running the Spring Boot API and the Vite UI together during local
development. The script starts the backend first, polls the health endpoint,
then launches the React dev server so both stacks are ready in a single command.
"""

from __future__ import annotations

import argparse
import json
import os
import shlex
import signal
import subprocess
import sys
import time
from pathlib import Path
from typing import Dict, List, Sequence, Tuple
from urllib.error import URLError
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]
FRONTEND_DIR = ROOT / "ui" / "contact-app"


def _run(cmd: Sequence[str], *, cwd: Path) -> None:
    """Run a one-off command (npm install) and stream output live."""
    subprocess.run(cmd, cwd=str(cwd), check=True)


def _wait_for_backend(url: str, timeout: int) -> None:
    """
    Poll the health endpoint until Spring reports UP or the timeout elapses.
    Raises RuntimeError so the launcher can tear everything down on failure.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urlopen(url) as response:  # nosec - local dev only
                if response.status == 200:
                    payload = json.loads(response.read().decode("utf-8"))
                    if payload.get("status") == "UP":
                        return
        except URLError:
            pass
        except json.JSONDecodeError:
            pass
        time.sleep(1)
    raise RuntimeError(f"Backend did not become healthy within {timeout} seconds at {url}")


def _maybe_install_frontend(skip_install: bool) -> None:
    """Install npm dependencies the first time the UI runs (no-op if node_modules exists)."""
    node_modules = FRONTEND_DIR / "node_modules"
    if skip_install or node_modules.exists():
        return
    print("[dev-stack] Installing frontend dependencies (npm install)...", flush=True)
    _run(["npm", "install"], cwd=FRONTEND_DIR)


def _start_process(cmd: Sequence[str], *, cwd: Path, env: Dict[str, str] | None = None) -> subprocess.Popen:
    """Spawn a long-running process (backend or frontend) and immediately return the handle."""
    return subprocess.Popen(cmd, cwd=str(cwd), env=env)


def _attach_signal_handlers(children: List[Tuple[str, subprocess.Popen]]) -> None:
    """Ensure Ctrl+C or SIGTERM stops both processes cleanly."""

    def _shutdown(signum, frame):  # noqa: ARG001 - required for signal handler signature
        print(f"\n[dev-stack] Received signal {signum}, stopping services...", flush=True)
        for name, proc in children:
            if proc.poll() is None:
                proc.terminate()
        # Give processes a moment to exit gracefully before forcing.
        time.sleep(2)
        for name, proc in children:
            if proc.poll() is None:
                print(f"[dev-stack] Force killing {name}", flush=True)
                proc.kill()
        sys.exit(0)

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            signal.signal(sig, _shutdown)
        except ValueError:
            # Windows may not allow registering SIGTERM, so just skip.
            continue


def parse_args() -> argparse.Namespace:
    """CLI args keep the command flexible without complicating the workflow."""
    parser = argparse.ArgumentParser(description="Run Spring Boot API + Vite UI together.")
    parser.add_argument(
        "--backend-url",
        default="http://localhost:8080/actuator/health",
        help="Actuator health URL to poll before starting the UI.",
    )
    parser.add_argument(
        "--backend-timeout",
        type=int,
        default=120,
        help="Max seconds to wait for the backend health endpoint.",
    )
    parser.add_argument(
        "--frontend-port",
        type=int,
        default=5173,
        help="Port passed to `npm run dev -- --port <value>`.",
    )
    parser.add_argument(
        "--skip-frontend-install",
        action="store_true",
        help="Skip `npm install` even if node_modules is missing.",
    )
    parser.add_argument(
        "--backend-goal",
        default="spring-boot:run",
        help="Arguments passed to Maven after `mvn` (default: spring-boot:run).",
    )
    parser.add_argument(
        "--database",
        choices=("h2", "postgres"),
        default="h2",
        help="Database backing the backend service (default: h2).",
    )
    parser.add_argument(
        "--docker-compose-file",
        default="docker-compose.dev.yml",
        help="Docker compose file used when starting the Postgres dev database.",
    )
    parser.add_argument(
        "--postgres-url",
        default="jdbc:postgresql://localhost:5432/contactapp",
        help="JDBC URL applied to the backend when using the Postgres database.",
    )
    parser.add_argument(
        "--postgres-username",
        default="contactapp",
        help="Username for the Postgres dev database.",
    )
    parser.add_argument(
        "--postgres-password",
        default="contactapp",
        help="Password for the Postgres dev database.",
    )
    parser.add_argument(
        "--postgres-profile",
        default="dev",
        help="Spring profile activated when using the Postgres dev database.",
    )
    return parser.parse_args()


def _ensure_postgres(compose_file: Path) -> None:
    """Start the dockerized Postgres dev database if requested."""
    if not compose_file.exists():
        raise FileNotFoundError(f"Docker compose file not found: {compose_file}")
    print(f"[dev-stack] Ensuring Postgres is running via {compose_file}...", flush=True)
    try:
        subprocess.run(
            ["docker", "compose", "-f", str(compose_file), "up", "-d"],
            cwd=str(ROOT),
            check=True,
        )
    except FileNotFoundError as exc:
        raise RuntimeError("Docker is required to run the Postgres stack but is not installed.") from exc


def _build_backend_env(args: argparse.Namespace) -> Dict[str, str]:
    """
    Construct environment variables for the backend process. When Postgres is selected,
    default datasource credentials and the Spring profile are configured unless already set.
    """
    env = os.environ.copy()
    if args.database == "postgres":
        env.setdefault("SPRING_PROFILES_ACTIVE", args.postgres_profile)
        env.setdefault("SPRING_DATASOURCE_URL", args.postgres_url)
        env.setdefault("SPRING_DATASOURCE_USERNAME", args.postgres_username)
        env.setdefault("SPRING_DATASOURCE_PASSWORD", args.postgres_password)
        env.setdefault("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver")
    return env


def main() -> None:
    args = parse_args()
    backend_cmd = ["mvn"] + shlex.split(args.backend_goal)
    frontend_cmd = ["npm", "run", "dev", "--", "--port", str(args.frontend_port)]
    compose_file = Path(args.docker_compose_file)
    if not compose_file.is_absolute():
        compose_file = ROOT / compose_file

    running: List[Tuple[str, subprocess.Popen]] = []
    _attach_signal_handlers(running)

    if args.database == "postgres":
        try:
            _ensure_postgres(compose_file)
        except Exception as exc:  # noqa: BLE001 - user-facing CLI error handling
            print(f"[dev-stack] Failed to start Postgres: {exc}", file=sys.stderr)
            sys.exit(1)

    backend_env = _build_backend_env(args)

    print("[dev-stack] Starting Spring Boot backend...", flush=True)
    backend = _start_process(backend_cmd, cwd=ROOT, env=backend_env)
    running.append(("backend", backend))

    try:
        _wait_for_backend(args.backend_url, args.backend_timeout)
    except RuntimeError as exc:
        print(f"[dev-stack] {exc}", file=sys.stderr)
        for name, proc in running:
            proc.terminate()
        sys.exit(1)

    print("[dev-stack] Backend is UP, preparing frontend...", flush=True)
    _maybe_install_frontend(args.skip_frontend_install)

    print("[dev-stack] Launching Vite dev server...", flush=True)
    frontend = _start_process(frontend_cmd, cwd=FRONTEND_DIR)
    running.append(("frontend", frontend))

    print(
        "[dev-stack] Both servers are running.\n"
        "  • API:    http://localhost:8080\n"
        f"  • UI:     http://localhost:{args.frontend_port}\n"
        "Press Ctrl+C to stop both.",
        flush=True,
    )

    try:
        while True:
            for name, proc in running:
                if proc.poll() is not None:
                    raise RuntimeError(f"{name} process exited with code {proc.returncode}")
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        for name, proc in running:
            if proc.poll() is None:
                proc.terminate()


if __name__ == "__main__":
    main()
