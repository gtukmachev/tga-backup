# Task: Fix NFD-encoded Cyrillic filenames on exFAT drive

**Status:** IN PROGRESS — requires Windows/Linux to complete
**Created:** 2026-05-21
**Drive:** `/Volumes/Extreme SSD` (exFAT filesystem)
**Affected folder:** `Photo/_new/Lena-backup-whatsapp-2025-12-26/WhatsApp Documents/`

---

## Problem

59 files in the WhatsApp Documents folder have **NFD-decomposed Cyrillic filenames** (e.g., `й` stored as `и` + U+0306 COMBINING BREVE instead of single `й` U+0439).

macOS exFAT driver can **list** these files via `readdir()` but **cannot open, rename, or delete** them by path — all operations fail with `ENOENT (No such file or directory)`.

The backup program correctly handles this by marking them as `EXCLUDED FILES (due to read errors)` and continuing, but the files themselves need to be fixed so they can be backed up.

### Root cause

The files were originally created on Android (WhatsApp backup) which stored Cyrillic filenames in NFD form. macOS exFAT driver exposes them in directory listings but fails to resolve the NFD path for file operations.

### What was tried on macOS (all failed for these 59 files)

1. `os.rename()` with string paths — `ENOENT`
2. `os.rename()` with byte paths — `ENOENT`
3. Shell `mv` command with raw bytes — `ENOENT`
4. `os.remove()` — `ENOENT`

The exFAT driver on macOS normalizes path lookups differently from `readdir()`, making these files ghost entries.

---

## Action plan (on Windows or Linux)

1. Connect the `Extreme SSD` drive
2. Navigate to `Photo/_new/Lena-backup-whatsapp-2025-12-26/WhatsApp Documents/`
3. Run a script to rename all NFD files to NFC:

### Python script for Windows/Linux

```python
import os
import unicodedata

folder = r"E:\Photo\_new\Lena-backup-whatsapp-2025-12-26\WhatsApp Documents"  # adjust drive letter

fixed = 0
failed = 0
for entry in os.scandir(folder):
    nfc = unicodedata.normalize('NFC', entry.name)
    if nfc != entry.name:
        src = os.path.join(folder, entry.name)
        dst = os.path.join(folder, nfc)
        try:
            os.rename(src, dst)
            fixed += 1
            print(f"OK: {nfc}")
        except Exception as e:
            failed += 1
            print(f"FAIL: {nfc} — {e}")

print(f"\nDone: {fixed} fixed, {failed} failed")
```

4. After renaming, re-run the backup on macOS:
```bash
./backup photo --dry-run
```

5. Verify the 59 files no longer appear in `EXCLUDED FILES`

---

## List of all 59 affected files

```
1666354476707_затраты Алтай октябрь 22.pdf
20210428_Брошюра для родителей.pdf
2024-12-20_380_Приложение. Положение о порядке взаимодействия родителей обучающихся с педагогическими работниками.pdf
АКТУАЛЬНОЕ Пост100_НПА 019_Положение о стипендиях для одаренных детей в сфере образования,культуры и спорта — копия (1).docx
Билеты к смотру знаний.doc
Весенний учебный календарь для закрепления пройденного в 3 четверти (1 класс)..doc
Визерский Иван - Свидетельство о рождении.pdf
Визерский Иван.pdf
Визерский Иван.Квитанция по оплате.pdf
Встреча с нейропсихологом 16.02.2023 Word.docx
Выписка из поквартирной карточки Кольцово 17-96.pdf
Выписка из поквартирной карточки Кольцово 26-51.pdf
Договор на разработку дизайн-проекта интерьера помещения (с чертежами) - ФЛ в ред. 10.02.2021г..docx
Животные Красной книги.pdf
Загадки космической цивилизации 03.07-14.07.docx
Защитите_детей_от_новой_коронавирусной_инфекции.pdf
Зявка на двери - Океан Provance  - Тукмачёв.xls
Информационный плакат о проведении конкурсного отбора -- Информатика.Рег....pdf
Каникулы Базовый уровень.docx
Конкурс Мой родной Наукоград.pdf
Лёня - свидетельство о рождении.PDF
Множественный интеллект (рус) тест.pdf
Наряд Тукмачёва Елена.pdf
Не оставляйте детей одних у воды.pdf
Неделя_род_компет_Мероприятия_для_родителей.xlsx
О материальной помощи.docx
ПАСПОРТ-РФ-ТукмачёвГА-2018-11.pdf
Переписка с Полиной.pdf
Перечень мероприятий.docx
Письмо ОЦ Горностай УТЗ.pdf
Поддержка многодетных семей к началу учебного года.docx
Положение о требовании к школьной одежде 23.05.2022.docx
Полуфинал (1 раунд) всероссийских соревнований, Юноши 2008 г.р,Сезон 21-22.pdf
Постановление_об_опекунстве_Визерский_вычитанный.docx
Прайс10.04.22docx.pdf
Приложение № 3 (очный отбор).docx
Расписание Полуфинал (2 раунд) Всероссийских соревнований, Юноши 2008 г.р., Сезон 21-22.xls
Свидетельство о рождении Тукмачёв ВГ.pdf
Серенада Шуберт с транскрипцией.pdf
Список литературы для родителей.docx
Справка об обучении_Визерский.pdf
Сценарий для скоморохов-2017.doc
Тест для родителей.docx
Тукмачёв Андрей - согласие опд.pdf
Тукмачёв Андрей - сопд (1).pdf
Тукмачёва ЕА - Паспорт.pdf
Фоменко_В последний час декабря.pdf
Чек-лист Самостоятельный школьник.pdf
Школьный этап.pdf
ЮНЫЙ СПОРТСМЕН без тел..pdf
безопасность в сети памятка для детей (1).docx
безопасность в сети памятка для родителей.docx
памятка для родителей (1).docx
памятка для родителей.docx
расписания лекций осн.pdf
рейтинговая 1 до 30 сентября (1).docx
рейтинговая до 20 март.docx
рейтинговая до 25 февраля.docx
рейтинговая до 30 нобяря и 25 декабря.docx
```
