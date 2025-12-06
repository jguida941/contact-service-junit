#!/usr/bin/env python3
"""
Environment configuration helpers for the Contact Suite CLI.

This module provides centralized environment variable management for different
runtime modes (dev, prod-local, ci-local). Environment variables are set
automatically based on the mode, with guard rails to prevent common mistakes.

Usage:
    from scripts.runtime_env import DevEnvironment, ProdLocalEnvironment

    # Development mode
    env = DevEnvironment(database="postgres").build()

    # Production simulation
    env = ProdLocalEnvironment().build()  # Will raise if JWT_SECRET not set
"""

from __future__ import annotations

import os
import re
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Dict, Optional


# Project root directory
ROOT = Path(__file__).resolve().parents[1]

# Dev default JWT secret (intentionally insecure - only for local dev)
DEV_DEFAULT_JWT_SECRET = "devsecretkey123456789012345678901234567890"


class DatabaseType(Enum):
    """Supported database backends."""
    H2 = "h2"
    POSTGRES = "postgres"


class SpringProfile(Enum):
    """Spring Boot profiles."""
    DEFAULT = "default"
    DEV = "dev"
    PROD = "prod"


@dataclass
class EnvironmentConfig:
    """
    Base configuration for environment variables.

    Attributes:
        cookie_secure: Whether cookies require HTTPS (false for localhost HTTP)
        spring_profile: Spring Boot profile to activate
        csp_relaxed: Whether to relax CSP for Vite HMR
        jwt_secret: JWT signing secret (required for prod-local)
        require_ssl: Whether to require SSL/TLS
        database: Database type (h2 or postgres)
        postgres_url: JDBC URL for Postgres
        postgres_username: Postgres username
        postgres_password: Postgres password
    """
    cookie_secure: bool = False
    spring_profile: SpringProfile = SpringProfile.DEFAULT
    csp_relaxed: bool = False
    jwt_secret: Optional[str] = None
    require_ssl: bool = False
    database: DatabaseType = DatabaseType.H2
    postgres_url: str = "jdbc:postgresql://localhost:5432/contactapp"
    postgres_username: str = "contactapp"
    postgres_password: str = "contactapp"
    extra_vars: Dict[str, str] = field(default_factory=dict)


class EnvironmentBuilder(ABC):
    """Abstract base for environment builders."""

    def __init__(self):
        self._config = EnvironmentConfig()

    @abstractmethod
    def validate(self) -> None:
        """Validate configuration. Raises ValueError if invalid."""
        pass

    def build(self) -> Dict[str, str]:
        """Build and return the environment dictionary."""
        self.validate()
        env = os.environ.copy()

        # Cookie security
        env["APP_AUTH_COOKIE_SECURE"] = str(self._config.cookie_secure).lower()
        env["COOKIE_SECURE"] = str(self._config.cookie_secure).lower()

        # Spring profile
        env["SPRING_PROFILES_ACTIVE"] = self._config.spring_profile.value

        # CSP relaxation (for Vite HMR)
        if self._config.csp_relaxed:
            env["CSP_RELAXED"] = "true"

        # JWT secret
        if self._config.jwt_secret:
            env["JWT_SECRET"] = self._config.jwt_secret

        # SSL requirement
        env["REQUIRE_SSL"] = str(self._config.require_ssl).lower()

        # Postgres configuration
        if self._config.database == DatabaseType.POSTGRES:
            env.setdefault("SPRING_DATASOURCE_URL", self._config.postgres_url)
            env.setdefault("SPRING_DATASOURCE_USERNAME", self._config.postgres_username)
            env.setdefault("SPRING_DATASOURCE_PASSWORD", self._config.postgres_password)
            env.setdefault("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver")

        # Extra variables
        env.update(self._config.extra_vars)

        return env


class DevEnvironment(EnvironmentBuilder):
    """
    Development environment configuration.

    Sets up environment for local HTTP development:
    - Secure cookies disabled (HTTP works on localhost)
    - CSP relaxed for Vite HMR
    - Dev default JWT secret allowed

    Usage:
        env = DevEnvironment(database="postgres").build()
    """

    def __init__(self, database: str = "h2"):
        super().__init__()
        self._config.cookie_secure = False
        self._config.csp_relaxed = True
        self._config.require_ssl = False

        if database.lower() == "postgres":
            self._config.database = DatabaseType.POSTGRES
            self._config.spring_profile = SpringProfile.DEV
        else:
            self._config.database = DatabaseType.H2
            self._config.spring_profile = SpringProfile.DEFAULT

    def with_postgres_credentials(
        self,
        url: str = "jdbc:postgresql://localhost:5432/contactapp",
        username: str = "contactapp",
        password: str = "contactapp",
    ) -> "DevEnvironment":
        """Configure Postgres connection details."""
        self._config.postgres_url = url
        self._config.postgres_username = username
        self._config.postgres_password = password
        return self

    def validate(self) -> None:
        """Dev mode has no strict validation - anything goes locally."""
        pass


class ProdLocalEnvironment(EnvironmentBuilder):
    """
    Production-like environment for local testing.

    Sets up environment that simulates production:
    - Secure cookies enabled
    - CSP strict
    - JWT_SECRET REQUIRED (aborts if missing or matches dev default)
    - Postgres required

    Usage:
        # Will raise if JWT_SECRET not set
        env = ProdLocalEnvironment().build()
    """

    def __init__(self):
        super().__init__()
        self._config.cookie_secure = True
        self._config.csp_relaxed = False
        self._config.require_ssl = False  # No TLS locally by default
        self._config.database = DatabaseType.POSTGRES
        self._config.spring_profile = SpringProfile.PROD
        self._config.jwt_secret = os.environ.get("JWT_SECRET")

    def with_https(self) -> "ProdLocalEnvironment":
        """Enable HTTPS requirement (for self-signed cert testing)."""
        self._config.require_ssl = True
        return self

    def validate(self) -> None:
        """Validate production requirements."""
        jwt_secret = self._config.jwt_secret

        if not jwt_secret:
            raise ValueError(
                "JWT_SECRET environment variable is required for prod-local mode.\n"
                "Set it with: export JWT_SECRET=$(openssl rand -base64 32)"
            )

        if jwt_secret == DEV_DEFAULT_JWT_SECRET:
            raise ValueError(
                "JWT_SECRET cannot be the dev default in prod-local mode.\n"
                "Generate a secure secret with: openssl rand -base64 32"
            )

        if len(jwt_secret) < 32:
            raise ValueError(
                f"JWT_SECRET must be at least 32 characters (got {len(jwt_secret)}).\n"
                "Generate a secure secret with: openssl rand -base64 32"
            )


class CILocalEnvironment(EnvironmentBuilder):
    """
    CI environment for local reproduction.

    Mirrors GitHub Actions java-ci.yml settings:
    - Same environment as CI
    - NVD_API_KEY optional (faster OWASP scans if set)

    Usage:
        env = CILocalEnvironment().build()
    """

    def __init__(self):
        super().__init__()
        self._config.cookie_secure = True
        self._config.spring_profile = SpringProfile.DEFAULT
        self._config.csp_relaxed = False
        self._config.require_ssl = False

    def validate(self) -> None:
        """CI mode validates NVD_API_KEY presence (warning only)."""
        if not os.environ.get("NVD_API_KEY"):
            import sys
            print(
                "[WARN] NVD_API_KEY not set. OWASP Dependency-Check will be slower.\n"
                "       Get a free key at: https://nvd.nist.gov/developers/request-an-api-key",
                file=sys.stderr,
            )


def is_jwt_secret_valid(secret: Optional[str]) -> bool:
    """Check if a JWT secret is valid for production use."""
    if not secret:
        return False
    if secret == DEV_DEFAULT_JWT_SECRET:
        return False
    if len(secret) < 32:
        return False
    return True


def mask_sensitive_value(value: str) -> str:
    """Mask a sensitive value for logging (show first/last 2 chars)."""
    if len(value) <= 4:
        return "****"
    return f"{value[:2]}...{value[-2:]}"


def get_project_root() -> Path:
    """Return the project root directory."""
    return ROOT
