# =============================================================================
# Makefile for CS320 Contact Service
# =============================================================================
# Spring Boot + React application with Maven, Docker, and PostgreSQL
#
# Quick Start:
#   make help              - Show all available targets
#   make build             - Full Maven build
#   make test              - Run all tests
#   make docker-up         - Start complete application stack
#   make dev-db            - Start development database only
#   make run               - Run Spring Boot app locally
# =============================================================================

# -----------------------------------------------------------------------------
# Configuration Variables
# -----------------------------------------------------------------------------
# Maven wrapper executable (falls back to system Maven if wrapper not found)
MVN := $(shell if [ -f ./mvnw ]; then echo "./mvnw"; else echo "mvn"; fi)

# Docker configuration
DOCKER_IMAGE := contactapp-service
COMPOSE_FILE := docker-compose.yml
COMPOSE_DEV_FILE := docker-compose.dev.yml

# Project directories
SRC_DIR := src
UI_DIR := ui/contact-app
TARGET_DIR := target

# Maven build options
MVN_OPTS := -B
SKIP_TESTS := -DskipTests
SKIP_QUALITY := -Dpit.skip=true -Dspotbugs.skip=true -Ddependency.check.skip=true

# Test configuration
UNIT_TEST_PATTERN := *Test.java
INTEGRATION_TEST_PATTERN := *IT.java

# Colors for output (optional, for better UX)
COLOR_RESET := \033[0m
COLOR_BOLD := \033[1m
COLOR_GREEN := \033[32m
COLOR_BLUE := \033[34m
COLOR_YELLOW := \033[33m

# -----------------------------------------------------------------------------
# Phony Targets (not actual files)
# -----------------------------------------------------------------------------
.PHONY: help build build-skip-tests test test-unit test-integration verify \
        docker-build docker-up docker-down docker-logs docker-clean \
        dev-db run clean lint coverage mutation security \
        install deps format check-format

# -----------------------------------------------------------------------------
# Default Target
# -----------------------------------------------------------------------------
.DEFAULT_GOAL := help

# -----------------------------------------------------------------------------
# Help Target
# -----------------------------------------------------------------------------
help: ## Display this help message with all available targets
	@echo "$(COLOR_BOLD)CS320 Contact Service - Available Make Targets$(COLOR_RESET)"
	@echo ""
	@echo "$(COLOR_BLUE)Build Targets:$(COLOR_RESET)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; /^build|^test|^verify|^clean|^install/ {printf "  $(COLOR_GREEN)%-20s$(COLOR_RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(COLOR_BLUE)Docker Targets:$(COLOR_RESET)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; /^docker/ {printf "  $(COLOR_GREEN)%-20s$(COLOR_RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(COLOR_BLUE)Development Targets:$(COLOR_RESET)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; /^dev|^run|^deps/ {printf "  $(COLOR_GREEN)%-20s$(COLOR_RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(COLOR_BLUE)Quality Targets:$(COLOR_RESET)"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; /^lint|^coverage|^mutation|^security|^check/ {printf "  $(COLOR_GREEN)%-20s$(COLOR_RESET) %s\n", $$1, $$2}'
	@echo ""
	@echo "$(COLOR_YELLOW)Examples:$(COLOR_RESET)"
	@echo "  make build              # Build the entire project"
	@echo "  make test               # Run all tests (unit + integration)"
	@echo "  make docker-up          # Start the application in Docker"
	@echo "  make dev-db && make run # Start DB and run app locally"
	@echo ""

# =============================================================================
# Build Targets
# =============================================================================

install: deps ## Install dependencies (alias for deps)

deps: ## Download and cache all Maven dependencies
	@echo "$(COLOR_BOLD)Downloading Maven dependencies...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) dependency:go-offline

build: ## Full Maven build (clean, compile, test, package)
	@echo "$(COLOR_BOLD)Building project with tests...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) clean package

build-skip-tests: ## Build without running tests (faster for local dev)
	@echo "$(COLOR_BOLD)Building project without tests...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) clean package $(SKIP_TESTS) $(SKIP_QUALITY)

test: ## Run all tests (unit + integration)
	@echo "$(COLOR_BOLD)Running all tests...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) test verify -DskipITs=false

test-unit: ## Run unit tests only (excludes *IT.java)
	@echo "$(COLOR_BOLD)Running unit tests...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) test

test-integration: ## Run integration tests only (*IT.java)
	@echo "$(COLOR_BOLD)Running integration tests...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) verify -DskipTests=true -DskipITs=false

verify: ## Full verification with all quality gates (checkstyle, spotbugs, jacoco)
	@echo "$(COLOR_BOLD)Running full verification with quality checks...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) clean verify -DskipITs=false

clean: ## Clean all build artifacts (target/, node_modules/, etc.)
	@echo "$(COLOR_BOLD)Cleaning build artifacts...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) clean
	@if [ -d "$(UI_DIR)/node_modules" ]; then \
		echo "Cleaning UI node_modules..."; \
		rm -rf $(UI_DIR)/node_modules; \
	fi
	@if [ -d "$(UI_DIR)/dist" ]; then \
		echo "Cleaning UI dist..."; \
		rm -rf $(UI_DIR)/dist; \
	fi
	@echo "$(COLOR_GREEN)Clean complete!$(COLOR_RESET)"

# =============================================================================
# Docker Targets
# =============================================================================

docker-build: ## Build Docker image for the application
	@echo "$(COLOR_BOLD)Building Docker image...$(COLOR_RESET)"
	docker build -t $(DOCKER_IMAGE):latest .
	@echo "$(COLOR_GREEN)Docker image built: $(DOCKER_IMAGE):latest$(COLOR_RESET)"

docker-up: ## Start complete application stack (app + postgres + pgadmin)
	@echo "$(COLOR_BOLD)Starting Docker Compose stack...$(COLOR_RESET)"
	docker-compose -f $(COMPOSE_FILE) up -d
	@echo "$(COLOR_GREEN)Stack started! Access:$(COLOR_RESET)"
	@echo "  Application: http://localhost:8080"
	@echo "  Health:      http://localhost:8080/actuator/health"
	@echo "  pgAdmin:     http://localhost:5050"
	@echo ""
	@echo "View logs with: make docker-logs"

docker-down: ## Stop and remove all containers (preserves volumes)
	@echo "$(COLOR_BOLD)Stopping Docker Compose stack...$(COLOR_RESET)"
	docker-compose -f $(COMPOSE_FILE) down
	@echo "$(COLOR_GREEN)Stack stopped$(COLOR_RESET)"

docker-logs: ## Follow application logs in real-time
	@echo "$(COLOR_BOLD)Following application logs (Ctrl+C to exit)...$(COLOR_RESET)"
	docker-compose -f $(COMPOSE_FILE) logs -f app

docker-clean: ## Stop containers and remove all volumes (WARNING: deletes data!)
	@echo "$(COLOR_YELLOW)WARNING: This will delete all database data!$(COLOR_RESET)"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		echo "$(COLOR_BOLD)Stopping stack and removing volumes...$(COLOR_RESET)"; \
		docker-compose -f $(COMPOSE_FILE) down -v; \
		echo "$(COLOR_GREEN)Cleanup complete$(COLOR_RESET)"; \
	else \
		echo "$(COLOR_BLUE)Cancelled$(COLOR_RESET)"; \
	fi

docker-restart: ## Restart the application container only
	@echo "$(COLOR_BOLD)Restarting application container...$(COLOR_RESET)"
	docker-compose -f $(COMPOSE_FILE) restart app

docker-ps: ## Show status of all containers
	@echo "$(COLOR_BOLD)Docker Compose Stack Status:$(COLOR_RESET)"
	docker-compose -f $(COMPOSE_FILE) ps

# =============================================================================
# Development Targets
# =============================================================================

dev-db: ## Start development database only (docker-compose.dev.yml)
	@echo "$(COLOR_BOLD)Starting development database...$(COLOR_RESET)"
	docker-compose -f $(COMPOSE_DEV_FILE) up -d
	@echo "$(COLOR_GREEN)Database started!$(COLOR_RESET)"
	@echo "  PostgreSQL: localhost:5432"
	@echo "  Database:   contactapp"
	@echo "  User:       contactapp"
	@echo "  Password:   contactapp"
	@echo ""
	@echo "Run the app with: make run"

dev-db-down: ## Stop development database
	@echo "$(COLOR_BOLD)Stopping development database...$(COLOR_RESET)"
	docker-compose -f $(COMPOSE_DEV_FILE) down

dev-db-logs: ## Follow development database logs
	docker-compose -f $(COMPOSE_DEV_FILE) logs -f

run: ## Run Spring Boot application locally (requires dev-db to be running)
	@echo "$(COLOR_BOLD)Starting Spring Boot application...$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)Note: Make sure dev database is running (make dev-db)$(COLOR_RESET)"
	$(MVN) spring-boot:run

run-dev: dev-db run ## Start dev database and run application (convenience target)

# =============================================================================
# Quality Targets
# =============================================================================

lint: ## Run Checkstyle code style checks
	@echo "$(COLOR_BOLD)Running Checkstyle...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) checkstyle:check

coverage: ## Run tests with JaCoCo code coverage report
	@echo "$(COLOR_BOLD)Running tests with coverage analysis...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) clean test jacoco:report
	@echo "$(COLOR_GREEN)Coverage report generated:$(COLOR_RESET)"
	@echo "  HTML: $(TARGET_DIR)/site/jacoco/index.html"
	@if command -v open >/dev/null 2>&1; then \
		echo "Opening coverage report..."; \
		open $(TARGET_DIR)/site/jacoco/index.html; \
	fi

mutation: ## Run PITest mutation testing (requires all tests to pass)
	@echo "$(COLOR_BOLD)Running mutation testing with PITest...$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)This may take several minutes...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) test-compile pitest:mutationCoverage
	@echo "$(COLOR_GREEN)Mutation report generated:$(COLOR_RESET)"
	@echo "  HTML: $(TARGET_DIR)/pit-reports/index.html"
	@if command -v open >/dev/null 2>&1; then \
		echo "Opening mutation report..."; \
		open $(TARGET_DIR)/pit-reports/*/index.html 2>/dev/null || true; \
	fi

security: ## Run OWASP dependency vulnerability check
	@echo "$(COLOR_BOLD)Running OWASP Dependency-Check...$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)First run may take 10-15 minutes to download CVE database...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) org.owasp:dependency-check-maven:aggregate
	@echo "$(COLOR_GREEN)Security report generated:$(COLOR_RESET)"
	@echo "  HTML: $(TARGET_DIR)/dependency-check-report.html"
	@if command -v open >/dev/null 2>&1; then \
		echo "Opening security report..."; \
		open $(TARGET_DIR)/dependency-check-report.html; \
	fi

check-format: lint ## Check code formatting (alias for lint)

format: ## Format code with Maven formatter (if configured)
	@echo "$(COLOR_BOLD)Formatting code...$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)Note: Add maven-formatter-plugin to pom.xml for auto-formatting$(COLOR_RESET)"
	@echo "Currently using Checkstyle for validation only"

spotbugs: ## Run SpotBugs static analysis
	@echo "$(COLOR_BOLD)Running SpotBugs static analysis...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) spotbugs:check
	@echo "$(COLOR_GREEN)SpotBugs report generated:$(COLOR_RESET)"
	@echo "  HTML: $(TARGET_DIR)/spotbugsXml.html"

# =============================================================================
# Combined/Convenience Targets
# =============================================================================

all: clean build verify ## Run complete build with all quality checks

ci: clean verify ## CI pipeline target (full build + all quality gates)

quick: build-skip-tests ## Quick build without tests or quality checks

dev-setup: deps dev-db ## Setup development environment (install deps + start DB)

rebuild: clean build ## Clean and rebuild everything

# =============================================================================
# Info/Status Targets
# =============================================================================

info: ## Display project information and environment status
	@echo "$(COLOR_BOLD)CS320 Contact Service - Project Information$(COLOR_RESET)"
	@echo ""
	@echo "$(COLOR_BLUE)Project Details:$(COLOR_RESET)"
	@$(MVN) help:evaluate -Dexpression=project.name -q -DforceStdout 2>/dev/null | head -1 || echo "CS320 Contact Service"
	@echo -n "Version: "
	@$(MVN) help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | head -1 || echo "1.0.0-SNAPSHOT"
	@echo ""
	@echo "$(COLOR_BLUE)Environment:$(COLOR_RESET)"
	@echo -n "Maven: "
	@$(MVN) --version 2>/dev/null | head -1 || echo "Not found"
	@echo -n "Java:  "
	@java -version 2>&1 | head -1 || echo "Not found"
	@echo -n "Docker: "
	@docker --version 2>/dev/null || echo "Not found"
	@echo -n "Docker Compose: "
	@docker-compose --version 2>/dev/null || echo "Not found"
	@echo ""
	@echo "$(COLOR_BLUE)Docker Status:$(COLOR_RESET)"
	@docker-compose -f $(COMPOSE_FILE) ps 2>/dev/null || echo "Stack not running"
	@echo ""

status: info ## Alias for info

version: ## Display Maven project version
	@$(MVN) help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | head -1

# =============================================================================
# Advanced Targets
# =============================================================================

package: build ## Build and package application (alias for build)

jar: build-skip-tests ## Build JAR file without tests
	@echo "$(COLOR_GREEN)JAR file location:$(COLOR_RESET)"
	@ls -lh $(TARGET_DIR)/*.jar | tail -1

install-local: ## Install artifact to local Maven repository
	@echo "$(COLOR_BOLD)Installing to local Maven repository...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) clean install $(SKIP_TESTS)

dependency-tree: ## Display Maven dependency tree
	$(MVN) dependency:tree

dependency-updates: ## Check for dependency updates
	$(MVN) versions:display-dependency-updates

# =============================================================================
# UI Development Targets (React)
# =============================================================================

ui-install: ## Install UI dependencies (npm install)
	@echo "$(COLOR_BOLD)Installing UI dependencies...$(COLOR_RESET)"
	cd $(UI_DIR) && npm install

ui-build: ## Build React UI for production
	@echo "$(COLOR_BOLD)Building React UI...$(COLOR_RESET)"
	cd $(UI_DIR) && npm run build

ui-dev: ## Start React development server
	@echo "$(COLOR_BOLD)Starting React dev server...$(COLOR_RESET)"
	cd $(UI_DIR) && npm run dev

ui-test: ## Run React UI tests
	@echo "$(COLOR_BOLD)Running UI tests...$(COLOR_RESET)"
	cd $(UI_DIR) && npm test

ui-lint: ## Lint React UI code
	@echo "$(COLOR_BOLD)Linting UI code...$(COLOR_RESET)"
	cd $(UI_DIR) && npm run lint

ui-clean: ## Clean UI build artifacts
	@echo "$(COLOR_BOLD)Cleaning UI artifacts...$(COLOR_RESET)"
	rm -rf $(UI_DIR)/dist $(UI_DIR)/node_modules

# =============================================================================
# Database Management Targets
# =============================================================================

db-migrate: ## Run Flyway migrations manually
	@echo "$(COLOR_BOLD)Running Flyway migrations...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) flyway:migrate

db-info: ## Show Flyway migration status
	$(MVN) $(MVN_OPTS) flyway:info

db-validate: ## Validate Flyway migrations
	$(MVN) $(MVN_OPTS) flyway:validate

db-clean-warn: ## WARNING: Drop all database objects (use with caution!)
	@echo "$(COLOR_YELLOW)WARNING: This will drop all database objects!$(COLOR_RESET)"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		$(MVN) $(MVN_OPTS) flyway:clean; \
	else \
		echo "$(COLOR_BLUE)Cancelled$(COLOR_RESET)"; \
	fi

# =============================================================================
# Documentation Targets
# =============================================================================

docs: ## Generate all documentation (Javadoc + site)
	@echo "$(COLOR_BOLD)Generating documentation...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) site

javadoc: ## Generate Javadoc documentation
	@echo "$(COLOR_BOLD)Generating Javadoc...$(COLOR_RESET)"
	$(MVN) $(MVN_OPTS) javadoc:javadoc
	@echo "$(COLOR_GREEN)Javadoc generated:$(COLOR_RESET)"
	@echo "  HTML: $(TARGET_DIR)/site/apidocs/index.html"

# =============================================================================
# Utility Functions
# =============================================================================

# Check if a command exists
check-command = $(if $(shell command -v $(1) 2>/dev/null),,$(error "$(1) is not installed. Please install it first."))

# Validate required tools
validate-tools: ## Validate that required tools are installed
	@echo "$(COLOR_BOLD)Validating required tools...$(COLOR_RESET)"
	@command -v java >/dev/null 2>&1 || { echo "$(COLOR_YELLOW)Java not found$(COLOR_RESET)"; exit 1; }
	@command -v docker >/dev/null 2>&1 || { echo "$(COLOR_YELLOW)Docker not found$(COLOR_RESET)"; exit 1; }
	@command -v docker-compose >/dev/null 2>&1 || { echo "$(COLOR_YELLOW)Docker Compose not found$(COLOR_RESET)"; exit 1; }
	@if [ -f "./mvnw" ]; then \
		echo "Maven: Using wrapper (./mvnw)"; \
	elif command -v mvn >/dev/null 2>&1; then \
		echo "Maven: Using system installation (mvn)"; \
	else \
		echo "$(COLOR_YELLOW)Maven not found (neither ./mvnw nor system mvn)$(COLOR_RESET)"; \
		exit 1; \
	fi
	@echo "$(COLOR_GREEN)All required tools are installed!$(COLOR_RESET)"
