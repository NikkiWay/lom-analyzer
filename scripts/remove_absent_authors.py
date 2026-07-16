"""
НАЗНАЧЕНИЕ
Скрипт очистки датасета examples/dataset_ai_v2.json от авторов, которых не должно
быть в выборке: тех, у кого нет реального присутствия во ВКонтакте или кто
заведомо не публикует там контент по теме (министры через пресс-службу,
иноагенты без VK и т. п.). Вместе с авторами каскадно удаляет их посты,
комментарии к их постам и комментарии, оставленные ими.

ЧТО ВНУТРИ
- REMOVE — справочник vkId -> причина удаления (для прозрачности решения).
- main() — печатает статистику до/после, выполняет каскадное удаление и
  проверяет отсутствие «осиротевших» ссылок, затем сохраняет датасет.

БИБЛИОТЕКИ
Только стандартная библиотека: json, pathlib.

СВЯЗИ
Читает и перезаписывает examples/dataset_ai_v2.json.
"""

import json
from pathlib import Path

DATASET_PATH = Path(__file__).parent.parent / "examples" / "dataset_ai_v2.json"

# Авторы на удаление (vkId -> причина)
REMOVE = {
    1067513: "Максут Шадаев — министр, общается через пресс-службу, нет личного VK",
    1191265: "Дмитрий Песков АСИ — спецпредставитель, конференции и официальные каналы",
    1119442: "Константин Воронцов — профессор РАН, не ведёт соцсети",
    1460813: "Борис Добродеев — ушёл из VK, сейчас в геймдеве, не постит про ИИ",
    1237518: "Максим Кац — принципиально не пользуется VK, иноагент",
    1273045: "Алексей Пивоваров — иноагент, нет VK, Telegram/YouTube only",
    1274112: "Ирина Шихман — иноагент, нет VK, YouTube/Telegram only",
}


def main():
    """Каскадно удаляет авторов из REMOVE с их постами и комментариями, сохраняя датасет."""
    with open(DATASET_PATH, "r", encoding="utf-8") as f:
        data = json.load(f)

    remove_ids = set(REMOVE.keys())  # множество vkId на удаление для быстрых проверок

    # Статистика до удаления
    print("=== BEFORE ===")
    print(f"  Authors: {len(data['authors'])}")
    print(f"  Posts: {len(data['posts'])}")
    print(f"  Comments: {len(data['comments'])}")

    # Показываем, кого удаляем (имя, подписчики, число постов и причина)
    print("\n=== REMOVING ===")
    for a in data["authors"]:
        if a["vkId"] in remove_ids:
            reason = REMOVE[a["vkId"]]
            posts_count = sum(1 for p in data["posts"] if p["fromId"] == a["vkId"])
            print(f"  {a['firstName']} {a['lastName']} ({a['followersCount']:,}) — {posts_count} posts — {reason}")

    # Собираем vkId удаляемых постов — понадобится для чистки комментариев к ним
    removed_post_vkids = set()
    for p in data["posts"]:
        if p["fromId"] in remove_ids:
            removed_post_vkids.add(p["vkId"])

    # Удаляем самих авторов
    data["authors"] = [a for a in data["authors"] if a["vkId"] not in remove_ids]

    # Удаляем их посты
    data["posts"] = [p for p in data["posts"] if p["fromId"] not in remove_ids]

    # Удаляем комментарии к их постам
    data["comments"] = [c for c in data["comments"] if c["postVkId"] not in removed_post_vkids]

    # А также комментарии, написанные этими авторами (они бы их тоже не оставляли)
    data["comments"] = [c for c in data["comments"] if c["fromId"] not in remove_ids]

    # Статистика после удаления
    print("\n=== AFTER ===")
    print(f"  Authors: {len(data['authors'])}")
    print(f"  Posts: {len(data['posts'])}")
    print(f"  Comments: {len(data['comments'])}")

    # Проверка целостности: не осталось ли «осиротевших» ссылок
    author_ids = {a["vkId"] for a in data["authors"]}
    post_vkids = {p["vkId"] for p in data["posts"]}
    orphan_posts = [p for p in data["posts"] if p["fromId"] not in author_ids]      # пост без автора
    orphan_comments = [c for c in data["comments"] if c["postVkId"] not in post_vkids]  # коммент без поста
    print(f"\n  Orphan posts (fromId not in authors): {len(orphan_posts)}")
    print(f"  Orphan comments (postVkId not in posts): {len(orphan_comments)}")

    # Сохраняем очищенный датасет поверх исходного файла
    with open(DATASET_PATH, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"\nSaved to {DATASET_PATH}")


if __name__ == "__main__":
    main()
