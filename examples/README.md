# Примеры

Материалы для запуска LOM Analyzer без обращения к VK API.

| Файл | Что это |
|------|---------|
| [session_setup.md](session_setup.md) | Пример заполнения полей сессии: n-граммы, референсные тексты, окна наблюдения |
| [dataset_demo.json](dataset_demo.json) | Пример JSON для импорта: сообщества, авторы, посты и комментарии по теме развития ИИ |

## dataset_demo.json

Демонстрационный набор: 5 сообществ, 91 автор, 1026 постов, 2660 комментариев.
Авторы и сообщества вымышлены, совпадения с существующими случайны.

Файл задаёт формат импорта — четыре массива верхнего уровня:

```json
{
  "communities": [ { "vkId": 194580895, "name": "Нейросети и ИИ",
                     "screenName": "neural_networks_ai", "membersCount": 342000,
                     "isClosed": false, "type": "group" } ],
  "authors":     [ { "vkId": 971354406, "firstName": "Вадим", "lastName": "Плетнёв",
                     "screenName": "vadim_pletnev", "followersCount": 12400,
                     "isClosed": false } ],
  "posts":       [ { "vkId": 10021, "ownerId": 971354406, "fromId": 971354406,
                     "date": 1779038434, "text": "...", "likes": 34, "reposts": 11,
                     "comments": 5, "views": 224, "containsMedia": true,
                     "hasCopyHistory": false, "window": "CURRENT" } ],
  "comments":    [ { "vkId": 50001, "postVkId": 10021, "postOwnerId": 971354406,
                     "fromId": 942788548, "date": 1779040000, "text": "...",
                     "likes": 3 } ]
}
```

Связи между сущностями:

- пост → автор: `posts[].fromId` совпадает с `authors[].vkId`;
- комментарий → пост: пара `postVkId` + `postOwnerId` совпадает с парой
  `vkId` + `ownerId` поста (одного `vkId` недостаточно — посты разных владельцев
  нумеруются независимо);
- сущности дедуплицируются по `vkId`: запись, уже имеющаяся в базе,
  переиспользуется, а не дублируется.

Поле `window` относит пост к фоновому (`BASELINE`) или текущему (`CURRENT`) окну
наблюдения. В демонстрационном наборе 527 постов baseline и 499 current.
Тональность считается только для окна `CURRENT`: фоновое окно задаёт базовый
уровень активности автора.

Метки времени `date` — Unix-секунды.

## Как использовать

Заполните поля по [session_setup.md](session_setup.md) и на экране «Настройка
сессии» импортируйте `dataset_demo.json`. Порядок прогона — в том же файле.
