"""Java-safe regex templates keyed by abstract type / subcategory.

Encoding-aware fragments so compiled rules can hit Nginx `args`/`uri`
as logged (percent-encoding) as well as plaintext payloads.
"""

from __future__ import annotations

# Flexible whitespace / separators as they appear on access logs.
_WS = r"(?:\s+|%20|\+)"
_EQ = r"(?:=|%3d)"
_LT = r"(?:<|%3c|%253c)"
_GT = r"(?:>|%3e)"
_SQ = r"(?:'|%27)"
_COLON = r"(?::|%3a)"
_SLASH = r"(?:/|%2f|%252f)"
_DOT = r"(?:\.|%2e|%252e)"
_BSLASH = r"(?:\\+|%5c|%255c)"

TEMPLATES: dict[str, dict] = {
    "BOOLEAN_TAUTOLOGY": {
        "pattern": rf"(?i)(\bor\b|\band\b){_WS}+\d+{_WS}*{_EQ}{_WS}*\d+",
        "name": "Boolean tautology in query",
        "description": "Detects classic boolean-based SQLi tautologies such as OR/AND n=n in query string.",
        "weight": 40,
        "target_field": "query",
        "category": "SQLI",
        "code": "SQLI-BOOLEAN-001",
    },
    "BOOLEAN_STRING": {
        "pattern": rf"(?i)(\bor\b|\band\b){_WS}*{_SQ}[^']{{0,32}}{_SQ}{_WS}*{_EQ}{_WS}*{_SQ}",
        "name": "String equality tautology",
        "description": "Detects quoted string tautologies such as 'a'='a' after OR/AND.",
        "weight": 40,
        "target_field": "query",
        "category": "SQLI",
        "code": "SQLI-BOOLEAN-002",
    },
    "UNION": {
        "pattern": rf"(?i)\bunion\b{_WS}+(?:all{_WS}+)?\bselect\b",
        "name": "UNION SELECT injection",
        "description": "Detects UNION SELECT used to append an attacker query in query/path.",
        "weight": 55,
        "target_field": "query",
        "category": "SQLI",
        "code": "SQLI-UNION-001",
    },
    "COMMENT_EVASION": {
        "pattern": rf"(?i){_SQ}{_WS}*(?:--|#|%23|/\*)",
        "name": "SQL comment after quote",
        "description": "Detects quote-then-comment evasion ('-- , '# , '/*) that short-circuits the rest of a SQL statement.",
        "weight": 35,
        "target_field": "query",
        "category": "SQLI",
        "code": "SQLI-COMMENT-001",
    },
    "STACKED": {
        "pattern": rf"(?i);{_WS}*(select|insert|update|delete|drop|alter|exec|waitfor)\b",
        "name": "Stacked SQL statement",
        "description": "Detects stacked queries that start a second SQL statement after a semicolon.",
        "weight": 50,
        "target_field": "query",
        "category": "SQLI",
        "code": "SQLI-STACKED-001",
    },
    "TIME_BASED": {
        "pattern": rf"(?i)(?:(?:sleep|pg_sleep|benchmark)\s*\(|waitfor{_WS}+delay)",
        "name": "Time-based SQL delay",
        "description": "Detects time-based SQLi primitives (SLEEP / pg_sleep / BENCHMARK / WAITFOR DELAY).",
        "weight": 60,
        "target_field": "query",
        "category": "SQLI",
        "code": "SQLI-TIME-001",
    },
    "SCRIPT_TAG": {
        "pattern": rf"(?i){_LT}{_WS}*(script|iframe|svg)\b",
        "name": "HTML script/iframe/svg tag",
        "description": "Detects injected <script>, <iframe> or <svg> tags, including %3c encoding.",
        "weight": 55,
        "target_field": "query",
        "category": "XSS",
        "code": "XSS-SCRIPT-001",
    },
    "EVENT_HANDLER": {
        "pattern": rf"(?i){_LT}[^>]{{0,80}}on(?:error|load|click|mouseover|focus|submit)\s*{_EQ}",
        "name": "HTML event-handler XSS",
        "description": "Detects event-handler XSS such as <img ... onerror=...> in query values.",
        "weight": 45,
        "target_field": "query",
        "category": "XSS",
        "code": "XSS-EVENT-001",
    },
    "JS_URI": {
        "pattern": rf"(?i)(?:javascript|vbscript){_WS}*{_COLON}",
        "name": "javascript: URI XSS",
        "description": "Detects javascript: / vbscript: URIs used as XSS vectors in query or path.",
        "weight": 50,
        "target_field": "query",
        "category": "XSS",
        "code": "XSS-JSURI-001",
    },
    "ENCODED": {
        "pattern": rf"(?i)(?:%3c|%253c)\s*(?:script|img|svg|iframe|body)\b",
        "name": "Percent-encoded XSS tag",
        "description": "Detects percent-encoded opening tags (%3cscript, %3cimg) typical of query-string XSS.",
        "weight": 40,
        "target_field": "query",
        "category": "XSS",
        "code": "XSS-ENCODED-001",
    },
    "DOTDOT_SLASH": {
        "pattern": rf"(?i)(?:\.\./|\.\.\\|{_DOT}{_DOT}{_SLASH})",
        "name": "Dot-dot slash traversal",
        "description": "Detects ../ and encoded %2e%2e%2f path traversal sequences.",
        "weight": 45,
        "target_field": "path",
        "category": "PATH_TRAVERSAL",
        "code": "PT-DOTDOT-001",
    },
    "URL_ENCODED": {
        "pattern": rf"(?i)(?:%2e%2e(?:%2f|%5c|/|\\)|%2e%2e/)",
        "name": "URL-encoded path traversal",
        "description": "Detects %2e%2e%2f / %2e%2e%5c traversal encodings on path or query.",
        "weight": 50,
        "target_field": "path",
        "category": "PATH_TRAVERSAL",
        "code": "PT-ENCODED-001",
    },
    "DOUBLE_ENCODED": {
        "pattern": rf"(?i)%252e%252e(?:%252f|%252e|%255c|%2f)",
        "name": "Double-encoded path traversal",
        "description": "Detects double-encoded traversal such as %252e%252e%252f.",
        "weight": 55,
        "target_field": "path",
        "category": "PATH_TRAVERSAL",
        "code": "PT-DOUBLE-001",
    },
    "BACKSLASH": {
        "pattern": rf"(?i)\.\.(?:\\+|%5c+)|%2e%2e%5c",
        "name": "Windows backslash traversal",
        "description": "Detects ..\\ and %2e%2e%5c Windows-style path traversal.",
        "weight": 45,
        "target_field": "path",
        "category": "PATH_TRAVERSAL",
        "code": "PT-BACKSLASH-001",
    },
}

TYPE_ALIASES = {
    "BOOLEAN_BASED": "BOOLEAN_TAUTOLOGY",
    "BOOLEAN": "BOOLEAN_TAUTOLOGY",
    "UNION_BASED": "UNION",
    "UNION_SELECT": "UNION",
    "COMMENT": "COMMENT_EVASION",
    "STACKED_QUERY": "STACKED",
    "TIME": "TIME_BASED",
    "SLEEP": "TIME_BASED",
    "SCRIPT": "SCRIPT_TAG",
    "EVENT": "EVENT_HANDLER",
    "JSURI": "JS_URI",
    "JAVASCRIPT_URI": "JS_URI",
    "XSS_ENCODED": "ENCODED",
    "DOTDOT": "DOTDOT_SLASH",
    "TRAVERSAL": "DOTDOT_SLASH",
    "DOUBLE": "DOUBLE_ENCODED",
    "PT_ENCODED": "URL_ENCODED",
}


def resolve_template_key(abstract: dict) -> str | None:
    for field in ("type", "subcategory", "id"):
        raw = str(abstract.get(field, "")).upper()
        if raw in TEMPLATES:
            return raw
        if raw in TYPE_ALIASES:
            return TYPE_ALIASES[raw]
        for key in TEMPLATES:
            if key in raw:
                return key
    conditions = " ".join(abstract.get("conditions") or []).upper()
    mapping = (
        ("TAUTOLOGY", "BOOLEAN_TAUTOLOGY"),
        ("UNION", "UNION"),
        ("SLEEP", "TIME_BASED"),
        ("WAITFOR", "TIME_BASED"),
        ("STACKED", "STACKED"),
        ("COMMENT", "COMMENT_EVASION"),
        ("SCRIPT", "SCRIPT_TAG"),
        ("EVENT", "EVENT_HANDLER"),
        ("JAVASCRIPT", "JS_URI"),
        ("DOUBLE", "DOUBLE_ENCODED"),
        ("BACKSLASH", "BACKSLASH"),
        ("PERCENT", "URL_ENCODED"),
        ("TRAVERSAL", "DOTDOT_SLASH"),
        ("DOTDOT", "DOTDOT_SLASH"),
    )
    for needle, key in mapping:
        if needle in conditions:
            return key
    return None
