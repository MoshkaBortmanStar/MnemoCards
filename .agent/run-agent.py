#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenSpec Change Driver — CLI-движок пайплайна Spec-Driven Change Development.

Конвейер: MAIN Spec -> Промпт -> Proposal -> BDD Spec (GIVEN-WHEN-THEN) -> Tasks -> Code

Основные команды:

    python .agent/run-agent.py --main "что за приложение и какой стек"
    python .agent/run-agent.py --new "хочу эхо-сервис с кодированием в base64"
    python .agent/run-agent.py --list
    python .agent/run-agent.py --status --change change-001-base64
    python .agent/run-agent.py --codegen --change change-001-base64
    python .agent/run-agent.py --offline --new "..."     # без LLM, каркасы по шаблонам

Полная документация: README.md в корне проекта.
"""

import argparse
import copy
import datetime
import json
import re
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional

import yaml

# ---------------------------------------------------------------------------
# Пути (скрипт лежит в .agent/, корень проекта — на уровень выше)
# ---------------------------------------------------------------------------

AGENT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = AGENT_DIR.parent
TEMPLATES_DIR = AGENT_DIR / "templates"
SPECS_DIR = PROJECT_ROOT / "specs"
CONFIG_PATH = AGENT_DIR / "config.yaml"
SCHEMA_PATH = AGENT_DIR / "schema.yaml"

# ---------------------------------------------------------------------------
# Вывод в терминал
# ---------------------------------------------------------------------------

_USE_COLOR = sys.stdout.isatty()


def _c(code: str, text: str) -> str:
    return "\033[{}m{}\033[0m".format(code, text) if _USE_COLOR else text


def info(text: str) -> None:
    print(_c("36", text))


def ok(text: str) -> None:
    print(_c("32", "  ✔ " + text))


def warn(text: str) -> None:
    print(_c("33", "  ⚠ " + text))


def err(text: str) -> None:
    print(_c("31", "  ✖ " + text))


def banner(text: str) -> None:
    print("\n" + _c("1;36", "=" * 62))
    print(_c("1;36", text))
    print(_c("1;36", "=" * 62))


def fail(text: str) -> None:
    """Завершить работу с понятным сообщением."""
    err(text)
    sys.exit(1)


# ---------------------------------------------------------------------------
# Системные промпты LLM (по ТЗ, раздел 3)
# ---------------------------------------------------------------------------

SYSTEM_PROMPTS = {
    "main": """\
Ты — AI Архитектор в режиме OpenSpec Change Driver. Пользователь описывает
свой проект (что за приложение, зачем, на чём написано). Твоя задача —
сформировать мейн-спеку проекта MAIN.md — единый первичный документ,
который описывает приложение в целом:
1. Секции: What Is This App / Tech Stack / Architecture / Capabilities /
   Conventions.
2. Стек согласовывай с конфигурацией проекта (передана ниже), а детали
   домена бери из описания пользователя.
3. Capabilities — уже существующие возможности приложения; если проект
   новый, перечисли стартовые или оставь заготовку.
4. Пиши коротко и фактологично — это справочник, а не маркетинг.
Если передана текущая версия MAIN.md — обнови её с учётом новых вводных,
сохранив структуру секций.
Придерживайся структуры шаблона (передан ниже).
Верни ТОЛЬКО итоговый markdown-документ: без пояснений и без обёртки
в код-блоки.""",

    "proposal": """\
Ты — AI Архитектор в режиме OpenSpec Change Driver. К тебе поступает сырой
промпт пользователя на добавление фичи. Твоя задача — сгенерировать документ
proposal.md:
1. Строго следуй правилам из схемы артефакта (переданы ниже).
2. Структура секций: Why / What Changes / New Capabilities.
3. Максимум 100 слов. Фокус на "Зачем", а не "Как".
Придерживайся структуры шаблона (передан ниже), заполнив секции содержанием.
Верни ТОЛЬКО итоговый markdown-документ: без пояснений и без обёртки
в код-блоки.""",

    "spec-bdd": """\
Ты — AI Архитектор в режиме OpenSpec Change Driver. На основе proposal.md
(передан ниже) разложи фичу на строгие BDD-сценарии:
1. Каждый сценарий — в формате:
   * GIVEN (предусловия, например: 'Сервер запущен, на вход подан текст "hello"')
   * WHEN (действие, например: 'Выполняется GET запрос на /base64?text=hello')
   * THEN (результат, например: 'Статус ответа 200, тело ответа равно "aGVsbG8="')
2. Каждому сценарию присвой ID: S1, S2, ...
3. Опиши все краевые случаи: валидация входных данных, ошибки 400, 404, 500.
4. Добавь секцию OpenAPI Delta: изменения контракта (paths, schemas) в YAML.
Придерживайся структуры шаблона (передан ниже).
Верни ТОЛЬКО итоговый markdown-документ: без пояснений и без обёртки
в код-блоки.""",

    "tasks": """\
На основе созданных файлов proposal.md и spec-bdd.md (переданы ниже)
сформируй план реализации tasks.md. Разбей работу на микро-задачи:
1. Максимальный размер задачи — 2 часа.
2. Первой задачей всегда идёт генерация/запуск падающего теста (TDD).
3. Каждая задача — чек-бокс, ID связанных сценариев (S1, S2...),
   список затрагиваемых файлов.
4. План должен быть понятен для ИИ-кодера без дополнительных пояснений.
Придерживайся структуры шаблона (передан ниже).
Верни ТОЛЬКО итоговый markdown-документ: без пояснений и без обёртки
в код-блоки.""",

    "codegen": """\
ГЛАВНОЕ ПРАВИЛО, действует поверх всего остального: если файл уже существует —
отвечай ТОЛЬКО блоками <<<<<<< SEARCH / ======= / >>>>>>> REPLACE. Выводить
существующий файл целиком ЗАПРЕЩЕНО. Полное содержимое — только для файлов,
которых ещё нет. Формат блоков описан ниже.

Ты — Senior-разработчик, работающий в стеке этого проекта. Твоя задача —
написать код, строго соответствующий спецификации spec-bdd.md и плану
tasks.md (переданы ниже).

Контекст проекта:
Стек: {tech_stack}
Код-стайл: {code_style}
Базовый пакет / неймспейс: {base_package}
Корень исходников: {source_root}

Порядок работы:
1. Сначала сгенерируй тесты, воспроизводящие сценарии GIVEN-WHEN-THEN
   из спецификации. Тестовый фреймворк бери из стека проекта.
2. Затем сгенерируй реализацию, чтобы тесты прошли успешно.
3. Соблюдай код-стайл проекта. Не сокращай код — выводи файлы целиком.

ПЕРЕПИСЫВАЕШЬ СУЩЕСТВУЮЩИЙ ФАЙЛ — СОХРАНЯЙ ТО, ЧТО В НЁМ УЖЕ ЕСТЬ.
Менять можно только то, что требует задача. Всё остальное переноси как было:
оформление и стили, подключение темы, подобранные цвета и отступы, порядок
и расположение элементов, обработку краевых случаев, комментарии.
Особенно это касается решений, которые пользователь принял вручную и которые
задача не затрагивает, — их нельзя заменять значениями по умолчанию, даже
если дефолт выглядит «чище». Если для новых данных нужен другой каркас —
перенеси в него прежнее оформление целиком, а не пиши экран заново с нуля.
Не оставляй после себя осиротевшие компоненты: перестал использовать
существующий файл — перенеси его содержимое или используй его, а не дублируй
логику на месте.

ВАЖНО: язык, расширения файлов, тестовые библиотеки и структуру каталогов
определяй ИСКЛЮЧИТЕЛЬНО по стеку проекта, указанному выше. Не переноси
соглашения из других экосистем и не подставляй язык по умолчанию.

ЗАВИСИМОСТИ. Ниже переданы файлы сборки проекта с их актуальным содержимым.
Если задача требует библиотеку, которой там нет, — добавь её сам: выведи
изменённый файл сборки ЦЕЛИКОМ отдельным блоком ### FILE, наравне с кодом.
Версию объявляй тем же механизмом, который уже используется в этом проекте
(каталог версий, properties, dependencyManagement) — не хардкодь версию
в месте подключения, если рядом так не делают. Не добавляй библиотеки,
которых задача не требует.

ПРАВИЛО ПРАВКИ ФАЙЛОВ СБОРКИ — соблюдай строго. Файл выводится целиком, но
менять в нём можно ТОЛЬКО то, что требует задача:
- существующие строки переноси ДОСЛОВНО, символ в символ: имена артефактов,
  версии, порядок, отступы, комментарии;
- ничего не переименовывай, не сокращай и не «причёсывай» по дороге;
- новые записи добавляй рядом с однотипными, по образцу соседней строки.
Имена артефактов полные и точные: у многих библиотек имя повторяет префикс
группы (например androidx.lifecycle:lifecycle-runtime-ktx, а не runtime-ktx).
Сокращение такого префикса ломает сборку, а синтаксически файл выглядит
правильным — проверяй по соседним строкам того же файла.

ФОРМАТ ОТВЕТА. Два вида блоков, выбирай по ситуации.

НОВЫЙ файл — полное содержимое:

### FILE: <относительный/путь/Файл.расширение>
```язык
<полное содержимое файла>
```

СУЩЕСТВУЮЩИЙ файл — ТОЛЬКО точечные правки, блоками поиска и замены:

### FILE: <относительный/путь/Файл.расширение>
<<<<<<< SEARCH
<фрагмент, который надо заменить — ДОСЛОВНО как в файле>
=======
<чем заменить>
>>>>>>> REPLACE

Блоков поиска-замены на один файл может быть несколько подряд.

Правила точечной правки, соблюдай строго:

- SEARCH копируется из файла СИМВОЛ В СИМВОЛ: те же отступы, пробелы,
  переносы, комментарии. Несовпадение хотя бы в одном пробеле — правка
  не применится.
- SEARCH должен быть УНИКАЛЕН в файле. Если фрагмент встречается несколько
  раз, добавь соседних строк, пока он не станет единственным.
- Бери минимальный фрагмент, достаточный для однозначности: несколько
  строк вокруг изменения, а не всю функцию и тем более не весь файл.
- Файл целиком выводи только если он новый или меняется больше половины
  его содержимого.

Зачем так: строки, не попавшие в патч, не проходят через тебя и потому не
могут быть потеряны. При перевыводе файла целиком регулярно пропадают детали,
которых задача не касалась, — подключение темы, подобранные цвета, префиксы
в именах зависимостей. Файл при этом остаётся синтаксически верным, а
поведение ломается.

Вместо "расширение" и "язык" подставь принятые в стеке проекта
(например .kt и kotlin, .py и python, .ts и typescript).

Файлы, приложенные к заданию как контекст, помечены "--- СОДЕРЖИМОЕ ФАЙЛА ... ---".
Это справка для чтения, а НЕ образец формата ответа: не копируй этот вид
и не выводи такие файлы целиком, если правишь их точечно.

Требования к путям: только относительные пути от корня проекта, исходники
клади внутрь "{source_root}" по правилам стека. Никаких абсолютных путей.
Все завершающие комментарии и пояснения вне блоков FILE запрещены.""",
}


# ---------------------------------------------------------------------------
# Конфигурация и схема
# ---------------------------------------------------------------------------

DEFAULT_CONFIG: Dict = {
    "project_context": {
        # Нейтральные заглушки: если их не переопределить в config.yaml,
        # модель увидит явный маркер вместо чужого стека.
        "tech_stack": "НЕ ЗАДАН — укажи tech_stack в .agent/config.yaml",
        "code_style": "НЕ ЗАДАН — укажи code_style в .agent/config.yaml",
        "base_package": "app",
        "source_root": "src",
    },
    "ai_settings": {
        "base_url": "http://localhost:11434/v1",
        "model": "qwen2.5-coder:7b",
        "temperature": 0.2,
        "max_tokens": 8192,
        "timeout_seconds": 180,
        "api_key_env": "OPENAI_API_KEY",
    },
    "pipeline": {
        "isolate_git_branch": False,
    },
}


def _deep_merge(base: Dict, override: Dict) -> Dict:
    """Рекурсивно наложить override на base (base остаётся нетронутым)."""
    result = copy.deepcopy(base)
    for key, value in (override or {}).items():
        if isinstance(value, dict) and isinstance(result.get(key), dict):
            result[key] = _deep_merge(result[key], value)
        else:
            result[key] = value
    return result


def load_config() -> Dict:
    """Прочитать .agent/config.yaml, наложив значения на дефолты."""
    if not CONFIG_PATH.exists():
        warn("config.yaml не найден, используются значения по умолчанию")
        return copy.deepcopy(DEFAULT_CONFIG)
    with CONFIG_PATH.open(encoding="utf-8") as fh:
        data = yaml.safe_load(fh) or {}
    return _deep_merge(DEFAULT_CONFIG, data)


@dataclass
class Artifact:
    """Описание артефакта конвейера из schema.yaml."""
    id: str
    generates: Optional[str] = None          # путь к файлу, None = шаг без файла
    requires: List[str] = field(default_factory=list)
    description: str = ""
    rules: List[str] = field(default_factory=list)


def _load_schema_data() -> Dict:
    """Сырые данные schema.yaml (общий ридер для артефактов и мейн-спеки)."""
    if not SCHEMA_PATH.exists():
        fail("Не найден .agent/schema.yaml — схема конвейера обязательна")
    with SCHEMA_PATH.open(encoding="utf-8") as fh:
        return yaml.safe_load(fh) or {}


def load_schema() -> List[Artifact]:
    """Прочитать .agent/schema.yaml и вернуть упорядоченный список артефактов."""
    data = _load_schema_data()
    artifacts = []
    for raw in data.get("artifacts", []):
        artifacts.append(Artifact(
            id=raw["id"],
            generates=raw.get("generates"),
            requires=list(raw.get("requires", [])),
            description=raw.get("description", ""),
            rules=list(raw.get("rules", [])),
        ))
    if not artifacts:
        fail("В schema.yaml не описано ни одного артефакта")
    return artifacts


def load_project_spec() -> Artifact:
    """Описание мейн-спеки проекта из schema.yaml (секция project_spec)."""
    raw = _load_schema_data().get("project_spec") or {}
    return Artifact(
        id="main-spec",
        generates=raw.get("generates", "specs/MAIN.md"),
        description=raw.get("description", ""),
        rules=list(raw.get("rules", [])),
    )


def main_spec_path() -> Path:
    """Путь к мейн-спеке (по умолчанию specs/MAIN.md)."""
    return PROJECT_ROOT / load_project_spec().generates


def artifact_path(artifact: Artifact, change_id: str) -> Optional[Path]:
    """Путь к файлу артефакта для конкретного изменения (или None)."""
    if not artifact.generates:
        return None
    rel = artifact.generates.format(change_id=change_id)
    return PROJECT_ROOT / rel


def check_requirements(artifact: Artifact, change_id: str) -> None:
    """Проверить, что все зависимые артефакты уже сгенерированы."""
    schema = {a.id: a for a in load_schema()}
    missing = []
    for req_id in artifact.requires:
        req = schema.get(req_id)
        if req is None:
            fail("schema.yaml: артефакт '{}' требует неизвестный '{}'"
                 .format(artifact.id, req_id))
        req_path = artifact_path(req, change_id)
        if req_path and not req_path.exists():
            missing.append(str(req_path.relative_to(PROJECT_ROOT)))
    if missing:
        fail("Артефакт '{}' нельзя сгенерировать, отсутствуют зависимости: {}"
             .format(artifact.id, ", ".join(missing)))


# ---------------------------------------------------------------------------
# LLM-клиент (OpenAI-совместимый API: OpenAI, Ollama, OpenRouter, vLLM...)
# ---------------------------------------------------------------------------

@dataclass
class LLMSettings:
    base_url: str
    model: str
    temperature: float
    max_tokens: int
    timeout: int
    api_key: Optional[str]
    verify_ssl: bool
    stream: bool = True
    extra: Dict = field(default_factory=dict)


def load_llm_settings(cfg: Dict) -> LLMSettings:
    ai = cfg["ai_settings"]
    import os
    # Приоритет: переменная окружения -> api_key из config.yaml -> без ключа.
    api_key = (os.environ.get(ai.get("api_key_env", "OPENAI_API_KEY"))
               or ai.get("api_key"))
    return LLMSettings(
        base_url=str(ai["base_url"]).rstrip("/"),
        model=ai["model"],
        temperature=float(ai.get("temperature", 0.2)),
        max_tokens=int(ai.get("max_tokens", 4096)),
        timeout=int(ai.get("timeout_seconds", 180)),
        api_key=api_key,
        verify_ssl=bool(ai.get("verify_ssl", True)),
        stream=bool(ai.get("stream", True)),
        extra=dict(ai.get("extra_params") or {}),
    )


def codegen_llm_settings(cfg: Dict) -> LLMSettings:
    """Настройки для кодогенерации: базовые + переопределения этого шага.

    Спека и код — разные задачи. При проектировании размышления модели
    полезны, при генерации кода по готовому плану они только жгут бюджет
    max_tokens. Поэтому code_generation.extra_params накладывается поверх
    ai_settings.extra_params и действует только на этот шаг.
    """
    settings = load_llm_settings(cfg)
    override = (cfg.get("code_generation") or {}).get("extra_params")
    if override:
        settings.extra = _deep_merge(settings.extra, override)
    return settings


FILE_HEADER_RE = re.compile(r"###\s*FILE:\s*([^\s`\n]+)")


def _stream_response(requests, url, headers, payload, settings,
                     progress: bool) -> str:
    """Потоковый запрос: собираем ответ по кускам, попутно показывая прогресс.

    Стриминг тут не ради красоты: при обычном запросе timeout применяется к
    ожиданию ВСЕГО ответа, и длинная генерация рвётся по таймауту. В потоке
    таймаут считается между кусками, поэтому долгий ответ не обрывается.
    """
    payload = dict(payload, stream=True)
    pieces: List[str] = []
    seen_files = set()
    scanned = 0
    thinking_ticks = 0
    started = time.time()

    with requests.post(url, json=payload, headers=headers,
                       timeout=settings.timeout, verify=settings.verify_ssl,
                       stream=True) as response:
        response.raise_for_status()
        for raw in response.iter_lines(decode_unicode=True):
            if not raw or not raw.startswith("data:"):
                continue
            data = raw[5:].strip()
            if data == "[DONE]":
                break
            try:
                delta = json.loads(data)["choices"][0].get("delta") or {}
            except Exception:  # noqa: BLE001 — битый кусок просто пропускаем
                continue

            piece = delta.get("content") or ""
            if not piece:
                # Думающие модели сначала шлют reasoning_content — показываем,
                # что процесс идёт, а не завис.
                if delta.get("reasoning_content") and progress:
                    thinking_ticks += 1
                    if thinking_ticks % 25 == 0:
                        print(".", end="", flush=True)
                continue

            pieces.append(piece)
            if progress:
                text = "".join(pieces)
                end = text.rfind("\n")
                if end > scanned:
                    for match in FILE_HEADER_RE.finditer(text, scanned, end):
                        path = match.group(1).strip()
                        if path not in seen_files:
                            seen_files.add(path)
                            if thinking_ticks:
                                print()
                                thinking_ticks = 0
                            ok("[{:>3.0f}с] {}".format(time.time() - started, path))
                    scanned = end

    if progress:
        if thinking_ticks:
            print()
        info("ответ получен за {:.0f}с, файлов: {}".format(
            time.time() - started, len(seen_files)))
    return "".join(pieces)


def llm_chat(settings: LLMSettings, system: str, user: str,
             progress: bool = False) -> str:
    """Один запрос к /chat/completions с ретраями. Возвращает текст ответа."""
    try:
        import requests
    except ImportError:
        fail("Библиотека requests не установлена: pip install -r .agent/requirements.txt")

    url = "{}/chat/completions".format(settings.base_url)
    headers = {"Content-Type": "application/json"}
    if settings.api_key:
        headers["Authorization"] = "Bearer {}".format(settings.api_key)
    if not settings.verify_ssl:
        import urllib3
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    payload = {
        "model": settings.model,
        "temperature": settings.temperature,
        "max_tokens": settings.max_tokens,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    }
    # Произвольные параметры провайдера из config.yaml → в тело запроса как
    # есть. Так новые опции (thinking, top_p, reasoning_effort и прочее)
    # добавляются правкой конфига, без изменения движка.
    if settings.extra:
        payload.update(settings.extra)
        info("Доп. параметры запроса: {}".format(
            ", ".join(sorted(settings.extra))))

    last_error = None
    use_stream = settings.stream
    for attempt in range(1, 4):  # 3 попытки
        try:
            if use_stream:
                content = _stream_response(requests, url, headers, payload,
                                           settings, progress)
                if not content.strip():
                    raise RuntimeError("пустой ответ в потоковом режиме")
            else:
                response = requests.post(url, json=payload, headers=headers,
                                         timeout=settings.timeout,
                                         verify=settings.verify_ssl)
                response.raise_for_status()
                body = response.json()["choices"][0]
                content = body["message"].get("content") or ""
                if not content.strip():
                    # Думающая модель могла израсходовать весь max_tokens на
                    # reasoning_content и не дойти до ответа.
                    raise RuntimeError(
                        "пустой ответ (finish_reason={}) — вероятно, весь "
                        "max_tokens ушёл на размышления"
                        .format(body.get("finish_reason")))
            return clean_llm_output(content)
        except Exception as exc:  # noqa: BLE001 — показываем пользователю любую
            last_error = exc
            warn("Попытка {} из 3 не удалась: {}".format(attempt, exc))
            if use_stream:
                # Провайдер может не поддерживать stream — пробуем обычный режим.
                warn("Повтор без стриминга")
                use_stream = False
    fail("LLM недоступна ({}). Проверь base_url/model в .agent/config.yaml "
         "или используй --offline".format(last_error))
    return ""  # unreachable


def clean_llm_output(text: str) -> str:
    """Убрать артефакты LLM: блоки <think>, внешнюю обёртку из код-блоков."""
    text = re.sub(r"<think>.*?</think>", "", text, flags=re.DOTALL)
    text = text.strip()
    # Если весь ответ обёрнут в ```markdown ... ``` — снимаем обёртку.
    if text.startswith("```"):
        text = re.sub(r"^```[a-zA-Z]*\n", "", text)
        if text.endswith("```"):
            text = text[: -3].rstrip()
    return text.strip() + "\n"


# ---------------------------------------------------------------------------
# Изменения (Change) и их метаданные
# ---------------------------------------------------------------------------

# Транслитерация RU->EN для человекочитаемых ID изменений.
_RU_MAP = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "е": "e", "ё": "e",
    "ж": "zh", "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m",
    "н": "n", "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u",
    "ф": "f", "х": "kh", "ц": "ts", "ч": "ch", "ш": "sh", "щ": "sch",
    "ъ": "", "ы": "y", "ь": "", "э": "e", "ю": "yu", "я": "ya",
}

_STOP_WORDS = {
    "khochu", "hochu", "nuzhno", "nugno", "sdelai", "sdelat", "dobavit",
    "please", "want", "need", "make", "add", "the", "and", "for", "with",
}


def slugify(text: str, max_words: int = 3, max_len: int = 40) -> str:
    """Превратить промпт в короткий slug: 'хочу эхо-сервис base64' -> 'ekho-servis-base64'."""
    translated = "".join(_RU_MAP.get(ch, ch) for ch in text.lower())
    words = [w for w in re.findall(r"[a-z0-9]+", translated)
             if len(w) >= 3 and w not in _STOP_WORDS]
    slug = "-".join(words[:max_words])[:max_len].strip("-")
    return slug or "feature"


def list_changes() -> List[Path]:
    """Существующие папки изменений в specs/."""
    if not SPECS_DIR.exists():
        return []
    return sorted(p for p in SPECS_DIR.iterdir() if p.is_dir())


def next_change_id(prompt: str) -> str:
    """Сгенерировать следующий ID вида change-00N-slug."""
    numbers = [0]
    for path in list_changes():
        match = re.match(r"change-(\d+)", path.name)
        if match:
            numbers.append(int(match.group(1)))
    return "change-{:03d}-{}".format(max(numbers) + 1, slugify(prompt))


def find_change(ref: str) -> Path:
    """Найти изменение по полному имени, префиксу или номеру ('1' -> change-001-*)."""
    changes = list_changes()
    for path in changes:
        if path.name == ref:
            return path
    by_prefix = [p for p in changes if p.name.startswith(ref)]
    if len(by_prefix) == 1:
        return by_prefix[0]
    if ref.isdigit():
        by_number = [p for p in changes
                     if re.match(r"change-{:03d}(?!\d)".format(int(ref)), p.name)]
        if len(by_number) == 1:
            return by_number[0]
    fail("Изменение '{}' не найдено. Смотри: python .agent/run-agent.py --list".format(ref))
    return Path()  # unreachable


def load_change_meta(change_dir: Path) -> Dict:
    meta_path = change_dir / "change.yaml"
    if not meta_path.exists():
        return {}
    with meta_path.open(encoding="utf-8") as fh:
        return yaml.safe_load(fh) or {}


def save_change_meta(change_dir: Path, meta: Dict) -> None:
    with (change_dir / "change.yaml").open("w", encoding="utf-8") as fh:
        yaml.safe_dump(meta, fh, allow_unicode=True, sort_keys=False)


def mark_artifact(change_dir: Path, artifact_id: str, status: str) -> None:
    meta = load_change_meta(change_dir)
    meta.setdefault("status", {})[artifact_id] = status
    meta["updated"] = now_str()
    save_change_meta(change_dir, meta)


def now_str() -> str:
    return datetime.datetime.now().strftime("%Y-%m-%d %H:%M")


# ---------------------------------------------------------------------------
# Шаблоны
# ---------------------------------------------------------------------------

def read_template(artifact_id: str) -> str:
    """Текст шаблона для артефакта. Отдаётся и LLM, и offline-рендеру."""
    names = {"proposal": "proposal.template",
             "spec-bdd": "spec.template",
             "tasks": "tasks.template",
             "main": "main.template"}
    path = TEMPLATES_DIR / names[artifact_id]
    if not path.exists():
        fail("Не найден шаблон: {}".format(path))
    return path.read_text(encoding="utf-8")


def render_offline(artifact_id: str, ctx: Dict[str, str]) -> str:
    """Каркас артефакта без LLM: подставить контекст, остальное оставить TODO."""
    template = read_template(artifact_id)
    def replace(match: "re.Match") -> str:
        key = match.group(1)
        return ctx.get(key, "TODO: заполни вручную или перегенерируй с LLM")
    return re.sub(r"\{\{(\w+)\}\}", replace, template)


# ---------------------------------------------------------------------------
# Генерация артефактов (Proposal -> Spec -> Tasks)
# ---------------------------------------------------------------------------

ARTIFACT_ORDER = ["proposal", "spec-bdd", "tasks"]


def main_spec_context() -> str:
    """Текст мейн-спеки для подмешивания в промпты (пустая строка, если нет)."""
    path = main_spec_path()
    if not path.exists():
        return ""
    return ("Мейн-спека проекта (MAIN.md) — приложение и его стек:\n"
            "---\n{}\n---".format(path.read_text(encoding="utf-8").rstrip()))


def build_user_prompt(artifact_id: str, prompt: str, change_dir: Path,
                      cfg: Dict) -> str:
    """Пользовательский промпт: сырой промпт + правила схемы + контекст артефактов."""
    artifact = next(a for a in load_schema() if a.id == artifact_id)
    parts = []

    # Мейн-спека — первичный контекст: что за приложение и какой у него стек.
    main_ctx = main_spec_context()
    if main_ctx:
        parts.append(main_ctx)

    parts.append("Сырой промпт пользователя:\n---\n{}\n---".format(prompt))

    # Зависимые артефакты как контекст (proposal для spec, spec для tasks).
    depends = {"spec-bdd": ["proposal"], "tasks": ["proposal", "spec-bdd"]}
    for dep_id in depends.get(artifact_id, []):
        dep = next(a for a in load_schema() if a.id == dep_id)
        content = artifact_path(dep, change_dir.name).read_text(encoding="utf-8")
        parts.append("Текущий {}:\n---\n{}\n---".format(dep_id, content.rstrip()))

    if artifact.rules:
        parts.append("Правила из schema.yaml:\n" +
                     "\n".join("- {}".format(r) for r in artifact.rules))
    parts.append("Структура шаблона (следуй ей):\n---\n{}---".format(
        read_template(artifact_id)))
    parts.append("Контекст проекта: {}".format(
        cfg["project_context"]["tech_stack"]))
    return "\n\n".join(parts)


def generate_artifact(artifact_id: str, change_dir: Path, prompt: str,
                      cfg: Dict, offline: bool) -> Optional[Path]:
    """Сгенерировать один артефакт конвейера и записать файл. Возвращает путь."""
    artifact = next(a for a in load_schema() if a.id == artifact_id)
    check_requirements(artifact, change_dir.name)
    target = artifact_path(artifact, change_dir.name)
    assert target is not None  # у документных артефактов always задан generates

    ctx = {"CHANGE_ID": change_dir.name, "DATE": now_str(), "TITLE": prompt[:80]}
    if offline:
        content = render_offline(artifact_id, ctx)
    else:
        settings = load_llm_settings(cfg)
        info("Генерация {} через {} ...".format(artifact_id, settings.model))
        content = llm_chat(settings, SYSTEM_PROMPTS[artifact_id],
                           build_user_prompt(artifact_id, prompt, change_dir, cfg))

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    mark_artifact(change_dir, artifact_id,
                  "offline-skeleton" if offline else "generated")
    ok("{} -> {}".format(artifact_id, target.relative_to(PROJECT_ROOT)))
    return target


def cmd_main(args: argparse.Namespace, cfg: Dict) -> None:
    """--main: сгенерировать/обновить мейн-спеку проекта (specs/MAIN.md)."""
    prompt = args.main
    spec = load_project_spec()
    target = main_spec_path()
    exists = target.exists()

    banner("Мейн-спека проекта" + (" (обновление)" if exists else " (создание)"))
    if not exists:
        info("Мейн-спека описывает, что за приложение и какой у него стек. "
             "Она подмешивается в контекст каждого изменения.")

    rules = "\n".join("- {}".format(r) for r in spec.rules)
    parts = [
        "Описание проекта от пользователя:\n---\n{}\n---".format(prompt),
        "Технологический стек из config.yaml:\n---\n{}\n---".format(
            yaml.safe_dump(cfg["project_context"], allow_unicode=True, sort_keys=False)),
        "Код-стайл: {}".format(cfg["project_context"].get("code_style", "")),
    ]
    if exists:
        parts.append("Текущая версия MAIN.md — обнови её с учётом новых вводных:\n"
                     "---\n{}\n---".format(target.read_text(encoding="utf-8").rstrip()))
    if spec.rules:
        parts.append("Правила из schema.yaml:\n" + rules)
    parts.append("Структура шаблона (следуй ей):\n---\n{}---".format(
        read_template("main")))

    ctx = {"TITLE": "проект пользователя", "DATE": now_str()}
    if args.offline:
        content = render_offline("main", ctx)
    else:
        settings = load_llm_settings(cfg)
        info("Генерация MAIN.md через {} ...".format(settings.model))
        content = llm_chat(settings, SYSTEM_PROMPTS["main"], "\n\n".join(parts))

    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    ok("{} -> {}".format("обновлён" if exists else "создан",
                         target.relative_to(PROJECT_ROOT)))
    print()
    info("Дальше: python .agent/run-agent.py --new \"опиши первое изменение\"")


def cmd_new(args: argparse.Namespace, cfg: Dict) -> None:
    """--new: создать изменение и прогнать Proposal -> Spec -> Tasks."""
    prompt = args.new
    if not main_spec_path().exists():
        warn("specs/MAIN.md не найден — изменения будут генерироваться без "
             "контекста приложения (стек возьмётся только из config.yaml)")
        warn("Создай мейн-спеку: python .agent/run-agent.py --main \"что за приложение\"")
    change_id = next_change_id(prompt)
    change_dir = SPECS_DIR / change_id

    banner("Создание изменения: {}".format(change_id))
    change_dir.mkdir(parents=True, exist_ok=False)
    save_change_meta(change_dir, {
        "id": change_id,
        "prompt": prompt,
        "created": now_str(),
        "updated": now_str(),
        "mode": "offline" if args.offline else "llm",
        "status": {},
    })
    ok("папка specs/{}/ создана".format(change_id))

    if args.branch or cfg["pipeline"].get("isolate_git_branch"):
        create_git_branch(change_id)

    for artifact_id in ARTIFACT_ORDER:
        generate_artifact(artifact_id, change_dir, prompt, cfg, args.offline)

    if getattr(args, "worktree", False):
        print()
        setup_worktree(change_id, change_dir, cfg)

    print()
    info("Дальше:")
    print("  1. Прочитай и при необходимости поправь файлы в specs/{}".format(change_id))
    print("  2. Запусти кодогенерацию:")
    print("       python .agent/run-agent.py --codegen --change {}".format(change_id))


def create_git_branch(change_id: str) -> None:
    """Изолировать изменение в ветке spec/<change_id> (по желанию)."""
    try:
        subprocess.run(["git", "rev-parse", "--is-inside-work-tree"],
                       capture_output=True, check=True, cwd=str(PROJECT_ROOT))
    except Exception:  # noqa: BLE001
        warn("Проект не является git-репозиторием — ветка не создана")
        return
    result = subprocess.run(["git", "checkout", "-b", "spec/" + change_id],
                            capture_output=True, text=True, cwd=str(PROJECT_ROOT))
    if result.returncode == 0:
        ok("git-ветка spec/{} создана".format(change_id))
    else:
        warn("Не удалось создать ветку: {}".format(result.stderr.strip()))


# ---------------------------------------------------------------------------
# Git worktree: параллельная работа над разными изменениями
#
# Порядок принципиален: номер изменения выдаётся по максимуму существующих
# папок в specs/, поэтому спека СНАЧАЛА коммитится в основной ветке (номер
# занят и виден всем), и только потом от этого коммита отпочковывается
# worktree. Иначе два параллельных worktree выдали бы один и тот же ID.
# ---------------------------------------------------------------------------

def git(*cmd: str, cwd: Optional[Path] = None) -> subprocess.CompletedProcess:
    """Запустить git-команду в корне проекта (или в указанной папке)."""
    return subprocess.run(["git", *cmd], capture_output=True, text=True,
                          cwd=str(cwd or PROJECT_ROOT))


def is_git_repo() -> bool:
    return git("rev-parse", "--is-inside-work-tree").returncode == 0


def git_current_branch() -> str:
    result = git("rev-parse", "--abbrev-ref", "HEAD")
    return result.stdout.strip() if result.returncode == 0 else ""


def change_number(change_id: str) -> str:
    """'change-002-slug' -> '002' (или сам id, если формат неожиданный)."""
    match = re.match(r"change-(\d+)", change_id)
    return match.group(1) if match else change_id


def commit_spec(change_id: str) -> bool:
    """Закоммитить только папку спеки — чужие staged-файлы не затрагиваются."""
    rel = "specs/{}".format(change_id)
    if git("add", "--", rel).returncode != 0:
        warn("git add {} не удался — worktree не создан".format(rel))
        return False
    result = git("commit", "-m", "spec: {}".format(change_id), "--", rel)
    output = result.stdout + result.stderr
    if result.returncode == 0:
        ok("спека закоммичена: spec: {}".format(change_id))
        return True
    if "nothing to commit" in output or "no changes added" in output:
        warn("нечего коммитить — спека уже в истории")
        return True
    warn("Не удалось закоммитить спеку: {}".format(output.strip()))
    return False


def copy_local_files(target: Path, cfg: Dict) -> None:
    """Перенести в новый worktree локальные файлы, которых нет в git.

    Такие файлы (local.properties с путём к Android SDK, .env, локальные
    настройки) лежат в .gitignore, поэтому git их в worktree не переносит —
    и сборка там падает не из-за кода, а из-за окружения.
    """
    names = (cfg.get("pipeline") or {}).get("worktree_copy") or []
    for rel in names:
        source = PROJECT_ROOT / rel
        if not source.exists():
            continue
        destination = target / rel
        destination.parent.mkdir(parents=True, exist_ok=True)
        try:
            if source.is_dir():
                shutil.copytree(source, destination, dirs_exist_ok=True)
            else:
                shutil.copy2(source, destination)
            ok("скопирован в worktree: {}".format(rel))
        except Exception as exc:  # noqa: BLE001
            warn("не удалось скопировать {}: {}".format(rel, exc))


def setup_worktree(change_id: str, change_dir: Path, cfg: Dict) -> None:
    """Закоммитить спеку и создать worktree с отдельной веткой под изменение."""
    if not is_git_repo():
        warn("Проект не является git-репозиторием — worktree не создан")
        return
    if git("rev-parse", "HEAD").returncode != 0:
        warn("В репозитории ещё нет коммитов — worktree создать нельзя")
        return

    base_branch = git_current_branch()
    slug = change_id[len("change-"):] if change_id.startswith("change-") else change_id
    branch = "change/{}".format(slug)
    path = PROJECT_ROOT.parent / "{}-wt-{}".format(PROJECT_ROOT.name,
                                                   change_number(change_id))
    if path.exists():
        warn("Папка {} уже существует — worktree не создан".format(path))
        return

    # Метаданные пишем ДО коммита, чтобы они уехали в историю вместе со спекой.
    meta = load_change_meta(change_dir)
    meta["git"] = {"branch": branch, "worktree": str(path),
                   "base_branch": base_branch}
    meta["updated"] = now_str()
    save_change_meta(change_dir, meta)

    if not commit_spec(change_id):
        return

    result = git("worktree", "add", str(path), "-b", branch)
    if result.returncode != 0:
        warn("Не удалось создать worktree: {}".format(
            (result.stderr or result.stdout).strip()))
        return

    ok("worktree: {}".format(path))
    ok("ветка {} (от {})".format(branch, base_branch or "HEAD"))
    copy_local_files(path, cfg)
    print()
    info("Работать над изменением там:")
    print("  cd {}".format(path))
    print("  python3 .agent/run-agent.py --codegen --change {} --dry-run"
          .format(change_number(change_id)))


def cmd_worktrees(args: argparse.Namespace) -> None:
    """--worktrees: какие worktree живы и что из них ещё не смерджено."""
    if not is_git_repo():
        fail("Проект не является git-репозиторием")

    meta_by_branch: Dict[str, Dict] = {}
    for change_dir in list_changes():
        meta = load_change_meta(change_dir)
        git_meta = meta.get("git") or {}
        if git_meta.get("branch"):
            meta_by_branch[git_meta["branch"]] = {
                "change_id": meta.get("id", change_dir.name),
                "base_branch": git_meta.get("base_branch", ""),
            }

    result = git("worktree", "list", "--porcelain")
    if result.returncode != 0:
        fail("git worktree list не отработал: {}".format(result.stderr.strip()))

    entries: List[Dict[str, str]] = []
    current: Dict[str, str] = {}
    for line in result.stdout.splitlines():
        if not line.strip():
            if current:
                entries.append(current)
                current = {}
            continue
        key, _, value = line.partition(" ")
        current[key] = value
    if current:
        entries.append(current)

    main_path = str(PROJECT_ROOT.resolve())
    rows = [e for e in entries if e.get("worktree")
            and str(Path(e["worktree"]).resolve()) != main_path]

    banner("Worktree изменений")
    if not rows:
        print("  Дополнительных worktree нет.")
        print()
        info("Создать: python .agent/run-agent.py --new \"...\" --worktree")
        return

    for entry in rows:
        path = Path(entry["worktree"])
        branch = entry.get("branch", "").replace("refs/heads/", "") or "(detached)"
        meta = meta_by_branch.get(branch, {})
        base = meta.get("base_branch") or git_current_branch()

        print("  {}".format(path.name))
        print("    изменение : {}".format(meta.get("change_id", "—")))
        print("    ветка     : {}   (база: {})".format(branch, base or "—"))
        print("    путь      : {}".format(path))
        if not path.exists():
            warn("папка отсутствует — почисти: git worktree prune")
            print()
            continue

        dirty = git("status", "--porcelain", cwd=path)
        if dirty.returncode == 0 and dirty.stdout.strip():
            warn("незакоммичено файлов: {}".format(
                len(dirty.stdout.strip().splitlines())))

        if base:
            log = git("log", "--oneline", "{}..{}".format(base, branch))
            if log.returncode == 0:
                commits = [ln for ln in log.stdout.splitlines() if ln.strip()]
                if commits:
                    warn("НЕ смерджено в {}: коммитов {}".format(base, len(commits)))
                    for line in commits[:5]:
                        print("        {}".format(line))
                    if len(commits) > 5:
                        print("        ... ещё {}".format(len(commits) - 5))
                else:
                    ok("всё смерджено в {}".format(base))
        print()

    info("Смерджить и убрать worktree:")
    print("  git checkout <база> && git merge <ветка>")
    print("  git worktree remove <путь> && git branch -d <ветка>")


# ---------------------------------------------------------------------------
# Кодогенерация (Tasks -> Code)
# ---------------------------------------------------------------------------

FILE_BLOCK_RE = re.compile(
    r"###\s*FILE:\s*(?P<path>[^\s`]+)\s*\n+```[^\n]*\n(?P<body>.*?)\n?```",
    re.DOTALL,
)

# Точечная правка: блок поиска и замены внутри секции файла. Формат намеренно
# совпадает с конфликтными маркерами git — модели хорошо его знают.
PATCH_BLOCK_RE = re.compile(
    r"<<<<<<<\s*SEARCH\s*\n(?P<search>.*?)\n?=======\s*\n(?P<replace>.*?)\n?>>>>>>>\s*REPLACE",
    re.DOTALL,
)

FILE_SECTION_RE = re.compile(r"###\s*FILE:\s*(?P<path>[^\s`\n]+)[^\n]*\n")


def parse_files(raw: str) -> List[Dict]:
    """Разобрать ответ LLM на операции над файлами.

    Поддерживаются два формата, и они смешиваются в одном ответе:

      full  — `### FILE: путь` + код-блок с ПОЛНЫМ содержимым (новые файлы);
      patch — `### FILE: путь` + блоки <<<<<<< SEARCH / ======= / >>>>>>> REPLACE
              (правка существующего файла).

    Точечная правка не только дешевле по токенам: строки, которых нет в патче,
    физически не проходят через модель, поэтому их нельзя потерять. Именно так
    в этом проекте пропадали обёртка темы и префиксы имён артефактов.
    """
    files: List[Dict] = []
    sections = list(FILE_SECTION_RE.finditer(raw))
    for index, match in enumerate(sections):
        start = match.end()
        end = sections[index + 1].start() if index + 1 < len(sections) else len(raw)
        body = raw[start:end]
        path = match.group("path").strip()

        edits = [(m.group("search"), m.group("replace"))
                 for m in PATCH_BLOCK_RE.finditer(body)]
        if edits:
            files.append({"path": path, "kind": "patch", "edits": edits})
            continue

        fence = re.search(r"```[^\n]*\n(?P<code>.*?)\n?```", body, re.DOTALL)
        if fence:
            files.append({"path": path, "kind": "full",
                          "content": fence.group("code").rstrip() + "\n"})
    return files


def apply_patch(path: Path, edits: List[tuple]) -> str:
    """Применить блоки поиска-замены к файлу. Ошибка — понятное исключение."""
    if not path.is_file():
        raise ValueError("файла нет на диске — для нового файла нужен полный текст")
    text = path.read_text(encoding="utf-8")
    for number, (search, replace) in enumerate(edits, start=1):
        if not search.strip():
            raise ValueError("блок {}: пустой SEARCH".format(number))
        count = text.count(search)
        if count == 0:
            head = search.strip().splitlines()[0][:70] if search.strip() else ""
            raise ValueError(
                "блок {}: фрагмент не найден (первая строка: {!r}). "
                "SEARCH должен совпадать дословно, включая отступы".format(number, head))
        if count > 1:
            raise ValueError(
                "блок {}: фрагмент встречается {} раз — добавь строк "
                "контекста, чтобы он стал уникальным".format(number, count))
        text = text.replace(search, replace, 1)
    return text


def safe_relative_path(raw_path: str) -> Optional[Path]:
    """Валидация пути: только относительный, без выхода за корень проекта."""
    path = Path(raw_path)
    if path.is_absolute() or ".." in path.parts or not str(path).strip():
        return None
    return path


def in_linked_worktree() -> bool:
    """Мы сейчас внутри дочернего worktree, а не в основном рабочем дереве?"""
    own = git("rev-parse", "--git-dir").stdout.strip()
    common = git("rev-parse", "--git-common-dir").stdout.strip()
    if not own or not common:
        return False
    return Path(own).resolve() != Path(common).resolve()


def uncommitted_outside_specs() -> List[str]:
    """Незакоммиченные файлы вне specs/ — они НЕ попадут в новый worktree."""
    result = git("status", "--porcelain")
    if result.returncode != 0:
        return []
    paths = []
    for line in result.stdout.splitlines():
        path = line[3:].strip()
        if path and not path.startswith("specs/"):
            paths.append(path)
    return paths


def ensure_worktree(change_dir: Path, args: argparse.Namespace,
                    cfg: Dict) -> None:
    """Перед кодогеном завести worktree, если у изменения его ещё нет.

    Смысл: кодоген всегда идёт в изоляции, даже если при создании спеки
    про --worktree забыли. Не срабатывает, если worktree уже есть, если он
    был и удалён после мерджа, если мы сами внутри дочернего worktree,
    а также при --here или pipeline.auto_worktree: false.
    """
    if getattr(args, "here", False):
        return
    if not (cfg.get("pipeline") or {}).get("auto_worktree", True):
        return
    if (load_change_meta(change_dir).get("git") or {}).get("worktree"):
        return  # привязка уже есть (существующая или смердженная)
    if not is_git_repo():
        return
    if git("rev-parse", "HEAD").returncode != 0:
        warn("В репозитории нет коммитов — worktree не создаётся, "
             "кодоген пойдёт в текущей директории")
        return
    if in_linked_worktree():
        return  # уже в изоляции, вложенные worktree не плодим

    banner("Worktree для {}".format(change_dir.name))
    info("У изменения нет своего worktree — создаю (отключить: --here)")

    dirty = uncommitted_outside_specs()
    if dirty:
        print()
        warn("Есть незакоммиченные изменения вне specs/ — в worktree они "
             "НЕ попадут:")
        for path in dirty[:5]:
            print("    {}".format(path))
        if len(dirty) > 5:
            print("    ... ещё {}".format(len(dirty) - 5))
        info("Закоммить их сначала, если они нужны в worktree:")
        print("    git add -A && git commit -m \"chore: ...\"")
        if not confirm_continue(args, "Всё равно создать worktree?"):
            warn("Отменено — кодоген не запущен")
            raise SystemExit(1)

    setup_worktree(change_dir.name, change_dir, cfg)


def confirm_continue(args: argparse.Namespace, question: str) -> bool:
    if getattr(args, "yes", False):
        return True
    try:
        answer = input("\n{} [y/N] ".format(question)).strip().lower()
    except EOFError:
        answer = "n"
    return answer in ("y", "yes", "д", "да")


def maybe_delegate_to_worktree(change_dir: Path, args: argparse.Namespace) -> None:
    """Изменение привязано к другому worktree — перезапустить себя там.

    Работать можно из общей директории: движок сам находит нужный worktree
    по секции git в change.yaml и передаёт туда те же аргументы. Дочерний
    процесс видит PROJECT_ROOT == worktree, поэтому повторной делегации
    не происходит. Отключается флагом --here.
    """
    if getattr(args, "here", False):
        return
    git_meta = (load_change_meta(change_dir).get("git") or {})
    target = git_meta.get("worktree")
    if not target:
        return
    target_path = Path(target)
    if not target_path.exists():
        return  # worktree смерджен и удалён — работаем здесь
    if target_path.resolve() == PROJECT_ROOT.resolve():
        return  # мы уже там

    script = target_path / ".agent" / "run-agent.py"
    if not script.exists():
        warn("В worktree {} нет .agent/run-agent.py — работаю здесь"
             .format(target_path))
        return

    info("Изменение привязано к worktree: {} (ветка {})".format(
        target_path.name, git_meta.get("branch", "—")))
    info("Перехожу туда и продолжаю там — код запишется в этот worktree.")
    info("Остаться здесь: добавь флаг --here")
    print()
    # Дочерний процесс пишет в тот же поток — сбрасываем буфер, иначе при
    # перенаправлении вывода наши строки окажутся после его вывода.
    sys.stdout.flush()
    result = subprocess.run([sys.executable, str(script), *sys.argv[1:]],
                            cwd=str(target_path))
    raise SystemExit(result.returncode)


def cmd_codegen(args: argparse.Namespace, cfg: Dict) -> None:
    """--codegen: LLM читает спеку+таски и пишет файлы в проект."""
    change_dir = find_change(args.change)
    artifact = next(a for a in load_schema() if a.id == "code-generation")
    check_requirements(artifact, change_dir.name)
    ensure_worktree(change_dir, args, cfg)
    maybe_delegate_to_worktree(change_dir, args)
    if args.offline:
        fail("Кодогенерация требует LLM (--offline не поддерживается для этого шага)")

    spec = (change_dir / "spec-bdd.md").read_text(encoding="utf-8")
    tasks = (change_dir / "tasks.md").read_text(encoding="utf-8")
    ctx = cfg["project_context"]
    system = SYSTEM_PROMPTS["codegen"].format(
        tech_stack=ctx["tech_stack"], code_style=ctx["code_style"],
        base_package=ctx.get("base_package", "app"),
        source_root=ctx.get("source_root", "src"))
    main_ctx = main_spec_context()
    sections = []
    if main_ctx:
        sections.append(main_ctx)
    sections.append("Спецификация spec-bdd.md:\n---\n{}\n---".format(spec.rstrip()))
    map_ctx = project_map_context(cfg)
    if map_ctx:
        sections.append(map_ctx)
    build_ctx = build_files_context(cfg)
    if build_ctx:
        sections.append(build_ctx)
    build_snapshot = snapshot_build_files(cfg)
    # Полный план нужен только одиночному запросу: в батчах вместо него идут
    # заголовки задач (план целиком) плюс полный текст задач своего шага.
    user = "\n\n".join(sections
                       + ["План tasks.md:\n---\n{}\n---".format(tasks.rstrip())])

    banner("Кодогенерация для {}".format(change_dir.name))
    settings = codegen_llm_settings(cfg)
    info("Модель: {} ({})".format(settings.model, settings.base_url))

    batch_size = args.batch if args.batch is not None else \
        (cfg.get("code_generation") or {}).get("batch_size", 0)
    task_blocks = split_tasks(tasks) if batch_size and batch_size > 0 else []

    if len(task_blocks) > batch_size > 0:
        written = codegen_batched(change_dir, settings, system, sections,
                                  task_blocks, batch_size, args)
    else:
        if batch_size and batch_size > 0:
            info("Задач меньше размера батча — генерирую одним запросом")
        written = codegen_single(change_dir, settings, system, user, args)

    if written is None:
        return  # dry-run или отмена пользователем

    mark_artifact(change_dir, "code-generation", "generated")
    check_build_files_intact(build_snapshot)

    if args.no_test:
        print()
        info("Тесты пропущены (--no-test).")
        return

    passed = run_tests(cfg)
    if passed is None:
        print()
        warn("Тесты не запускались: задай code_generation.test_command "
             "в .agent/config.yaml")
        return

    print()
    if not passed:
        info("Почини тесты, затем закоммить изменение:")
        print("  git add -A && git commit -m \"feat: {}\"".format(change_dir.name))
        return

    if args.no_commit:
        info("Коммит пропущен (--no-commit).")
        return
    if not in_change_worktree(change_dir):
        info("Тесты зелёные. Закоммить, когда будешь готов:")
        print("  git add -A && git commit -m \"feat: {}\"".format(change_dir.name))
        return

    # Мы внутри worktree изменения и тесты зелёные — коммитим, чтобы
    # в основном проекте было что мерджить.
    commit_generated_code(change_dir.name)
    git_meta = (load_change_meta(change_dir).get("git") or {})
    print()
    info("Изменение готово к мерджу. Из основного проекта:")
    print("  python3 .agent/run-agent.py --worktrees")
    print("  git merge {}".format(git_meta.get("branch", "<ветка>")))


# Объявления верхнего уровня: только то, что начинается с нулевой колонки —
# вложенные методы в карту не попадают, иначе она раздувается.
DECL_RE = re.compile(
    r"^(?:public |internal |private |protected |export |export default )?"
    r"(?:abstract |final |open |sealed |data |value |suspend |inline |static )*"
    r"(class|interface|object|enum class|enum|fun|def|type|struct|record)\s+"
    r"([A-Za-z_]\w*)",
    re.MULTILINE)

CODE_SUFFIXES = {".kt", ".java", ".py", ".ts", ".tsx", ".js", ".go", ".rs",
                 ".swift", ".cs", ".rb", ".php", ".scala"}
SKIP_DIRS = {"build", "out", "target", "node_modules", ".git", ".gradle",
             "__pycache__", ".venv", "venv", "dist", "generated"}
PROJECT_MAP_CHAR_BUDGET = 12000


def project_map_context(cfg: Dict) -> str:
    """Карта существующего кода: какие файлы есть и что в них объявлено.

    Без неё модель знает только спеку, план и то, что создала сама в этом
    прогоне. Код предыдущих изменений для неё не существует — и она заново
    создаёт уже имеющиеся сущности, второй NavHost, свои пакеты. Карта даёт
    ей увидеть проект целиком, оставаясь дешёвой: пути и объявления верхнего
    уровня, без тел функций.
    """
    gen = cfg.get("code_generation") or {}
    if not gen.get("project_map", True):
        return ""
    roots = gen.get("project_map_roots") or [
        (cfg.get("project_context") or {}).get("source_root", "src")]

    lines, used, skipped = [], 0, 0
    for root in roots:
        base = PROJECT_ROOT / root
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*")):
            if (not path.is_file() or path.suffix not in CODE_SUFFIXES
                    or SKIP_DIRS & set(path.parts)):
                continue
            rel = path.relative_to(PROJECT_ROOT)
            try:
                text = path.read_text(encoding="utf-8")
            except Exception:  # noqa: BLE001 — нечитаемый файл просто пропускаем
                continue
            names = []
            for kind, name in DECL_RE.findall(text):
                entry = "{} {}".format(kind, name)
                if entry not in names:
                    names.append(entry)
            entry = "- {}{}".format(
                rel, ": " + ", ".join(names[:12]) if names else "")
            if used + len(entry) > PROJECT_MAP_CHAR_BUDGET:
                skipped += 1
                continue
            used += len(entry)
            lines.append(entry)

    if not lines:
        return ""
    info("Карта проекта: файлов {}{}".format(
        len(lines), ", не влезло {}".format(skipped) if skipped else ""))
    tail = ("\n(ещё {} файлов не поместились)".format(skipped)) if skipped else ""
    return (
        "КАРТА ПРОЕКТА — код, который УЖЕ СУЩЕСТВУЕТ (путь: объявления "
        "верхнего уровня).\n"
        "Используй существующие сущности вместо создания своих: не заводи "
        "второй класс с тем же смыслом, не создавай параллельные пакеты, "
        "не дублируй точку входа или навигацию. Нужно изменить существующий "
        "файл — выведи его целиком по его текущему пути. Нужного файла тут "
        "нет — только тогда создавай новый.\n\n"
        + "\n".join(lines) + tail)


def snapshot_build_files(cfg: Dict) -> Dict[str, List[str]]:
    """Запомнить строки файлов сборки до кодогенерации."""
    paths = (cfg.get("code_generation") or {}).get("dependency_files") or []
    snapshot = {}
    for rel in paths:
        path = PROJECT_ROOT / rel
        if path.is_file():
            snapshot[rel] = path.read_text(encoding="utf-8").splitlines()
    return snapshot


def check_build_files_intact(snapshot: Dict[str, List[str]]) -> None:
    """Предупредить, если из файлов сборки пропали существовавшие строки.

    Модель выводит файл сборки целиком, и на перепечатывании теряет детали:
    в change-002 у артефактов androidx.lifecycle пропал префикс `lifecycle-`
    в поле name — файл остался синтаксически верным, а сборка развалилась
    на резолве зависимостей. Ловим такое до запуска тестов.
    """
    for rel, before in snapshot.items():
        path = PROJECT_ROOT / rel
        if not path.is_file():
            warn("файл сборки пропал после кодогенерации: {}".format(rel))
            continue
        after = set(line.strip() for line in
                    path.read_text(encoding="utf-8").splitlines())
        lost = [line for line in before
                if line.strip() and line.strip() not in after]
        if not lost:
            continue
        print()
        warn("В {} изменились или пропали строки, которые были раньше ({}):"
             .format(rel, len(lost)))
        for line in lost[:8]:
            print("    {}".format(line.strip()[:100]))
        if len(lost) > 8:
            print("    ... ещё {}".format(len(lost) - 8))
        info("Проверь: модель могла «причесать» файл и сломать имена "
             "артефактов. Вернуть прежнюю версию: git checkout -- {}"
             .format(rel))


def build_files_context(cfg: Dict) -> str:
    """Файлы сборки проекта — чтобы модель могла дописать в них зависимости."""
    paths = (cfg.get("code_generation") or {}).get("dependency_files") or []
    blocks, missing = [], []
    for rel in paths:
        path = PROJECT_ROOT / rel
        if not path.is_file():
            missing.append(rel)
            continue
        blocks.append("--- СОДЕРЖИМОЕ ФАЙЛА {} ---\n```\n{}\n```".format(
            rel, path.read_text(encoding="utf-8").rstrip()))
    for rel in missing:
        warn("dependency_files: {} не найден — пропущен".format(rel))
    if not blocks:
        return ""
    info("Файлы сборки в контексте: {}".format(
        ", ".join(p for p in paths if (PROJECT_ROOT / p).is_file())))
    return ("Файлы сборки проекта (актуальное содержимое). Если нужной "
            "библиотеки здесь нет — добавь и выведи файл целиком:\n\n"
            + "\n\n".join(blocks))


TASK_MARKER_RE = re.compile(r"^-\s*\[[^\]]*\]\s*\*\*T\d+", re.MULTILINE)


def split_tasks(tasks_md: str) -> List[str]:
    """Разбить tasks.md на блоки задач по маркерам '- [ ] **T<N>'."""
    matches = list(TASK_MARKER_RE.finditer(tasks_md))
    blocks = []
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(tasks_md)
        blocks.append(tasks_md[match.start():end].rstrip())
    return blocks


def collect_files(raw: str, change_dir: Path) -> List[tuple]:
    """Разобрать ответ LLM в список (относительный путь, итоговое содержимое).

    Патчи применяются здесь же: на выход всегда идёт готовый текст файла,
    поэтому запись, подтверждение и --dry-run работают одинаково для обоих
    форматов. Не применившийся патч не роняет весь батч — файл пропускается
    с понятным сообщением, остальные записываются.
    """
    valid = []
    for item in parse_files(raw):
        rel = safe_relative_path(item["path"])
        if rel is None:
            err("Отклонён небезопасный путь: {}".format(item["path"]))
            continue
        if item["kind"] == "full":
            valid.append((rel, item["content"]))
            continue
        try:
            valid.append((rel, apply_patch(PROJECT_ROOT / rel, item["edits"])))
            ok("патч применён: {} ({} правк{})".format(
                rel, len(item["edits"]), "а" if len(item["edits"]) == 1 else "и"))
        except ValueError as exc:
            err("Патч не применён к {}: {}".format(rel, exc))
    return valid


def write_files(valid: List[tuple]) -> None:
    for rel, content in valid:
        target = PROJECT_ROOT / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        ok("записан {}".format(rel))


def confirm_write(args: argparse.Namespace) -> bool:
    if args.yes:
        return True
    try:
        answer = input("\nЗаписать эти файлы? [y/N] ").strip().lower()
    except EOFError:
        answer = "n"
    if answer in ("y", "yes", "д", "да"):
        return True
    warn("Отменено пользователем")
    return False


def codegen_single(change_dir: Path, settings: "LLMSettings", system: str,
                   user: str, args: argparse.Namespace) -> Optional[List[tuple]]:
    """Кодогенерация одним запросом (историческое поведение)."""
    info("Генерирую (файлы появятся ниже по мере готовности)...")
    raw = llm_chat(settings, system, user, progress=True)
    (change_dir / "codegen-output.md").write_text(raw, encoding="utf-8")

    valid = collect_files(raw, change_dir)
    if not valid:
        fail("LLM не вернул ни одного блока '### FILE:'. "
             "Сырой ответ: specs/{}/codegen-output.md".format(change_dir.name))

    print()
    info("Файлы к записи ({}):".format(len(valid)))
    for rel, _ in valid:
        print("  - {}".format(rel))

    if args.dry_run:
        warn("--dry-run: файлы НЕ записаны")
        return None
    if not confirm_write(args):
        return None
    write_files(valid)
    return valid


# Бюджет на содержимое уже созданных файлов в промпте батча. Без него
# контекст растёт квадратично: к последнему батчу тянем всё написанное.
CONTEXT_CHAR_BUDGET = 24000


def generated_context(done: Dict[str, str]) -> str:
    """Контекст созданных файлов: пути всегда, содержимое — в пределах бюджета.

    Свежие файлы важнее старых: их сигнатуры нужны прямо сейчас. Поэтому
    содержимое включаем с конца, пока не упрёмся в бюджет; остальное
    остаётся в списке путей, чтобы модель знала об их существовании.
    """
    paths = list(done)
    listing = "\n".join("- {}".format(p) for p in sorted(paths))
    parts = ["Уже созданные файлы (не дублируй; выводи повторно только если "
             "задача требует их изменить):\n{}".format(listing)]

    included, budget = [], CONTEXT_CHAR_BUDGET
    for path in reversed(paths):          # словарь хранит порядок вставки
        content = done[path]
        if len(content) > budget:
            continue
        budget -= len(content)
        included.append(path)

    if included:
        parts.append("Содержимое (самые свежие файлы):\n{}".format(
            "\n\n".join("--- СОДЕРЖИМОЕ ФАЙЛА {} ---\n```\n{}\n```".format(p, done[p])
                        for p in included)))
    skipped = len(paths) - len(included)
    if skipped:
        parts.append("Содержимое ещё {} файлов не приложено — если понадобится "
                     "их изменить, опиши изменение целиком по пути."
                     .format(skipped))
    return "\n\n".join(parts)


def load_codegen_progress(change_dir: Path, batch_size: int,
                          task_count: int) -> tuple:
    """Прогресс прошлого запуска: (сколько батчей готово, карта файлов).

    Прогресс годен, только если план и размер батча не менялись — иначе
    границы батчей сместятся и продолжать будет нечего.
    """
    meta = (load_change_meta(change_dir).get("codegen") or {})
    if (meta.get("batch_size") != batch_size
            or meta.get("task_count") != task_count):
        return 0, {}
    done_batches = int(meta.get("done_batches") or 0)
    if done_batches <= 0:
        return 0, {}

    files: Dict[str, str] = {}
    for rel in meta.get("files") or []:
        path = PROJECT_ROOT / rel
        if not path.is_file():
            warn("файл из прошлого запуска пропал: {} — начинаю заново"
                 .format(rel))
            return 0, {}
        files[rel] = path.read_text(encoding="utf-8")
    return done_batches, files


def save_codegen_progress(change_dir: Path, batch_size: int, task_count: int,
                          done_batches: int, files: List[str]) -> None:
    meta = load_change_meta(change_dir)
    meta["codegen"] = {
        "batch_size": batch_size,
        "task_count": task_count,
        "done_batches": done_batches,
        "files": files,
    }
    meta["updated"] = now_str()
    save_change_meta(change_dir, meta)


def codegen_batched(change_dir: Path, settings: "LLMSettings", system: str,
                    sections: List[str], task_blocks: List[str], batch_size: int,
                    args: argparse.Namespace) -> Optional[List[tuple]]:
    """Кодогенерация порциями задач: каждый батч — свой запрос и своя запись.

    Зачем: план на 10+ задач в один вызов не помещается — модель обрывает
    файлы на середине или молча пропускает задачи. Здесь каждый шаг получает
    свои задачи плюс уже сгенерированные файлы как контекст, и результат
    пишется на диск сразу, не дожидаясь конца.
    """
    batches = [task_blocks[i:i + batch_size]
               for i in range(0, len(task_blocks), batch_size)]
    info("Задач в плане: {} → батчей по {}: {}".format(
        len(task_blocks), batch_size, len(batches)))

    plan_outline = "Полный план (заголовки задач):\n{}".format(
        "\n".join(block.splitlines()[0].strip() for block in task_blocks))

    # Прогресс прошлого запуска: пропускаем уже сделанные батчи, а контекст
    # для них поднимаем с диска — файлы там уже лежат.
    done: Dict[str, str] = {}
    start_index = 0
    if not args.dry_run and not getattr(args, "fresh", False):
        start_index, done = load_codegen_progress(change_dir, batch_size,
                                                  len(task_blocks))
        if start_index >= len(batches):
            # Все батчи уже отработали — продолжать нечего, это повторный
            # прогон по тому же плану.
            warn("Прошлый запуск отработал план целиком — генерирую заново")
            start_index, done = 0, {}
        elif start_index:
            info("Найден прогресс прошлого запуска: батчей готово {} из {}"
                 .format(start_index, len(batches)))
            info("Начинаю с батча {}. Всё заново: флаг --fresh"
                 .format(start_index + 1))

    if not args.dry_run and not confirm_write(args):
        return None

    raw_log: List[str] = []

    for index, batch in enumerate(batches, start=1):
        if index <= start_index:
            continue
        print()
        banner("Батч {}/{}".format(index, len(batches)))
        for block in batch:
            print("  {}".format(block.splitlines()[0][:100]))

        parts = list(sections)
        parts.append(plan_outline)
        parts.append("Задачи ЭТОГО шага — реализуй только их:\n---\n{}\n---"
                     .format("\n\n".join(batch)))
        if done:
            parts.append(generated_context(done))

        raw = llm_chat(settings, system, "\n\n".join(parts), progress=True)
        raw_log.append("## Батч {}/{}\n\n{}".format(index, len(batches), raw))

        valid = collect_files(raw, change_dir)
        if not valid:
            # Дальше идти нельзя: следующие батчи опираются на код этого.
            # Прогресс остался на предыдущем батче — повторный запуск
            # продолжит именно отсюда, а не проскочит дырку.
            (change_dir / "codegen-output.md").write_text(
                "\n\n---\n\n".join(raw_log), encoding="utf-8")
            err("Батч {} не вернул ни одного файла.".format(index))
            print()
            info("Частая причина: модель израсходовала max_tokens на "
                 "размышления и не дошла до ответа.")
            print("  - подними max_tokens в .agent/config.yaml")
            print("  - или отключи размышления: ai_settings.extra_params")
            print("  - или уменьши размер батча: --batch 2")
            print()
            info("Прогресс сохранён на батче {}. Повторный запуск "
                 "продолжит с батча {}.".format(index - 1, index))
            fail("Кодогенерация остановлена. Сырой ответ: "
                 "specs/{}/codegen-output.md".format(change_dir.name))

        for rel, content in valid:
            done[str(rel)] = content   # строкой: путь уходит в change.yaml
        print()
        info("Файлы батча ({}):".format(len(valid)))
        for rel, _ in valid:
            print("  - {}".format(rel))
        if args.dry_run:
            warn("--dry-run: не записаны")
        else:
            write_files(valid)
            # Прогресс после каждого батча: прерванный кодоген можно
            # продолжить, а не гонять всё заново.
            save_codegen_progress(change_dir, batch_size, len(task_blocks),
                                  index, sorted(done))

    (change_dir / "codegen-output.md").write_text(
        "\n\n---\n\n".join(raw_log), encoding="utf-8")

    if not done:
        fail("Ни один батч не вернул файлов. "
             "Сырой ответ: specs/{}/codegen-output.md".format(change_dir.name))

    print()
    info("Всего файлов: {}".format(len(done)))
    if args.dry_run:
        warn("--dry-run: файлы НЕ записаны")
        return None
    return sorted(done.items())


def in_change_worktree(change_dir: Path) -> bool:
    """Мы сейчас в том самом worktree, к которому привязано изменение?"""
    target = (load_change_meta(change_dir).get("git") or {}).get("worktree")
    if not target:
        return False
    return Path(target).resolve() == PROJECT_ROOT.resolve()


def run_tests(cfg: Dict) -> Optional[bool]:
    """Прогнать тесты проекта. None — команда не задана, иначе результат."""
    command = (cfg.get("code_generation") or {}).get("test_command")
    if not command:
        return None
    print()
    banner("Тесты: {}".format(command))
    result = subprocess.run(command, shell=True, cwd=str(PROJECT_ROOT))
    if result.returncode == 0:
        ok("тесты зелёные")
        return True
    warn("тесты упали (код возврата {})".format(result.returncode))
    return False


def commit_generated_code(change_id: str) -> None:
    """Закоммитить сгенерированный код в ветке worktree."""
    if not is_git_repo():
        warn("Не git-репозиторий — коммит пропущен")
        return
    if git("add", "-A").returncode != 0:
        warn("git add не удался — коммит пропущен")
        return
    result = git("commit", "-m", "feat: {}".format(change_id))
    output = result.stdout + result.stderr
    if result.returncode == 0:
        ok("закоммичено: feat: {}".format(change_id))
    elif "nothing to commit" in output:
        warn("нечего коммитить — рабочее дерево чистое")
    else:
        warn("коммит не удался: {}".format(output.strip()))


# ---------------------------------------------------------------------------
# Информационные команды
# ---------------------------------------------------------------------------

def cmd_list(_: argparse.Namespace) -> None:
    """--list: показать все изменения."""
    changes = list_changes()
    if not changes:
        info("Изменений пока нет. Создай первое:")
        print('  python .agent/run-agent.py --new "опиши фичу"')
        return
    banner("Изменения (specs/)")
    for path in changes:
        meta = load_change_meta(path)
        print("  {}  {}".format(_c("1", path.name),
                                meta.get("prompt", "")[:60]))


def cmd_status(args: argparse.Namespace) -> None:
    """--status: какие артефакты изменения уже сгенерированы."""
    schema = load_schema()
    changes = ([find_change(args.change)] if args.change else list_changes())

    main_path = main_spec_path()
    print("\n  [{}] {:<14} {}".format(
        "✔" if main_path.exists() else "—", "main-spec",
        main_path.relative_to(PROJECT_ROOT)))
    if not main_path.exists():
        warn("создай: python .agent/run-agent.py --main \"что за приложение\"")

    if not changes:
        if not args.change:
            info("Изменений нет")
        return
    for change_dir in changes:
        print("\n" + _c("1", change_dir.name))
        for artifact in schema:
            path = artifact_path(artifact, change_dir.name)
            if path is None:  # code-generation: смотрим метаданные
                status = load_change_meta(change_dir).get("status", {}).get(artifact.id)
                mark = "✔" if status == "generated" else "—"
                print("  [{}] {}".format(mark, artifact.id))
                continue
            mark = "✔" if path.exists() else "—"
            rel = path.relative_to(PROJECT_ROOT)
            print("  [{}] {:<14} {}".format(mark, artifact.id, rel))


def cmd_regenerate(args: argparse.Namespace, cfg: Dict) -> None:
    """--proposal/--spec/--tasks: перегенерировать один артефакт."""
    artifact_id = ("proposal" if args.proposal
                   else "spec-bdd" if args.spec else "tasks")
    change_dir = find_change(args.change)
    meta = load_change_meta(change_dir)
    prompt = meta.get("prompt") or fail_meta_prompt(change_dir)
    banner("Перегенерация {} для {}".format(artifact_id, change_dir.name))
    generate_artifact(artifact_id, change_dir, prompt, cfg, args.offline)


def fail_meta_prompt(change_dir: Path) -> str:
    fail("В specs/{}/change.yaml не сохранён исходный промпт — "
         "укажи его в поле prompt".format(change_dir.name))
    return ""  # unreachable


# ---------------------------------------------------------------------------
# --init: развернуть .agent/ в новом проекте
# ---------------------------------------------------------------------------

INIT_FILES = {
    "config.yaml": CONFIG_PATH,
    "schema.yaml": SCHEMA_PATH,
    "templates/main.template": TEMPLATES_DIR / "main.template",
    "templates/proposal.template": TEMPLATES_DIR / "proposal.template",
    "templates/spec.template": TEMPLATES_DIR / "spec.template",
    "templates/tasks.template": TEMPLATES_DIR / "tasks.template",
}


def cmd_init(_: argparse.Namespace) -> None:
    """--init: создать недостающие файлы структуры .agent/ (существующие не трогаем)."""
    banner("Инициализация .agent/")
    for rel, target in INIT_FILES.items():
        if target.exists():
            ok("{} уже существует — пропущен".format(rel))
            continue
        source = AGENT_DIR / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")
        ok("создан {}".format(rel))
    SPECS_DIR.mkdir(exist_ok=True)
    ok("папка specs/ готова")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="run-agent.py",
        description="OpenSpec Change Driver: MAIN Spec -> Промпт -> Proposal -> BDD Spec -> Tasks -> Code",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""Примеры:
  python .agent/run-agent.py --main "сервис уведомлений, Kotlin + Spring Boot"
  python .agent/run-agent.py --new "хочу эхо-сервис с кодированием в base64"
  python .agent/run-agent.py --list
  python .agent/run-agent.py --status --change 1
  python .agent/run-agent.py --spec --change 1            # перегенерировать спеку
  python .agent/run-agent.py --codegen --change 1 --dry-run

Параллельная работа (worktree):
  python .agent/run-agent.py --new "вторая фича" --worktree
  python .agent/run-agent.py --worktrees                  # что ещё не смерджено
""")
    parser.add_argument("--main", metavar="PROMPT",
                        help="сгенерировать/обновить мейн-спеку проекта "
                             "(specs/MAIN.md): что за приложение и какой стек")
    parser.add_argument("--new", metavar="PROMPT",
                        help="новое изменение: папка + proposal + spec-bdd + tasks")
    parser.add_argument("--change", metavar="ID",
                        help="ID изменения (полный, префикс или номер: change-001-..., change-001, 1)")
    parser.add_argument("--codegen", action="store_true",
                        help="шаг генерации кода (нужен --change)")
    parser.add_argument("--proposal", action="store_true",
                        help="перегенерировать proposal.md (нужен --change)")
    parser.add_argument("--spec", action="store_true",
                        help="перегенерировать spec-bdd.md (нужен --change)")
    parser.add_argument("--tasks", action="store_true",
                        help="перегенерировать tasks.md (нужен --change)")
    parser.add_argument("--list", action="store_true", help="список изменений")
    parser.add_argument("--status", action="store_true",
                        help="статус артефактов (всех или одного --change)")
    parser.add_argument("--offline", action="store_true",
                        help="не вызывать LLM — каркасы документов по шаблонам")
    parser.add_argument("--dry-run", action="store_true",
                        help="(codegen) показать файлы, но не записывать")
    parser.add_argument("--yes", "-y", action="store_true",
                        help="(codegen) не спрашивать подтверждение")
    parser.add_argument("--model", metavar="NAME",
                        help="модель на один запуск (переопределяет config.yaml)")
    parser.add_argument("--profile", metavar="NAME",
                        help="профиль провайдера из config.yaml (work / glm / home): "
                             "host + ключ + модель одним флагом")
    parser.add_argument("--branch", action="store_true",
                        help="(--new) создать git-ветку spec/<change-id>")
    parser.add_argument("--worktree", action="store_true",
                        help="закоммитить спеку и создать отдельный git worktree "
                             "с веткой change/<NNN-slug> — для параллельной "
                             "работы. С --new: сразу при создании изменения. "
                             "С --change <ID>: для уже существующего")
    parser.add_argument("--worktrees", action="store_true",
                        help="список worktree изменений: ветка, база и что "
                             "из них ещё не смерджено")
    parser.add_argument("--here", action="store_true",
                        help="(codegen) не переходить в worktree изменения — "
                             "работать в текущей директории")
    parser.add_argument("--batch", metavar="N", type=int,
                        help="(codegen) генерировать порциями по N задач из "
                             "tasks.md вместо одного большого запроса "
                             "(переопределяет code_generation.batch_size)")
    parser.add_argument("--fresh", action="store_true",
                        help="(codegen) игнорировать прогресс прошлого запуска "
                             "и сгенерировать все батчи заново")
    parser.add_argument("--no-test", action="store_true",
                        help="(codegen) не запускать тесты после записи файлов")
    parser.add_argument("--no-commit", action="store_true",
                        help="(codegen) не коммитить автоматически, даже если "
                             "тесты прошли")
    parser.add_argument("--init", action="store_true",
                        help="развернуть структуру .agent/ в этом проекте")
    return parser


def main(argv: Optional[List[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    cfg = load_config()
    if args.profile:
        profile = cfg.get("profiles", {}).get(args.profile)
        if profile is None:
            available = ", ".join(sorted(cfg.get("profiles", {}))) or "нет"
            fail("--profile '{}': профиль не найден в config.yaml (доступны: {})"
                 .format(args.profile, available))
        cfg["ai_settings"] = _deep_merge(cfg["ai_settings"], profile)
        info("Профиль '{}': model={}, base_url={}".format(
            args.profile, cfg["ai_settings"]["model"],
            cfg["ai_settings"]["base_url"]))
    if args.model:
        cfg["ai_settings"]["model"] = args.model
        info("Модель на этот запуск: {}".format(args.model))

    if args.init:
        cmd_init(args)
        return 0
    if args.main:
        cmd_main(args, cfg)
        return 0
    if args.list:
        cmd_list(args)
        return 0
    if args.worktrees:
        cmd_worktrees(args)
        return 0
    if args.worktree and args.change and not args.new:
        # Завести worktree для уже существующего изменения.
        change_dir = find_change(args.change)
        banner("Worktree для {}".format(change_dir.name))
        setup_worktree(change_dir.name, change_dir, cfg)
        return 0
    if args.status:
        cmd_status(args)
        return 0
    if args.new:
        cmd_new(args, cfg)
        return 0
    if args.codegen:
        if not args.change:
            fail("--codegen требует --change <ID> (список: --list)")
        cmd_codegen(args, cfg)
        return 0
    if args.proposal or args.spec or args.tasks:
        if not args.change:
            fail("этой команде нужен --change <ID> (список: --list)")
        cmd_regenerate(args, cfg)
        return 0

    build_parser().print_help()
    return 1


if __name__ == "__main__":
    sys.exit(main())
