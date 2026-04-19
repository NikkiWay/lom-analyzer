# LOM Analyzer — User Manual v1.0

## Installation

### Bundled Edition (~700 MB)
1. Download `LomAnalyzer-1.0.0-bundled-{os}.{msi|dmg|deb}`
2. Run the installer
3. Python NLP environment is pre-installed — no additional setup needed

### Bootstrap Edition (~100 MB)
1. Download `LomAnalyzer-1.0.0-bootstrap-{os}.{msi|dmg|deb}`
2. Run the installer
3. On first launch, the app will download and install Python NLP components (~600 MB)
4. Requires internet connection for initial setup

### Prerequisites
- JDK 17+ (bundled with installer)
- Python 3.12 (bundled or bootstrap)
- VK API developer application (see VK API Setup below)

## First Launch

### Master Password
On first launch, you will be asked to create a master password. This password:
- Encrypts your VK API token using AES-256-GCM
- Is required every time you start the application
- Cannot be recovered if lost — you will need to re-authorize with VK

### VK API Authorization
1. Go to [VK Apps](https://vk.com/apps?act=manage) and create a Standalone app
2. Note the **App ID** (client_id)
3. In the app, enter your App ID when prompted
4. Authorize via the embedded browser
5. The token is encrypted and stored locally

### Resource Validation
The app verifies SHA-256 checksums of reference files on startup:
- `reference_base.json` — reference population statistics
- `holidays.json` — Russian public holidays
- `sentilex_base.json` — sentiment dictionary

## Creating an Analysis Session

### Step 1: Session Setup
- **Session name**: descriptive label
- **Topic query**: the subject being analyzed (e.g., "ecology", "urban development")
- **Primary n-grams**: core topic keywords (comma-separated)
- **Secondary n-grams**: related keywords (lower weight)
- **Excluded n-grams**: false-positive phrases to exclude
- **Reference texts**: 3-10 example texts for semantic scoring (FULL mode only)
- **Baseline window**: 60 days default (30-180)
- **Current window**: 30 days default

### Step 2: Data Collection
The app collects VK posts from specified communities and authors:
- Baseline window: historical context
- Current window: analysis period
- Reposters: for posts with 0-200 reposts
- Discovery: up to 30 new authors found via high-reach posts

### Step 3: Topic Validation
Review 70 sampled posts (30 stratified + 40 random):
- Vote Yes/No/Unsure on topic relevance
- Monitor Bayesian precision/recall with 95% CI
- Adjust threshold if needed
- Add stop-phrases to reduce false positives

### Step 4: Analysis
Automated pipeline runs 35 stages including:
- Preprocessing, deduplication, gamma calibration
- Base and event influence scoring with bootstrap CI
- Reference calibration and role classification
- Anomaly detection and risk scoring
- Persona aggregation

## Interpreting Results

### Scatter Plot
- **X-axis**: I_base (structural influence)
- **Y-axis**: I_event (event-driven activity)
- **Color**: combined role (8 distinct colors)
- **Size**: proportional to topical post count
- **Dashed lines**: session thresholds (median)
- **Dotted lines**: reference thresholds

### Role Matrix (4x2)
| Session Role | High Reference Base | Low Reference Base |
|---|---|---|
| High base + High event | AUTH_LOM_CONFIRMED | AUTH_LOM_LOCAL |
| High base + Low event | SLEEPING_GIANT_CONFIRMED | SLEEPING_GIANT_LOCAL |
| Low base + High event | TOPIC_DRIVER_WITH_BASE | TOPIC_DRIVER |
| Low base + Low event | BACKGROUND_LARGE | BACKGROUND |

### Risk Signals
- **HIGH** (R >= 0.55): immediate attention required
- **MEDIUM** (0.35-0.55): monitor closely
- **LOW** (0.15-0.35): awareness level
- **MINIMAL** (< 0.15): no action needed
- **BORDERLINE**: CI crosses a boundary — interpret with caution

### Confidence
- **High** (>= 0.60): reliable classification
- **Medium** (0.25-0.60): use with context
- **Low** (< 0.25): auto-recommendations blocked

## Export

### Privacy-First (Default)
- Author IDs hashed with SHA-256 + session salt
- Names not included
- Suitable for reports and sharing

### Raw Export
- Requires explicit confirmation dialog
- Includes original VK IDs and names
- For internal analytical use only

## Troubleshooting

### Python Sidecar Failure
If the Python NLP service fails after 3 auto-restart attempts:
- **Wait and retry**: attempts restart again
- **Switch to FALLBACK**: uses Snowball stemmer + dictionary sentiment (no embeddings)
- **Cancel session**: marks session as CANCELLED

### VK API Errors
- **Rate limit (429)**: automatic exponential backoff, max 5 retries
- **Token expired**: re-authorize via Settings > VK Token
- **Closed profiles**: imputed at Q25 with ESTIMATED_CONSERVATIVE flag

### Low Session Quality Score
- Check which SQS components are failing (hard gates marked with [GATE])
- Common issues: insufficient baseline data, high CV_IQR, low confidence distribution

## Legal Notes

- All data is stored locally on the user's device
- VK API token encrypted at rest (AES-256-GCM, PBKDF2 100k iterations)
- PII masked in logs via PiiSafeFormatter
- Retention: soft-delete after 12 months, hard-delete after 30-day grace
- Right to be forgotten: cascade delete by author ID
- Compliant with 152-ФЗ data locality requirements
