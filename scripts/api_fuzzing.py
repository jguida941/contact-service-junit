#!/usr/bin/env python3
"""
API Fuzzing Script for Phase 2.5.

Runs Schemathesis against the OpenAPI spec to detect:
- 5xx server errors (unexpected crashes)
- Schema violations (response doesn't match OpenAPI spec)
- Data generation edge cases

Usage:
    python scripts/api_fuzzing.py [--base-url URL] [--spec-path PATH]

The script expects the Spring Boot app to already be running, or it will
start one in the background and shut it down when done.

Exit codes:
    0 - All tests passed
    1 - Fuzzing found issues (5xx errors or schema violations)
    2 - Script error (app failed to start, dependencies missing, etc.)
"""

import argparse
import os
import signal
import subprocess
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

# Default configuration
DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_SPEC_PATH = "/v3/api-docs"
DEFAULT_TIMEOUT = 120  # seconds to wait for app startup
HEALTH_CHECK_INTERVAL = 2  # seconds between health checks


def log(message: str, level: str = "INFO") -> None:
    """Print a timestamped log message."""
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] [{level}] {message}", flush=True)


def check_schemathesis_installed() -> bool:
    """Verify Schemathesis is installed."""
    try:
        result = subprocess.run(
            ["schemathesis", "--version"],
            capture_output=True,
            text=True,
            timeout=10
        )
        if result.returncode == 0:
            log(f"Schemathesis version: {result.stdout.strip()}")
            return True
    except FileNotFoundError:
        pass
    except subprocess.TimeoutExpired:
        pass
    return False


def wait_for_app(base_url: str, timeout: int) -> bool:
    """
    Wait for the Spring Boot app to be ready.

    Polls the health endpoint until it returns 200 or timeout is reached.
    """
    health_url = f"{base_url}/actuator/health"
    start_time = time.time()

    log(f"Waiting for app at {health_url} (timeout: {timeout}s)")

    while time.time() - start_time < timeout:
        try:
            with urllib.request.urlopen(health_url, timeout=5) as response:
                if response.status == 200:
                    log("App is ready (health check passed)")
                    return True
        except urllib.error.URLError:
            pass
        except Exception as e:
            log(f"Health check error: {e}", "DEBUG")

        time.sleep(HEALTH_CHECK_INTERVAL)

    log(f"Timeout waiting for app after {timeout}s", "ERROR")
    return False


def start_spring_boot_app() -> subprocess.Popen:
    """
    Start the Spring Boot application in the background.

    Returns the process handle so it can be terminated later.
    """
    log("Starting Spring Boot app with 'mvn spring-boot:run'")

    # Use Maven to start the app in the background
    # The -q flag reduces Maven output noise
    process = subprocess.Popen(
        ["mvn", "-q", "spring-boot:run"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        preexec_fn=os.setsid if os.name != "nt" else None
    )

    log(f"Started Spring Boot app (PID: {process.pid})")
    return process


def stop_spring_boot_app(process: subprocess.Popen) -> None:
    """Stop the Spring Boot application."""
    if process is None:
        return

    log(f"Stopping Spring Boot app (PID: {process.pid})")

    try:
        # On Unix, kill the entire process group to ensure child processes are killed
        if os.name != "nt":
            os.killpg(os.getpgid(process.pid), signal.SIGTERM)
        else:
            process.terminate()

        # Wait for graceful shutdown
        process.wait(timeout=10)
        log("App stopped gracefully")
    except subprocess.TimeoutExpired:
        log("Forcing app shutdown", "WARN")
        if os.name != "nt":
            os.killpg(os.getpgid(process.pid), signal.SIGKILL)
        else:
            process.kill()
    except Exception as e:
        log(f"Error stopping app: {e}", "WARN")


def export_openapi_spec(base_url: str, spec_path: str, output_file: Path) -> bool:
    """
    Export the OpenAPI spec from the running app to a local file.

    This allows the spec to be archived as a CI artifact for ZAP or other tools.
    """
    spec_url = f"{base_url}{spec_path}"
    log(f"Exporting OpenAPI spec from {spec_url}")

    try:
        with urllib.request.urlopen(spec_url, timeout=30) as response:
            spec_content = response.read()
            output_file.parent.mkdir(parents=True, exist_ok=True)
            output_file.write_bytes(spec_content)
            log(f"OpenAPI spec saved to {output_file}")
            return True
    except Exception as e:
        log(f"Failed to export OpenAPI spec: {e}", "ERROR")
        return False


def run_schemathesis(base_url: str, spec_path: str) -> int:
    """
    Run Schemathesis against the OpenAPI spec.

    Returns:
        0 if all tests pass
        1 if any tests fail (5xx errors, schema violations)
        2 if Schemathesis itself fails to run
    """
    spec_url = f"{base_url}{spec_path}"

    log(f"Running Schemathesis against {spec_url}")

    # Schemathesis v4+ removed many options:
    # - --base-url (now inferred from spec URL)
    # - --hypothesis-* options
    # - --junit-xml (removed in v4+)
    # NOT using content_type_conformance: malformed URLs (invalid Unicode) fail at
    # Tomcat's connector level BEFORE reaching JsonErrorReportValve, returning HTML.
    # See ADR-0022 for details.
    cmd = [
        "schemathesis", "run",
        spec_url,
        "--checks", "not_a_server_error",
        "--checks", "response_schema_conformance",
        "--workers", "1",
        "--max-examples", "50",  # Keep CI fast
    ]

    log(f"Command: {' '.join(cmd)}")

    try:
        result = subprocess.run(
            cmd,
            capture_output=False,  # Stream output to console
            timeout=600  # 10 minute timeout for entire fuzzing run
        )

        if result.returncode == 0:
            log("Schemathesis completed successfully (no issues found)")
            return 0
        else:
            log(f"Schemathesis found issues (exit code: {result.returncode})", "ERROR")
            return 1

    except subprocess.TimeoutExpired:
        log("Schemathesis timed out after 10 minutes", "ERROR")
        return 2
    except Exception as e:
        log(f"Failed to run Schemathesis: {e}", "ERROR")
        return 2


def main() -> int:
    """Main entry point for the API fuzzing script."""
    parser = argparse.ArgumentParser(
        description="Run API fuzzing against the OpenAPI spec"
    )
    parser.add_argument(
        "--base-url",
        default=DEFAULT_BASE_URL,
        help=f"Base URL of the running app (default: {DEFAULT_BASE_URL})"
    )
    parser.add_argument(
        "--spec-path",
        default=DEFAULT_SPEC_PATH,
        help=f"Path to OpenAPI spec (default: {DEFAULT_SPEC_PATH})"
    )
    parser.add_argument(
        "--start-app",
        action="store_true",
        help="Start the Spring Boot app before fuzzing (and stop it after)"
    )
    parser.add_argument(
        "--export-spec",
        type=Path,
        help="Export OpenAPI spec to this file (for ZAP/other tools)"
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=DEFAULT_TIMEOUT,
        help=f"Timeout for app startup in seconds (default: {DEFAULT_TIMEOUT})"
    )

    args = parser.parse_args()

    # Check dependencies
    if not check_schemathesis_installed():
        log("Schemathesis not installed. Install with: pip install schemathesis", "ERROR")
        return 2

    app_process = None
    exit_code = 0

    try:
        # Start app if requested
        if args.start_app:
            app_process = start_spring_boot_app()
            time.sleep(5)  # Give Maven time to start

        # Wait for app to be ready
        if not wait_for_app(args.base_url, args.timeout):
            log("App is not responding. Is it running?", "ERROR")
            return 2

        # Export spec if requested (for ZAP artifacts)
        if args.export_spec:
            if not export_openapi_spec(args.base_url, args.spec_path, args.export_spec):
                log("Failed to export OpenAPI spec, continuing with fuzzing", "WARN")

        # Run the fuzzing
        exit_code = run_schemathesis(args.base_url, args.spec_path)

    except KeyboardInterrupt:
        log("Interrupted by user", "WARN")
        exit_code = 2
    finally:
        # Clean up
        if app_process:
            stop_spring_boot_app(app_process)

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
