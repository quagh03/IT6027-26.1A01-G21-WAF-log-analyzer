from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

DATA = ROOT / "data"
RAW = DATA / "raw"
UPSTREAM = RAW / "_upstream"
NORMALIZED = DATA / "normalized"
CLUSTERED = DATA / "clustered"
EVALUATION = DATA / "evaluation"

AI = ROOT / "ai"
PROMPTS = AI / "prompts"
AI_OUTPUTS = AI / "outputs"

RULES = ROOT / "rules"
CANDIDATES = RULES / "candidates"
REVIEWED = RULES / "reviewed"
PRODUCTION = RULES / "production"

EVALUATOR = ROOT / "evaluator"
POSITIVE_TESTS = EVALUATOR / "positive-tests"
NEGATIVE_TESTS = EVALUATOR / "negative-tests"
REPORTS = EVALUATOR / "reports"
EXPORT = ROOT / "export"

CATEGORIES = ("SQLI", "XSS", "PATH_TRAVERSAL")
LABEL_ATTACK = "attack"
LABEL_BENIGN = "benign"

SQLI_SUBTYPES = (
    "BOOLEAN_TAUTOLOGY",
    "UNION",
    "COMMENT_EVASION",
    "STACKED",
    "TIME_BASED",
    "OTHER",
)
XSS_SUBTYPES = (
    "SCRIPT_TAG",
    "EVENT_HANDLER",
    "JS_URI",
    "ENCODED",
    "OTHER",
)
PT_SUBTYPES = (
    "DOTDOT_SLASH",
    "URL_ENCODED",
    "DOUBLE_ENCODED",
    "BACKSLASH",
    "OTHER",
)

GENERATION_FRAC = 0.70
RANDOM_SEED = 42
CLUSTER_POS_EXAMPLES = 12
CLUSTER_NEG_EXAMPLES = 8

FPR_GATE = 0.02
PRECISION_GATE = 0.80
MIN_HELD_OUT_HITS = 1

RULE_VERSION = "1.0.0"
DEFAULT_TARGET_FIELD = {
    "SQLI": "query",
    "XSS": "query",
    "PATH_TRAVERSAL": "path",
}

INGESTED_CSV = RAW / "ingested.csv"
NORMALIZED_CSV = NORMALIZED / "payloads.csv"
DEDUPED_CSV = NORMALIZED / "deduped.csv"
GENERATION_CSV = EVALUATION / "generation.csv"
HELD_OUT_CSV = EVALUATION / "held_out.csv"
BENIGN_CSV = EVALUATION / "benign.csv"
ADVERSARIAL_CSV = EVALUATION / "adversarial.csv"

HTTP_PARAMS_URL = (
    "https://raw.githubusercontent.com/Morzeux/HttpParamsDataset/master/payload_full.csv"
)

FUZZDB_FILES = {
    "SQLI": [
        "attack/sql-injection/detect/Generic_SQLI.txt",
        "attack/sql-injection/detect/GenericBlind.txt",
        "attack/sql-injection/detect/MySQL.txt",
        "attack/sql-injection/detect/MSSQL.txt",
        "attack/sql-injection/detect/MSSQL_blind.txt",
        "attack/sql-injection/detect/MySQL_MSSQL.txt",
        "attack/sql-injection/detect/oracle.txt",
        "attack/sql-injection/detect/xplatform.txt",
    ],
    "XSS": [
        "attack/xss/xss-rsnake.txt",
        "attack/xss/xss-uri.txt",
        "attack/xss/xss-other.txt",
        "attack/xss/XSSPolyglot.txt",
    ],
    "PATH_TRAVERSAL": [
        "attack/path-traversal/path-traversal-windows.txt",
        "attack/path-traversal/traversals-8-deep-exotic-encoding.txt",
    ],
}

FUZZDB_RAW_BASE = "https://raw.githubusercontent.com/fuzzdb-project/fuzzdb/master/"

ALL_DIRS = (
    RAW / "sqli",
    RAW / "xss",
    RAW / "path-traversal",
    RAW / "benign",
    UPSTREAM,
    NORMALIZED,
    CLUSTERED,
    EVALUATION,
    PROMPTS,
    AI_OUTPUTS,
    CANDIDATES,
    REVIEWED,
    PRODUCTION,
    POSITIVE_TESTS,
    NEGATIVE_TESTS,
    REPORTS,
    EXPORT,
)
