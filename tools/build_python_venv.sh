#!/bin/bash
# Build bundled Python environment for LOM Analyzer
# Creates <dist>/python_env/ with all NLP dependencies
# Expected size: ~700 MB

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DIST_DIR="${PROJECT_ROOT}/dist"
PYTHON_ENV="${DIST_DIR}/python_env"
REQUIREMENTS="${PROJECT_ROOT}/nlp/python/requirements.txt"

echo "=== LOM Analyzer Python Environment Builder ==="
echo "Output: ${PYTHON_ENV}"

# Create venv
python3.12 -m venv "${PYTHON_ENV}"

# Activate and install
if [[ "$OSTYPE" == "msys" ]] || [[ "$OSTYPE" == "win32" ]]; then
    source "${PYTHON_ENV}/Scripts/activate"
else
    source "${PYTHON_ENV}/bin/activate"
fi

pip install --upgrade pip
pip install -r "${REQUIREMENTS}"

# Verify
python -c "import pymorphy3; import dostoevsky; import natasha; print('All NLP packages OK')"

# Report size
echo "=== Build complete ==="
du -sh "${PYTHON_ENV}" 2>/dev/null || echo "Size check: use 'du -sh dist/python_env/'"
echo "Distribution mode: BUNDLED"
