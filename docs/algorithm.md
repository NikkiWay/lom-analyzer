# Алгоритм: связь кода с этапами 1–10

Связь кода с подразделом 2.1.1 диплома.

## 10 этапов

| Этап | Фаза | Код | Описание |
|---|---|---|---|
| 1 | Постановка | `ui/screens/SetupScreen.kt` | Форма: ключевые слова, период, фоновое окно |
| 2 | Сбор | `vk/CurrentCollector.kt`, `BaselineCollector.kt` | Тематические публикации по сообществам |
| 3 | Сбор | `vk/AuthorProfileCollector.kt` | Реестр авторов = все fromId из topic posts |
| 4 | Сбор | `vk/CommentCollector.kt` | Профили, фоновые, тематические, комментарии |
| 5 | Обработка | `preprocessing/PreprocessingExecutor.kt` | Очистка, лемматизация, язык |
| 6 | Обработка | `analysis/topic/TopicFilterExecutor.kt` | Двухпроходная фильтрация + сводка качества |
| 7 | Оценки | `analysis/scoring/ScoringExecutor.kt` | 11 оценок по 4 осям (Е.4) |
| 8 | Неопределённость | `analysis/inference/InferenceExecutor.kt` | Бутстрап: one-level + two-level (Е.3) |
| 9 | Классификация | `analysis/composite/CompositeRolesExecutor.kt` | Композиты → пороги → 4 роли + 2 атрибута |
| 10 | Качество | `analysis/quality/QualityCheckExecutor.kt` | Достаточность + 8 индикаторов качества |

## Контрольные точки

6 контрольных точек записываются в `pipeline_checkpoint` через `CheckpointService`:
- PHASE_1: после инициализации
- PHASE_2: после каждого шага сбора (baseline, current, authors, comments)
- PHASE_3: после обработки и фильтрации
- PHASE_4: после оценок и бутстрапа
- PHASE_5: после классификации и качества

## Оркестрация

`PipelineStage` (10 стадий) → `PipelineOrchestrator` → `PipelineWiring` (регистрация исполнителей).
