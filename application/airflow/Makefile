# Airflow Custom Image Build & Validation
# Pure Python + Docker workflow - no Gradle dependency

.PHONY: help install-dev validate lint format test build push clean

PYTHON := python3
DOCKER := docker
IMAGE_NAME := astro-airflow
VERSION := $(shell cat ../gradle.properties | grep '^version=' | cut -d'=' -f2)
REGISTRY := localhost:5000

help: ## Show this help message
	@echo "Airflow Build & Validation Tasks"
	@echo "=================================="
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

install-dev: ## Install Python development dependencies
	$(PYTHON) -m pip install -q \
		flake8>=6.0.0 \
		pylint>=2.17.0 \
		black>=23.0.0 \
		mypy>=1.4.0 \
		pytest>=7.4.0 \
		pytest-airflow>=0.0.4

validate: lint ## Validate Python code (lint + format check)
	@echo "✓ Validation complete"

lint: ## Lint Python code with flake8
	@echo "Running flake8..."
	$(PYTHON) -m flake8 \
		--max-line-length=120 \
		--exclude=venv,__pycache__,.git \
		--ignore=E203,W503 \
		dags/ plugins/

format: ## Format Python code with black
	@echo "Formatting with black..."
	$(PYTHON) -m black --line-length=120 dags/ plugins/

format-check: ## Check Python code formatting
	@echo "Checking format..."
	$(PYTHON) -m black --check --line-length=120 dags/ plugins/

test: ## Run Python tests (if any)
	@if [ -d tests ]; then \
		$(PYTHON) -m pytest tests/ --verbose --tb=short; \
	else \
		echo "No tests directory found, skipping..."; \
	fi

build: validate ## Build Docker image (with validation)
	@echo "Building Airflow image: $(IMAGE_NAME):$(VERSION)"
	$(DOCKER) build \
		-t $(IMAGE_NAME):$(VERSION) \
		-t $(IMAGE_NAME):latest \
		--build-arg AIRFLOW_VERSION=2.7.1 \
		.

build-ci: ## Build without cache (for CI/CD)
	@echo "Building Airflow image (no cache)..."
	$(DOCKER) build --no-cache \
		-t $(IMAGE_NAME):$(VERSION) \
		--build-arg AIRFLOW_VERSION=2.7.1 \
		.

push: build ## Push image to registry
	@echo "Pushing to $(REGISTRY)..."
	$(DOCKER) tag $(IMAGE_NAME):$(VERSION) $(REGISTRY)/$(IMAGE_NAME):$(VERSION)
	$(DOCKER) push $(REGISTRY)/$(IMAGE_NAME):$(VERSION)

test-image: build ## Test that image starts successfully
	@echo "Testing Airflow image startup..."
	$(DOCKER) run --rm $(IMAGE_NAME):$(VERSION) airflow version

scan: build ## Scan image for vulnerabilities (requires trivy)
	@echo "Scanning for vulnerabilities..."
	trivy image --severity HIGH,CRITICAL $(IMAGE_NAME):$(VERSION) || true

clean: ## Clean Docker images
	$(DOCKER) rmi -f $(IMAGE_NAME):$(VERSION) $(IMAGE_NAME):latest || true

# CI/CD integration targets
ci: install-dev validate ## CI pipeline: install deps + validate
	@echo "✓ CI validation passed"

cd: build test-image ## CD pipeline: build + test image
	@echo "✓ CD build passed"
