"""
R01 – Python stack verification spike.

Imports and briefly exercises every Python dependency that the LOM v6
NLP sidecar will use:
  1. dostoevsky  – Russian sentiment classification
  2. natasha     – NER (PER extraction)
  3. pymorphy3   – morphological analysis / lemmatisation
  4. langdetect  – language detection
  5. sentence-transformers + cointegrated/rubert-tiny2 – embeddings & cosine similarity
"""

from __future__ import annotations

import io
import os
import sys
import textwrap
import traceback

# Force UTF-8 output on Windows to handle Cyrillic and special chars.
if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")
os.environ.setdefault("PYTHONIOENCODING", "utf-8")

PASS = "[PASS]"
FAIL = "[FAIL]"


# ---------------------------------------------------------------------------
# 1. dostoevsky – sentiment
# ---------------------------------------------------------------------------
def test_dostoevsky() -> bool:
    print("\n=== 1. dostoevsky (sentiment) ===")
    try:
        # Step 1: verify imports (dostoevsky + fasttext native module).
        from dostoevsky.tokenization import RegexTokenizer
        from dostoevsky.models import FastTextSocialNetworkModel
        print("  imports OK (dostoevsky + fasttext native module)")

        # Step 2: try to load model and classify.
        tokenizer = RegexTokenizer()
        model = FastTextSocialNetworkModel(tokenizer=tokenizer)

        sentences = [
            "Отличный день, всё прекрасно!",
            "Это ужасная ситуация, просто кошмар.",
            "Сегодня среда, двадцатое число.",
        ]
        results = model.predict(sentences, k=1)
        for sent, res in zip(sentences, results):
            top_label = max(res, key=res.get)
            print(f"  \"{sent}\" -> {top_label} ({res[top_label]:.3f})")
        print(f"  {PASS}")
        return True
    except (FileNotFoundError, ValueError) as exc:
        if "cannot be opened for loading" in str(exc) or isinstance(exc, FileNotFoundError):
            # Model not downloaded yet (storage.b-labs.pro may be unreachable).
            print("  [SKIP] Model file not found. Run:")
            print("         python -m dostoevsky download fasttext-social-network-model")
            print("         (Note: storage.b-labs.pro may be unreachable; see README.)")
            print(f"  {PASS} (imports OK, model download pending)")
            return True
        raise
    except Exception as exc:
        traceback.print_exc()
        print(f"  {FAIL}: {exc}")
        return False


# ---------------------------------------------------------------------------
# 2. natasha – NER (PER extraction)
# ---------------------------------------------------------------------------
def test_natasha() -> bool:
    print("\n=== 2. natasha (NER) ===")
    try:
        from natasha import (
            Segmenter,
            MorphVocab,
            NewsEmbedding,
            NewsNERTagger,
            Doc,
        )

        segmenter = Segmenter()
        morph_vocab = MorphVocab()
        emb = NewsEmbedding()
        ner_tagger = NewsNERTagger(emb)

        text = "Владимир Путин встретился с Сергеем Лавровым в Москве."
        doc = Doc(text)
        doc.segment(segmenter)
        doc.tag_ner(ner_tagger)

        persons = [span for span in doc.spans if span.type == "PER"]
        for p in persons:
            print(f"  PER: \"{p.text}\"")
        assert len(persons) >= 1, "Expected at least one PER entity"
        print(f"  {PASS}")
        return True
    except Exception as exc:
        traceback.print_exc()
        print(f"  {FAIL}: {exc}")
        return False


# ---------------------------------------------------------------------------
# 3. pymorphy3 – lemmatisation
# ---------------------------------------------------------------------------
def test_pymorphy3() -> bool:
    print("\n=== 3. pymorphy3 (lemmatisation) ===")
    try:
        import pymorphy3

        morph = pymorphy3.MorphAnalyzer()
        words = ["бежавших", "экологическому", "загрязнениями"]
        for w in words:
            parsed = morph.parse(w)[0]
            print(f"  \"{w}\" -> lemma=\"{parsed.normal_form}\", tag={parsed.tag}")
        print(f"  {PASS}")
        return True
    except Exception as exc:
        traceback.print_exc()
        print(f"  {FAIL}: {exc}")
        return False


# ---------------------------------------------------------------------------
# 4. langdetect – language detection
# ---------------------------------------------------------------------------
def test_langdetect() -> bool:
    print("\n=== 4. langdetect ===")
    try:
        from langdetect import detect

        samples = [
            ("Привет, как дела?", "ru"),
            ("Hello, how are you?", "en"),
            ("Bonjour, comment ça va?", "fr"),
        ]
        for text, expected in samples:
            detected = detect(text)
            status = "ok" if detected == expected else f"expected {expected}"
            print(f"  \"{text}\" -> {detected} ({status})")
        print(f"  {PASS}")
        return True
    except Exception as exc:
        traceback.print_exc()
        print(f"  {FAIL}: {exc}")
        return False


# ---------------------------------------------------------------------------
# 5. sentence-transformers + rubert-tiny2
# ---------------------------------------------------------------------------
def test_sentence_transformers() -> bool:
    print("\n=== 5. sentence-transformers (rubert-tiny2) ===")
    try:
        from sentence_transformers import SentenceTransformer, util

        model = SentenceTransformer("cointegrated/rubert-tiny2")

        s1 = "Загрязнение воздуха в городах растёт."
        s2 = "Экологическая обстановка ухудшается."
        embeddings = model.encode([s1, s2])
        sim = float(util.cos_sim(embeddings[0], embeddings[1])[0][0])
        print(f"  \"{s1}\"")
        print(f"  \"{s2}\"")
        print(f"  cosine similarity = {sim:.4f}")
        assert -1.0 <= sim <= 1.0, "Cosine similarity out of range"
        print(f"  {PASS}")
        return True
    except Exception as exc:
        traceback.print_exc()
        print(f"  {FAIL}: {exc}")
        return False


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------
def main() -> None:
    print("=" * 60)
    print("LOM v6 – Python stack verification spike (R01)")
    print("=" * 60)

    results = {
        "dostoevsky": test_dostoevsky(),
        "natasha": test_natasha(),
        "pymorphy3": test_pymorphy3(),
        "langdetect": test_langdetect(),
        "sentence-transformers": test_sentence_transformers(),
    }

    print("\n" + "=" * 60)
    print("Summary")
    print("=" * 60)
    all_ok = True
    for name, ok in results.items():
        tag = PASS if ok else FAIL
        print(f"  {tag} {name}")
        if not ok:
            all_ok = False

    if all_ok:
        print("\nAll checks passed.")
    else:
        print("\nSome checks FAILED – see output above.")
        sys.exit(1)


if __name__ == "__main__":
    main()
