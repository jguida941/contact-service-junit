#!/usr/bin/env bash
# Contact Suite CLI - Shell shim
#
# This script provides a single entry point for the Contact Suite CLI.
# It runs the Python CLI tool from the scripts directory.
#
# Usage:
#   ./cs dev              # Start development environment
#   ./cs test             # Run all tests
#   ./cs health           # Check service health
#   ./cs --help           # Show all commands

set -euo pipefail

# Get the directory where this script lives (project root)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Run the CLI using Python module syntax from the project root
# This ensures the scripts module can be found regardless of current directory
cd "$SCRIPT_DIR" && exec python3 -m scripts.cs_cli "$@"
