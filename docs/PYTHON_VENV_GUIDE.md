# Python Virtual Environment Guide

## Overview

This project uses a Python virtual environment (`venv`) to isolate Python dependencies and ensure reproducible builds across different development environments.

---

## ✅ Best Practices

### 1. **Use the Python Virtual Environment**


```bash
# Activate venv in your shell
source venv/bin/activate

# Or run scripts directly with venv Python
./venv/bin/python scripts/my_script.py

# Install packages to venv
./venv/bin/pip install package_name
```

---

## 📁 What to Commit to Git

### ✅ **DO Commit:**

1. **`venv/` directory structure** (if using poetry or pipenv managed venv)
   - OR just `requirements.txt` / `Pipfile` / `pyproject.toml`

2. **Dependency lock files:**
   - `requirements.txt` - pip dependencies
   - `requirements-dev.txt` - development dependencies
   - `Pipfile.lock` - if using pipenv
   - `poetry.lock` - if using poetry

3. **Python version specification:**
   - `.python-version` - pyenv version file
   - `runtime.txt` - Heroku/deployment Python version
   - Documented in `README.md`

### ❌ **DON'T Commit:**

1. **Virtual environment binaries and packages:**
   ```gitignore
   # .gitignore
   venv/
   env/
   .venv/
   ENV/
   env.bak/
   venv.bak/
   ```

2. **Python cache files:**
   ```gitignore
   __pycache__/
   *.py[cod]
   *$py.class
   *.so
   .Python
   ```

3. **IDE-specific venv folders:**
   ```gitignore
   .idea/
   .vscode/
   *.swp
   *.swo
   ```

---

## 🎯 Current Project Configuration

### Virtual Environment Location
```
/Users/pmcgee/_dev/astronomical/astro-data-pipeline/venv/
```

### Python Version
```bash
$ ./venv/bin/python --version
Python 3.13.7
```

### Current Dependencies
```bash
# View installed packages
./venv/bin/pip list

# Key packages:
- requests (HTTP client)
- astropy (astronomical data processing)
- boto3 (AWS SDK)
```

---

## 🔧 Setup Instructions

### For New Developers

1. **Clone the repository:**
   ```bash
   git clone <repo-url>
   cd astro-data-pipeline
   ```

2. **Create virtual environment:**
   ```bash
   python3.13 -m venv venv
   ```

3. **Activate virtual environment:**
   ```bash
   source venv/bin/activate
   ```

4. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   # OR for development
   pip install -r requirements-dev.txt
   ```

5. **Verify installation:**
   ```bash
   which python  # Should show ./venv/bin/python
   python --version
   pip list
   ```

### For CI/CD (GitHub Actions)

```yaml
- name: Set up Python
  uses: actions/setup-python@v4
  with:
    python-version: '3.13'
    cache: 'pip'

- name: Install dependencies
  run: |
    python -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt
```

---

## 📝 Running Python Scripts

### Method 1: Direct Execution (Recommended)
```bash
./venv/bin/python scripts/test_catalog_evaluation.py --fits-file test.fits
```

### Method 2: Activated Shell
```bash
source venv/bin/activate
python scripts/test_catalog_evaluation.py --fits-file test.fits
deactivate  # When done
```

### Method 3: Shebang (Scripts Only)
Scripts in this project use a special shebang to automatically use venv:
```python
#!/usr/bin/env -S bash -c '"$(dirname "$0")/../venv/bin/python" "$0" "$@"'
```

Then make executable and run:
```bash
chmod +x scripts/test_catalog_evaluation.py
./scripts/test_catalog_evaluation.py --fits-file test.fits
```

---

## 📦 Managing Dependencies

### Adding New Dependencies

1. **Install to venv:**
   ```bash
   ./venv/bin/pip install new_package
   ```

2. **Update requirements:**
   ```bash
   ./venv/bin/pip freeze > requirements.txt
   ```

3. **Commit updated requirements:**
   ```bash
   git add requirements.txt
   git commit -m "Add new_package dependency"
   ```

### Updating Dependencies

```bash
# Update specific package
./venv/bin/pip install --upgrade package_name

# Update all packages (careful!)
./venv/bin/pip list --outdated
./venv/bin/pip install --upgrade package1 package2

# Regenerate requirements
./venv/bin/pip freeze > requirements.txt
```

### Removing Dependencies

```bash
# Uninstall package
./venv/bin/pip uninstall package_name

# Update requirements
./venv/bin/pip freeze > requirements.txt
```

---

## 🐛 Troubleshooting

### Issue: "ModuleNotFoundError"

**Cause:** Running with system Python instead of venv

**Solution:**
```bash
# Check which Python you're using
which python

# Should output: /Users/pmcgee/_dev/astronomical/astro-data-pipeline/venv/bin/python
# If not, use venv Python explicitly:
./venv/bin/python scripts/my_script.py
```

### Issue: "Permission denied installing packages"

**Cause:** Trying to install to system Python

**Solution:**
```bash
# DON'T use sudo or --break-system-packages
# DO use venv pip:
./venv/bin/pip install package_name
```

### Issue: "venv/ directory not found"

**Cause:** Virtual environment not created yet

**Solution:**
```bash
# Create venv
python3.13 -m venv venv

# Install dependencies
./venv/bin/pip install -r requirements.txt
```

### Issue: Different Python version in venv

**Cause:** venv created with different Python

**Solution:**
```bash
# Remove old venv
rm -rf venv

# Create new venv with correct Python
python3.13 -m venv venv

# Reinstall dependencies
./venv/bin/pip install -r requirements.txt
```

---

## 🔐 Security Considerations

### 1. **Never Commit Credentials**

Even in venv, avoid hardcoding:
```python
# ❌ DON'T
AWS_KEY = "AKIAIOSFODNN7EXAMPLE"

# ✅ DO
import os
AWS_KEY = os.environ.get('AWS_ACCESS_KEY_ID')
```

### 2. **Review Dependencies**

```bash
# Check for known vulnerabilities
./venv/bin/pip install safety
./venv/bin/safety check
```

### 3. **Pin Versions for Production**

```bash
# Development: flexible versions
requests>=2.32.0

# Production: exact versions
requests==2.32.5
```

---

## 📚 Additional Resources

- **Python venv docs**: https://docs.python.org/3/library/venv.html
- **pip documentation**: https://pip.pypa.io/en/stable/
- **Python packaging guide**: https://packaging.python.org/

---

## ✅ Checklist for Developers

- [ ] Virtual environment created (`venv/` exists)
- [ ] Using venv Python for all scripts (`./venv/bin/python`)
- [ ] Dependencies installed (`./venv/bin/pip install -r requirements.txt`)
- [ ] `venv/` is in `.gitignore`
- [ ] `requirements.txt` is committed and up-to-date
- [ ] Never use `--break-system-packages`
- [ ] Scripts use venv shebang or explicit venv Python path

---

## 🚨 Golden Rules

1. **ALWAYS** use `./venv/bin/python` and `./venv/bin/pip`
2. **NEVER** install packages to system Python
3. **ALWAYS** commit `requirements.txt` changes
4. **NEVER** commit `venv/` directory contents
5. **ALWAYS** document Python version requirements

---

**Last Updated:** $(date +%Y-%m-%d)
**Python Version:** 3.13.7
**Virtual Environment:** `/Users/pmcgee/_dev/astronomical/astro-data-pipeline/venv/`