ALTER TABLE analysis_session ADD COLUMN primary_ngrams TEXT;
ALTER TABLE analysis_session ADD COLUMN secondary_ngrams TEXT;
ALTER TABLE analysis_session ADD COLUMN excluded_ngrams TEXT;
ALTER TABLE analysis_session ADD COLUMN reference_texts TEXT;
