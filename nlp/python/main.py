"""
LOM Analyzer — Python NLP Sidecar (FastAPI + uvicorn).

Exposes NLP endpoints for the Kotlin desktop application.
Each endpoint requires X-Auth-Token header matching the --secret arg.

Usage:
    python main.py --port 8300 --secret <hex> [--model-cache-dir ./models]
"""
import argparse
import functools
import os
import sys
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel

# ---------------------------------------------------------------------------
# CLI args
# ---------------------------------------------------------------------------
parser = argparse.ArgumentParser()
parser.add_argument("--port", type=int, default=8300)
parser.add_argument("--secret", type=str, required=True)
parser.add_argument("--model-cache-dir", type=str, default="./models")
args, _unknown = parser.parse_known_args()

SHARED_SECRET = args.secret
os.environ["SENTENCE_TRANSFORMERS_HOME"] = args.model_cache_dir

# ---------------------------------------------------------------------------
# Lazy model loading
# ---------------------------------------------------------------------------
_models = {}


def get_dostoevsky():
    if "dostoevsky" not in _models:
        from dostoevsky.tokenization import RegexTokenizer
        from dostoevsky.models import FastTextSocialNetworkModel
        _models["dostoevsky"] = FastTextSocialNetworkModel(tokenizer=RegexTokenizer())
    return _models["dostoevsky"]


def get_morph():
    if "morph" not in _models:
        import pymorphy3
        _models["morph"] = pymorphy3.MorphAnalyzer()
    return _models["morph"]


def get_natasha():
    if "natasha" not in _models:
        from natasha import (Segmenter, MorphVocab, NewsEmbedding,
                             NewsNERTagger, NamesExtractor, Doc)
        segmenter = Segmenter()
        morph_vocab = MorphVocab()
        emb = NewsEmbedding()
        ner_tagger = NewsNERTagger(emb)
        _models["natasha"] = {
            "segmenter": segmenter,
            "morph_vocab": morph_vocab,
            "ner_tagger": ner_tagger,
        }
    return _models["natasha"]


def get_embedder():
    if "embedder" not in _models:
        from sentence_transformers import SentenceTransformer
        _models["embedder"] = SentenceTransformer("cointegrated/rubert-tiny2")
    return _models["embedder"]


def get_langdetect():
    if "langdetect" not in _models:
        import langdetect
        _models["langdetect"] = langdetect
    return _models["langdetect"]


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------
def verify_token(x_auth_token: str = Header(...)):
    if x_auth_token != SHARED_SECRET:
        raise HTTPException(status_code=403, detail="Invalid token")


# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------
app = FastAPI(title="LOM NLP Sidecar")


# ---------------------------------------------------------------------------
# Request / response models
# ---------------------------------------------------------------------------
class TextRequest(BaseModel):
    text: str


class SentimentRequest(BaseModel):
    text: str
    mode: str = "dostoevsky"


class SimilarityRequest(BaseModel):
    a: str
    b: str


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.get("/health")
async def health(x_auth_token: str = Header(...)):
    verify_token(x_auth_token)
    return {"status": "ok"}


@app.post("/lemmatize")
async def lemmatize(req: TextRequest, x_auth_token: str = Header(...)):
    verify_token(x_auth_token)
    morph = get_morph()
    words = req.text.split()
    lemmas = [morph.parse(w)[0].normal_form for w in words]
    return {"lemmas": lemmas}


@app.post("/language/detect")
async def detect_language(req: TextRequest, x_auth_token: str = Header(...)):
    verify_token(x_auth_token)
    ld = get_langdetect()
    try:
        results = ld.detect_langs(req.text)
        top = results[0]
        return {"language": top.lang, "confidence": round(top.prob, 4)}
    except Exception:
        return {"language": "unknown", "confidence": 0.0}


@app.post("/sentiment/dostoevsky")
async def sentiment(req: SentimentRequest, x_auth_token: str = Header(...)):
    verify_token(x_auth_token)
    model = get_dostoevsky()
    results = model.predict([req.text], k=1)
    label = max(results[0], key=results[0].get)
    score = results[0][label]
    return {"label": label, "score": round(score, 4)}


@app.post("/semantic_similarity")
async def semantic_similarity(req: SimilarityRequest, x_auth_token: str = Header(...)):
    verify_token(x_auth_token)
    embedder = get_embedder()
    from sentence_transformers.util import cos_sim
    emb_a = embedder.encode(req.a)
    emb_b = embedder.encode(req.b)
    sim = float(cos_sim(emb_a, emb_b)[0][0])
    return {"similarity": round(sim, 4)}


@app.post("/embed")
async def embed(req: TextRequest, x_auth_token: str = Header(...)):
    verify_token(x_auth_token)
    embedder = get_embedder()
    vector = embedder.encode(req.text).tolist()
    return {"vector": vector}


@app.post("/ner/natasha")
async def ner(req: TextRequest, x_auth_token: str = Header(...)):
    verify_token(x_auth_token)
    from natasha import Doc, Segmenter
    n = get_natasha()
    doc = Doc(req.text)
    doc.segment(n["segmenter"])
    doc.tag_ner(n["ner_tagger"])
    entities = []
    for span in doc.ner.spans:
        entities.append({
            "text": span.text,
            "type": span.type,
            "start": span.start,
            "end": span.stop,
        })
    return {"entities": entities}


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=args.port, workers=1)
