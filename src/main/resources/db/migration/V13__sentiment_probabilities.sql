-- ============================================================
-- V13: sentiment_result — распределение вероятностей по классам
-- ============================================================
--
-- ЗАЧЕМ
-- Модель тональности возвращает вероятности всех трёх классов. Одна метка-победитель
-- их не заменяет: у сдержанного текста распределение neutral 0.80 / positive 0.15 /
-- negative 0.05 сводится к «neutral», и склонность к позитиву в метке не выражена.
--
-- От этого зависит чувствительность осей позиции автора (Pos_a) и отклика аудитории
-- (Resp_a): по меткам у автора с одним тематическим постом Pos_a равен ровно
-- (0, 1, 0) — вырожденное распределение без перевеса. По вероятностям перевес
-- сохраняется (см. PositionScore.authorPositionFromProbabilities).
--
-- Колонки заполняет только модель sidecar. Словарный fallback распределения не
-- даёт: у него метка либо есть, либо нет, поэтому там остаётся NULL, и расчёт для
-- таких строк идёт по долям меток.

ALTER TABLE sentiment_result ADD COLUMN prob_positive REAL;
ALTER TABLE sentiment_result ADD COLUMN prob_neutral  REAL;
ALTER TABLE sentiment_result ADD COLUMN prob_negative REAL;

-- Те же колонки в кэше NLP: без них попадание в кэш возвращало бы результат без
-- распределения, и повторный прогон той же сессии откатывался бы к расчёту по
-- меткам — то есть исправление работало бы ровно один раз.
ALTER TABLE nlp_result ADD COLUMN prob_positive REAL;
ALTER TABLE nlp_result ADD COLUMN prob_neutral  REAL;
ALTER TABLE nlp_result ADD COLUMN prob_negative REAL;
