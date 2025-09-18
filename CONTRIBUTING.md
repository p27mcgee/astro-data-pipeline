# Contributing to Astronomical Data Pipeline

Thank you for your interest in contributing to the Astronomical Data Pipeline project! This document provides guidelines for contributing to this project.

## 🌟 How to Contribute

### Prerequisites
- Java 17+
- Python 3.9+
- Terraform 1.0+
- Docker
- kubectl
- Git

### Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd astro-data-pipeline
   ```

2. **Install pre-commit hooks**
   ```bash
   pip install pre-commit
   pre-commit install
   ```

3. **Set up environment**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

4. **Build and test**
   ```bash
   # Java applications
   cd application/image-processor
   ./gradlew build test
   
   # Python tools
   cd scripts/performance-testing
   pip install -r requirements.txt
   python -m pytest
   ```

## 📝 Contribution Process

### 1. Issue Creation
- Search existing issues before creating new ones
- Use issue templates when available
- Provide clear reproduction steps for bugs
- Include relevant astronomical context for features

### 2. Branch Naming
```
feature/description-of-feature
bugfix/description-of-bug
hotfix/critical-fix
docs/documentation-update
perf/performance-improvement
```

### 3. Code Standards

#### Java
- Follow Google Java Style Guide
- Use Lombok for boilerplate reduction
- Write comprehensive tests (unit + integration)
- Document public APIs with Javadoc
- Use Spring Boot best practices

#### Python
- Follow PEP 8 style guide
- Use Black for formatting
- Type hints for all functions
- Docstrings for modules and functions
- Use pytest for testing

#### Terraform
- Use consistent naming conventions
- Tag all resources appropriately
- Use variables for configuration
- Document modules with terraform-docs
- Run tflint and tfsec

#### Kubernetes
- Use proper resource limits
- Include health checks
- Use secrets for sensitive data
- Follow security best practices

### 4. Testing Requirements

#### Mandatory Tests
- Unit tests (>80% coverage)
- Integration tests for APIs
- Terraform validation
- Security scans pass
- Performance tests for critical paths

#### Test Commands
```bash
# Java
./gradlew test integrationTest

# Python
python -m pytest scripts/

# Terraform
terraform validate
tflint
tfsec

# Full validation
./scripts/run-all-tests.sh
```

### 5. Documentation
- Update README.md for new features
- Add architectural documentation
- Include deployment instructions
- Document astronomical algorithms
- Update API documentation

### 6. Commit Messages
Use conventional commits format:
```
type(scope): description

feat(processor): add dark frame subtraction algorithm
fix(api): resolve memory leak in FITS parsing
docs(readme): update installation instructions
perf(database): optimize catalog query performance
test(integration): add S3 processing tests
```

Types: feat, fix, docs, style, refactor, perf, test, chore

### 7. Pull Request Process

#### Before Submitting
- [ ] All tests pass locally
- [ ] Pre-commit hooks pass
- [ ] Documentation updated
- [ ] Self-review completed
- [ ] No merge conflicts

#### PR Template
```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update
- [ ] Performance improvement

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing completed

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] Tests added for new functionality
- [ ] All CI checks pass
```

#### Review Process
1. Automated checks must pass
2. Code review by maintainer
3. Manual testing if needed
4. Approval and merge

## 🔬 Astronomical Domain Guidelines

### Scientific Accuracy
- Use established astronomical conventions
- Reference relevant scientific papers
- Validate algorithms against known data
- Include proper error handling for edge cases

### FITS File Handling
- Follow FITS standard specifications
- Handle various astronomical coordinate systems
- Support standard FITS keywords
- Preserve metadata throughout processing

### Performance Considerations
- Optimize for large astronomical datasets
- Consider memory usage for multi-GB files
- Use appropriate data structures
- Profile performance-critical sections

### Coordinate Systems
- Use IAU-approved coordinate systems
- Handle coordinate transformations properly
- Support multiple epochs (J2000, B1950, etc.)
- Include proper motion calculations

## 🚨 Security Guidelines

### Secrets Management
- Never commit credentials
- Use environment variables
- Leverage AWS Secrets Manager
- Rotate keys regularly

### Code Security
- No hardcoded passwords
- Validate all inputs
- Use parameterized queries
- Follow OWASP guidelines

### Infrastructure Security
- Enable encryption at rest
- Use least privilege access
- Regular security updates
- Monitor for vulnerabilities

## 📊 Performance Standards

### Response Times
- API endpoints: < 200ms (95th percentile)
- FITS processing: < 5 minutes per GB
- Database queries: < 100ms
- Health checks: < 50ms

### Throughput
- Process 100+ FITS files/hour
- Support 50+ concurrent users
- Handle 1TB+ data volumes
- Maintain 99.9% uptime

## 🐛 Bug Reports

### Include
- Operating system and version
- Java/Python version
- Clear reproduction steps
- Expected vs actual behavior
- Relevant log files
- FITS file characteristics (if applicable)

### Astronomical Data Issues
- Coordinate system used
- Epoch information
- File format details
- Processing parameters
- Expected astronomical results

## 💡 Feature Requests

### Scientific Features
- Astronomical justification
- Literature references
- Expected performance impact
- Compatibility considerations

### Infrastructure Features
- Scalability requirements
- Security implications
- Monitoring needs
- Documentation requirements

## 🏆 Recognition

Contributors will be acknowledged in:
- CONTRIBUTORS.md file
- Release notes
- Scientific publications (where appropriate)
- Annual contributor recognition

## 📞 Getting Help

- GitHub Issues for bugs and features
- GitHub Discussions for questions
- Email maintainers for security issues
- Scientific consultation for astronomical algorithms

## 📜 Code of Conduct

- Be respectful and inclusive
- Welcome newcomers
- Focus on constructive feedback
- Maintain scientific rigor
- Support educational goals

---

Thank you for contributing to advancing astronomical data processing! 🌌