# NLP Python Sidecar

FastAPI service providing NLP capabilities for the LOM Analyzer desktop application.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| POST | `/lemmatize` | Lemmatization via pymorphy3 |
| POST | `/language/detect` | Language detection via langdetect |
| POST | `/sentiment/dostoevsky` | Sentiment analysis via dostoevsky |
| POST | `/semantic_similarity` | Cosine similarity via rubert-tiny2 |
| POST | `/embed` | Text embedding via rubert-tiny2 |
| POST | `/ner/natasha` | NER via natasha |

All endpoints require `X-Auth-Token` header matching the `--secret` startup argument.

## Manual Run (for debugging)

```bash
cd nlp/python
python -m venv venv
source venv/bin/activate  # or venv\Scripts\activate on Windows
pip install -r requirements.txt
python main.py --port 8300 --secret mysecret
```

Then test:
```bash
curl -X POST http://127.0.0.1:8300/lemmatize \
  -H "X-Auth-Token: mysecret" \
  -H "Content-Type: application/json" \
  -d '{"text": "красивые дома стоят"}'
```
