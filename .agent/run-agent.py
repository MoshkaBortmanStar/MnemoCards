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
import subprocess
import sys
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
Ты — Senior Java Developer. Твоя задача — написать код, строго
соответствующий спецификации spec-bdd.md и плану tasks.md (переданы ниже).

Контекст проекта:
{tech_stack}
{code_style}
Базовый java-пакет: {base_package}

Порядок работы:
1. Сначала сгенерируй интеграционные тесты (MockMvc + JUnit 5),
   воспроизводящие сценарии GIVEN-WHEN-THEN из спецификации.
2. Затем сгенерируй код контроллера/сервиса, чтобы тесты прошли успешно.
3. Соблюдай код-стайл проекта. Не сокращай код — выводи файлы целиком.

ФОРМАТ ОТВЕТА — строго для каждого файла:

### FILE: <путь/от/корня/проекта/ИмяФайла.java>
```java
<полное содержимое файла>
```

Требования к путям: только относительные пути от корня проекта
(например src/main/java/...). Никаких абсолютных путей. Все завершающие
комментарии и пояснения вне блоков FILE запрещены.""",
}


# ---------------------------------------------------------------------------
# Конфигурация и схема
# ---------------------------------------------------------------------------

DEFAULT_CONFIG: Dict = {
    "project_context": {
        "tech_stack": "Java 17, Spring Boot 3.x, Maven, JUnit 5, MockMvc",
        "code_style": "Conventional Commits, интерфейсы для контроллеров (API-first)",
        "base_package": "com.example",
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
    )


def llm_chat(settings: LLMSettings, system: str, user: str) -> str:
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

    last_error = None
    for attempt in range(1, 4):  # 3 попытки
        try:
            response = requests.post(url, json=payload, headers=headers,
                                     timeout=settings.timeout,
                                     verify=settings.verify_ssl)
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            return clean_llm_output(content)
        except Exception as exc:  # noqa: BLE001 — показываем пользователю любую
            last_error = exc
            warn("Попытка {} из 3 не удалась: {}".format(attempt, exc))
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
# Кодогенерация (Tasks -> Code)
# ---------------------------------------------------------------------------

FILE_BLOCK_RE = re.compile(
    r"###\s*FILE:\s*(?P<path>[^\s`]+)\s*\n+```[^\n]*\n(?P<body>.*?)\n?```",
    re.DOTALL,
)


def parse_files(raw: str) -> List[Dict[str, str]]:
    """Разобрать ответ LLM на список файлов: [{'path': ..., 'content': ...}]."""
    files = []
    for match in FILE_BLOCK_RE.finditer(raw):
        files.append({"path": match.group("path").strip(),
                      "content": match.group("body").rstrip() + "\n"})
    return files


def safe_relative_path(raw_path: str) -> Optional[Path]:
    """Валидация пути: только относительный, без выхода за корень проекта."""
    path = Path(raw_path)
    if path.is_absolute() or ".." in path.parts or not str(path).strip():
        return None
    return path


def cmd_codegen(args: argparse.Namespace, cfg: Dict) -> None:
    """--codegen: LLM читает спеку+таски и пишет файлы в проект."""
    change_dir = find_change(args.change)
    artifact = next(a for a in load_schema() if a.id == "code-generation")
    check_requirements(artifact, change_dir.name)
    if args.offline:
        fail("Кодогенерация требует LLM (--offline не поддерживается для этого шага)")

    spec = (change_dir / "spec-bdd.md").read_text(encoding="utf-8")
    tasks = (change_dir / "tasks.md").read_text(encoding="utf-8")
    ctx = cfg["project_context"]
    system = SYSTEM_PROMPTS["codegen"].format(
        tech_stack=ctx["tech_stack"], code_style=ctx["code_style"],
        base_package=ctx.get("base_package", "com.example"))
    main_ctx = main_spec_context()
    sections = []
    if main_ctx:
        sections.append(main_ctx)
    sections.append("Спецификация spec-bdd.md:\n---\n{}\n---".format(spec.rstrip()))
    sections.append("План tasks.md:\n---\n{}\n---".format(tasks.rstrip()))
    user = "\n\n".join(sections)

    banner("Кодогенерация для {}".format(change_dir.name))
    settings = load_llm_settings(cfg)
    info("Модель: {} ({})".format(settings.model, settings.base_url))
    raw = llm_chat(settings, system, user)

    # Сырой ответ сохраняем для аудита.
    (change_dir / "codegen-output.md").write_text(raw, encoding="utf-8")

    files = parse_files(raw)
    if not files:
        fail("LLM не вернул ни одного блока '### FILE:'. "
             "Сырой ответ: specs/{}/codegen-output.md".format(change_dir.name))

    valid, skipped = [], []
    for item in files:
        rel = safe_relative_path(item["path"])
        if rel is None:
            skipped.append(item["path"])
        else:
            valid.append((rel, item["content"]))

    for bad_path in skipped:
        err("Отклонён небезопасный путь: {}".format(bad_path))

    print()
    info("Файлы к записи ({}):".format(len(valid)))
    for rel, _ in valid:
        print("  - {}".format(rel))

    if args.dry_run:
        warn("--dry-run: файлы НЕ записаны")
        return

    if not args.yes:
        try:
            answer = input("\nЗаписать эти файлы? [y/N] ").strip().lower()
        except EOFError:
            answer = "n"
        if answer not in ("y", "yes", "д", "да"):
            warn("Отменено пользователем")
            return

    for rel, content in valid:
        target = PROJECT_ROOT / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        ok("записан {}".format(rel))

    mark_artifact(change_dir, "code-generation", "generated")
    print()
    info("Готово. Проверь код и запусти тесты, например: mvn test")


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
