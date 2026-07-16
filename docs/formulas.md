# Формулы: соответствие классов и Приложения Е

| Формула | Раздел Е | Класс в коде | Метод |
|---|---|---|---|
| Медиана | Е.1 | `RobustStats` | `median(values)` |
| IQR (тип 7) | Е.1 | `RobustStats` | `iqr(values)` |
| MAD (c=1.4826) | Е.2 | `RobustStats` | `mad(values)` |
| M-оценка Хьюбера (k=1.345) | Е.2 | `RobustStats` | `huberMEstimate(values, k)` |
| Одноуровневый бутстрап (B=1000) | Е.3.1 | `OneLevelBootstrap` | `bootstrap(values, statistic)` |
| Двухуровневый бутстрап (300×100) | Е.3.2 | `TwoLevelBootstrap` | `bootstrap(clusters)` |
| Aud_a = ln(1+F_a) | Е.4.1 | `StructuralScores` | `aud(followers)` |
| Age_a = (d−d_created)/max | Е.4.1 | `StructuralScores` | `age(days, maxDays)` |
| ER^bg_a = (1/\|B\|)Σ(L+C+R)/F | Е.4.1 | `StructuralScores` | `erBg(reactions, followers)` |
| TopVol_a = \|T_a\| | Е.4.2 | `TopicScores` | `topVol(count)` |
| TopFocus_a = \|T\|/(\|T\|+\|B^period\|) | Е.4.2 | `TopicScores` | `topFocus(topic, nonTopic)` |
| Reach_a = Σ V_i | Е.4.2 | `TopicScores` | `reach(views)` |
| Pos_a = (p+, p0, p−) | Е.4.3 | `PositionScore` | `pos(sentiments)` |
| ER^top_a = (1/\|T\|)Σ(L+C+R)/F | Е.4.4 | `ResponseScores` | `erTop(reactions, followers)` |
| Resp_a = (q+, q0, q−) | Е.4.4 | `ResponseScores` | `resp(commentSentiments)` |
| z(x) = (x−med)/IQR | Е.4.6 | `RobustZScore` | `normalize(values)` |
| Struct_a = (1/3)(z(Aud)+z(ER_bg)+z(Age)) | Е.4.6 | `CompositeScorer` | `structuralComposite(...)` |
| Topic_a = (1/3)(z(TopVol)+z(TopFocus)+z(Reach)) | Е.4.6 | `CompositeScorer` | `topicComposite(...)` |
| θ_Struct = med(Struct_a) | Е.4.6 | `CompositeScorer` | `adaptiveThresholds(...)` |
| θ_Topic = med(Topic_a) | Е.4.6 | `CompositeScorer` | `adaptiveThresholds(...)` |

## Параметры (фиксированы в дипломе)

| Параметр | Значение | Источник |
|---|---|---|
| k (Хьюбер) | 1.345 | Е.2, [75, 76] |
| c (MAD) | 1.4826 | Е.2 |
| Сходимость IRLS | 10⁻⁶ | Е.2 |
| B (one-level) | 1000 | Е.3.1 |
| B_outer | 300 | Е.3.2 |
| B_inner | 100 | Е.3.2 |
| Уровень доверия | 95% (2.5/97.5) | Е.3.3 |
| Веса композита | 1/3, 1/3, 1/3 | Е.4.6, OECD [114] |
