# R01 – Python Stack Verification Spike

Quick smoke-test that every Python library required by the LOM v6 NLP sidecar
can be imported and performs basic operations on Russian text.

## Libraries tested

| Library | Purpose |
|---------|---------|
| dostoevsky | Russian sentiment classification |
| natasha | Named-entity recognition (PER) |
| pymorphy3 | Morphological analysis & lemmatisation |
| langdetect | Language detection |
| sentence-transformers (`cointegrated/rubert-tiny2`) | Sentence embeddings & cosine similarity |

## Prerequisites

- **Python 3.12** (not 3.13+) — `fasttext` native module does not compile on
  Python 3.13 due to `ssize_t` C++ incompatibility with MSVC.
- **Microsoft C++ Build Tools** (with "Desktop development with C++" workload)
  — needed only if no prebuilt wheel is available for `fasttext-wheel`.

## How to run

```bash
# 1. Create venv with Python 3.12 specifically
py -3.12 -m venv .venv
# Windows
.venv\Scripts\activate
# Linux / macOS
source .venv/bin/activate

# 2. Install dependencies (order matters on Windows)
pip install --upgrade pip
pip install fasttext-wheel       # prebuilt native module
pip install dostoevsky --no-deps # avoid pulling broken fasttext==0.9.2
pip install natasha pymorphy3 langdetect sentence-transformers fastapi uvicorn

# 3. Download the dostoevsky FastText model (one-time)
python -m dostoevsky download fasttext-social-network-model

# 4. Run the verification script
python verify_python_stack.py
```

The script exits with code 0 if every check passes, or 1 if any check fails.

## Spike findings

### Python version constraint
`fasttext==0.9.2` (required by dostoevsky) **does not compile on Python 3.13**:
- The package's `setup.py` has a broken pybind11 bootstrap mechanism.
- Even with `fasttext-wheel` (clean build system), the C++ source uses
  `ssize_t` which is undefined on MSVC for Python 3.13 headers.
- **Resolution:** Pin the NLP sidecar to **Python 3.12**. Prebuilt
  `fasttext-wheel` wheels are available for cp312-win_amd64.

### fasttext installation on Windows
`dostoevsky` declares `fasttext==0.9.2` as a dependency, but that package's
`setup.py` is broken (tries to `pip install pybind11` from a subprocess that
can't find pip). **Workaround:** install `fasttext-wheel` first (provides the
same `fasttext` Python module), then install `dostoevsky --no-deps`.

### dostoevsky model hosting
The dostoevsky model is hosted at `storage.b-labs.pro`, which may be
unreachable (DNS failures, SSL errors). **Mitigation options for LOM:**
1. Bundle the model file (~120 MB) with the application.
2. Host a mirror and patch the download URL at runtime.
3. Fall back to `DictionarySentiment` (RuSentiLex) when model is unavailable
   (already planned in architecture as FALLBACK_ONLY mode).

### Other notes
- `cointegrated/rubert-tiny2` downloads ~120 MB from Hugging Face on first
  run; subsequent runs use the local cache.
- `langdetect` may misclassify very short Russian text as Macedonian (`mk`) —
  this is a known limitation for strings under ~20 characters.
- FastAPI and uvicorn are included in `requirements.txt` for completeness
  (they will be used by the NLP sidecar) but are not exercised here.
