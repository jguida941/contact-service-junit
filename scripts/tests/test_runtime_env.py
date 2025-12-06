"""
Unit tests for runtime_env.py environment configuration module.

Tests cover:
- DevEnvironment configuration
- ProdLocalEnvironment configuration and validation
- CILocalEnvironment configuration
- JWT secret validation
- Environment variable building
"""

import os
import pytest
from unittest.mock import patch

from scripts.runtime_env import (
    DevEnvironment,
    ProdLocalEnvironment,
    CILocalEnvironment,
    DatabaseType,
    SpringProfile,
    is_jwt_secret_valid,
    mask_sensitive_value,
    get_project_root,
    DEV_DEFAULT_JWT_SECRET,
)


class TestDevEnvironment:
    """Tests for DevEnvironment configuration."""

    def test_default_uses_h2_database(self):
        """Default dev environment uses H2 database."""
        env = DevEnvironment().build()
        assert env.get("SPRING_PROFILES_ACTIVE") == "default"
        assert "SPRING_DATASOURCE_URL" not in env or "postgresql" not in env.get("SPRING_DATASOURCE_URL", "")

    def test_postgres_sets_dev_profile(self):
        """Postgres database option sets dev profile."""
        env = DevEnvironment(database="postgres").build()
        assert env["SPRING_PROFILES_ACTIVE"] == "dev"
        assert "postgresql" in env.get("SPRING_DATASOURCE_URL", "")

    def test_cookie_secure_disabled(self):
        """Dev environment disables secure cookies for HTTP localhost."""
        env = DevEnvironment().build()
        assert env["APP_AUTH_COOKIE_SECURE"] == "false"
        assert env["COOKIE_SECURE"] == "false"

    def test_csp_relaxed_for_vite(self):
        """Dev environment relaxes CSP for Vite HMR."""
        env = DevEnvironment().build()
        assert env.get("CSP_RELAXED") == "true"

    def test_require_ssl_disabled(self):
        """Dev environment does not require SSL."""
        env = DevEnvironment().build()
        assert env["REQUIRE_SSL"] == "false"

    def test_custom_postgres_credentials(self):
        """Can set custom Postgres credentials."""
        env = DevEnvironment(database="postgres").with_postgres_credentials(
            url="jdbc:postgresql://custom:5432/mydb",
            username="myuser",
            password="mypass",
        ).build()
        assert env["SPRING_DATASOURCE_URL"] == "jdbc:postgresql://custom:5432/mydb"
        assert env["SPRING_DATASOURCE_USERNAME"] == "myuser"
        assert env["SPRING_DATASOURCE_PASSWORD"] == "mypass"

    def test_validation_always_passes(self):
        """Dev environment validation never fails (anything goes locally)."""
        env = DevEnvironment()
        env.validate()  # Should not raise


class TestProdLocalEnvironment:
    """Tests for ProdLocalEnvironment configuration and validation."""

    def test_requires_jwt_secret(self):
        """ProdLocalEnvironment requires JWT_SECRET to be set."""
        with patch.dict(os.environ, {}, clear=True):
            with pytest.raises(ValueError, match="JWT_SECRET environment variable is required"):
                ProdLocalEnvironment().build()

    def test_rejects_dev_default_jwt_secret(self):
        """ProdLocalEnvironment rejects the dev default JWT secret."""
        with patch.dict(os.environ, {"JWT_SECRET": DEV_DEFAULT_JWT_SECRET}):
            with pytest.raises(ValueError, match="cannot be the dev default"):
                ProdLocalEnvironment().build()

    def test_rejects_short_jwt_secret(self):
        """ProdLocalEnvironment rejects JWT secrets shorter than 32 chars."""
        with patch.dict(os.environ, {"JWT_SECRET": "short"}):
            with pytest.raises(ValueError, match="must be at least 32 characters"):
                ProdLocalEnvironment().build()

    def test_accepts_valid_jwt_secret(self):
        """ProdLocalEnvironment accepts valid JWT secret."""
        valid_secret = "a" * 32
        with patch.dict(os.environ, {"JWT_SECRET": valid_secret}):
            env = ProdLocalEnvironment().build()
            assert env["JWT_SECRET"] == valid_secret

    def test_cookie_secure_enabled(self):
        """ProdLocalEnvironment enables secure cookies."""
        valid_secret = "a" * 32
        with patch.dict(os.environ, {"JWT_SECRET": valid_secret}):
            env = ProdLocalEnvironment().build()
            assert env["APP_AUTH_COOKIE_SECURE"] == "true"
            assert env["COOKIE_SECURE"] == "true"

    def test_prod_profile_active(self):
        """ProdLocalEnvironment uses prod profile."""
        valid_secret = "a" * 32
        with patch.dict(os.environ, {"JWT_SECRET": valid_secret}):
            env = ProdLocalEnvironment().build()
            assert env["SPRING_PROFILES_ACTIVE"] == "prod"

    def test_csp_strict(self):
        """ProdLocalEnvironment does not relax CSP."""
        valid_secret = "a" * 32
        with patch.dict(os.environ, {"JWT_SECRET": valid_secret}):
            env = ProdLocalEnvironment().build()
            assert env.get("CSP_RELAXED") != "true"

    def test_https_flag_sets_require_ssl(self):
        """with_https() sets REQUIRE_SSL to true."""
        valid_secret = "a" * 32
        with patch.dict(os.environ, {"JWT_SECRET": valid_secret}):
            env = ProdLocalEnvironment().with_https().build()
            assert env["REQUIRE_SSL"] == "true"


class TestCILocalEnvironment:
    """Tests for CILocalEnvironment configuration."""

    def test_uses_default_profile(self):
        """CI environment uses default profile."""
        env = CILocalEnvironment().build()
        assert env["SPRING_PROFILES_ACTIVE"] == "default"

    def test_cookie_secure_enabled(self):
        """CI environment enables secure cookies like CI."""
        env = CILocalEnvironment().build()
        assert env["APP_AUTH_COOKIE_SECURE"] == "true"

    def test_warns_when_nvd_api_key_missing(self, capsys):
        """CI environment warns when NVD_API_KEY is not set."""
        with patch.dict(os.environ, {}, clear=True):
            # Remove NVD_API_KEY if present
            os.environ.pop("NVD_API_KEY", None)
            CILocalEnvironment().build()
            captured = capsys.readouterr()
            assert "NVD_API_KEY not set" in captured.err

    def test_no_warning_when_nvd_api_key_set(self, capsys):
        """CI environment does not warn when NVD_API_KEY is set."""
        with patch.dict(os.environ, {"NVD_API_KEY": "test-key"}):
            CILocalEnvironment().build()
            captured = capsys.readouterr()
            assert "NVD_API_KEY" not in captured.err


class TestJwtSecretValidation:
    """Tests for is_jwt_secret_valid helper."""

    def test_none_is_invalid(self):
        """None JWT secret is invalid."""
        assert is_jwt_secret_valid(None) is False

    def test_empty_is_invalid(self):
        """Empty JWT secret is invalid."""
        assert is_jwt_secret_valid("") is False

    def test_dev_default_is_invalid(self):
        """Dev default JWT secret is invalid for production."""
        assert is_jwt_secret_valid(DEV_DEFAULT_JWT_SECRET) is False

    def test_short_secret_is_invalid(self):
        """JWT secret shorter than 32 chars is invalid."""
        assert is_jwt_secret_valid("a" * 31) is False

    def test_valid_secret_accepted(self):
        """Valid JWT secret (32+ chars, not dev default) is accepted."""
        assert is_jwt_secret_valid("a" * 32) is True
        assert is_jwt_secret_valid("b" * 64) is True


class TestMaskSensitiveValue:
    """Tests for mask_sensitive_value helper."""

    def test_short_values_fully_masked(self):
        """Values 4 chars or shorter are fully masked."""
        assert mask_sensitive_value("abc") == "****"
        assert mask_sensitive_value("abcd") == "****"

    def test_longer_values_show_first_and_last(self):
        """Values longer than 4 chars show first 2 and last 2."""
        assert mask_sensitive_value("abcde") == "ab...de"
        assert mask_sensitive_value("secret123") == "se...23"


class TestGetProjectRoot:
    """Tests for get_project_root helper."""

    def test_returns_path(self):
        """get_project_root returns a Path object."""
        root = get_project_root()
        assert root.exists()
        assert (root / "pom.xml").exists()


class TestDatabaseType:
    """Tests for DatabaseType enum."""

    def test_h2_value(self):
        """H2 database type has correct value."""
        assert DatabaseType.H2.value == "h2"

    def test_postgres_value(self):
        """Postgres database type has correct value."""
        assert DatabaseType.POSTGRES.value == "postgres"


class TestSpringProfile:
    """Tests for SpringProfile enum."""

    def test_default_value(self):
        """Default profile has correct value."""
        assert SpringProfile.DEFAULT.value == "default"

    def test_dev_value(self):
        """Dev profile has correct value."""
        assert SpringProfile.DEV.value == "dev"

    def test_prod_value(self):
        """Prod profile has correct value."""
        assert SpringProfile.PROD.value == "prod"
