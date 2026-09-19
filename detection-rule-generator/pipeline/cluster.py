from __future__ import annotations

import re

from .config import PT_SUBTYPES, SQLI_SUBTYPES, XSS_SUBTYPES

# Taxonomy-first buckets. Order matters: first match wins.
_SQLI_RULES: list[tuple[str, re.Pattern[str]]] = [
    ("TIME_BASED", re.compile(r"sleep|pg_sleep|benchmark|waitfor|dbms_pipe", re.I)),
    ("UNION", re.compile(r"\bunion\b.{0,40}\bselect\b", re.I)),
    (
        "STACKED",
        re.compile(
            r";\s*(select|insert|update|delete|drop|create|alter|exec|waitfor)\b",
            re.I,
        ),
    ),
    (
        "BOOLEAN_TAUTOLOGY",
        re.compile(
            r"(\bor\b|\band\b).{0,24}(=\s*\S|\blike\b|<>|!=|'[^']*'\s*=\s*'[^']*')",
            re.I,
        ),
    ),
    ("COMMENT_EVASION", re.compile(r"(--|#|/\*|\*/)")),
]

_XSS_RULES: list[tuple[str, re.Pattern[str]]] = [
    ("ENCODED", re.compile(r"%3c|%3e|%253c|&#x?3c|\\x3c|\\u003c", re.I)),
    ("SCRIPT_TAG", re.compile(r"<\s*script\b|<\s*svg\b|<\s*iframe\b|<\s*img\b", re.I)),
    (
        "EVENT_HANDLER",
        re.compile(r"\bon(?:error|load|click|mouseover|focus|submit|mouseenter)\b", re.I),
    ),
    ("JS_URI", re.compile(r"javascript\s*:|vbscript\s*:|data\s*:\s*text/html", re.I)),
]

_PT_RULES: list[tuple[str, re.Pattern[str]]] = [
    ("DOUBLE_ENCODED", re.compile(r"%25(?:2e|2f|5c)", re.I)),
    ("URL_ENCODED", re.compile(r"%2e%2e|%2e/|%2f\.\.|%5c\.\.", re.I)),
    ("BACKSLASH", re.compile(r"\.\.\\")),
    ("DOTDOT_SLASH", re.compile(r"\.\./")),
]


def _first_match(text: str, rules: list[tuple[str, re.Pattern[str]]], fallback: str) -> str:
    for name, pattern in rules:
        if pattern.search(text):
            return name
    return fallback


def classify_subtype(category: str, canonical_payload: str, raw_payload: str = "") -> str:
    text = canonical_payload or ""
    raw = raw_payload or text
    if category == "SQLI":
        subtype = _first_match(text, _SQLI_RULES, "OTHER")
        return subtype if subtype in SQLI_SUBTYPES else "OTHER"
    if category == "XSS":
        if re.search(r"%3c|%253c|&#x?3c|\\x3c|\\u003c", raw, re.I):
            return "ENCODED"
        subtype = _first_match(text, _XSS_RULES, "OTHER")
        return subtype if subtype in XSS_SUBTYPES else "OTHER"
    if category == "PATH_TRAVERSAL":
        if re.search(r"%25(?:2e|2f|5c)", raw, re.I):
            return "DOUBLE_ENCODED"
        if re.search(r"%2e%2e|%2e/|%2f\.\.|%5c\.\.", raw, re.I):
            return "URL_ENCODED"
        subtype = _first_match(text, _PT_RULES, "OTHER")
        return subtype if subtype in PT_SUBTYPES else "OTHER"
    return "OTHER"
