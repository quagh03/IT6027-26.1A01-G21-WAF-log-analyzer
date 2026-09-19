-- Generated from detection-rule-generator/rules/production
-- 14 production rules (SQLI 6 / XSS 4 / PATH_TRAVERSAL 4), all source=AI_MINED.
-- Provenance columns: source, generator_rule_id, rule_version (solution-design §7.3.6)

INSERT INTO detection_rules (
    code, name, category, pattern, target_field, weight, enabled,
    description, source, generator_rule_id, rule_version
) VALUES
('PT-BACKSLASH-001', 'Windows backslash traversal', 'PATH_TRAVERSAL', '(?i)\.\.(?:\\+|%5c+)|%2e%2e%5c', 'path', 45, TRUE, 'Detects ..\ and %2e%2e%5c Windows-style path traversal.', 'AI_MINED', 'PT-BACKSLASH-001', '1.0.0'),
('PT-DOTDOT-001', 'Dot-dot slash traversal', 'PATH_TRAVERSAL', '(?i)(?:\.\./|\.\.\\|(?:\.|%2e|%252e)(?:\.|%2e|%252e)(?:/|%2f|%252f))', 'path', 45, TRUE, 'Detects ../ and encoded %2e%2e%2f path traversal sequences on path or query.', 'AI_MINED', 'PT-DOTDOT-001', '1.0.0'),
('PT-DOUBLE-001', 'Double-encoded path traversal', 'PATH_TRAVERSAL', '(?i)%252e%252e(?:%252f|%252e|%255c|%2f)', 'path', 55, TRUE, 'Detects double-encoded traversal such as %252e%252e%252f.', 'AI_MINED', 'PT-DOUBLE-001', '1.0.0'),
('PT-ENCODED-001', 'URL-encoded path traversal', 'PATH_TRAVERSAL', '(?i)(?:%2e%2e(?:%2f|%5c|/|\\)|%2e%2e/)', 'path', 50, TRUE, 'Detects %2e%2e%2f / %2e%2e%5c traversal encodings on path or query.', 'AI_MINED', 'PT-ENCODED-001', '1.0.0'),
('SQLI-BOOLEAN-001', 'Boolean tautology in query', 'SQLI', '(?i)(\bor\b|\band\b)(?:\s+|%20|\+)+\d+(?:\s+|%20|\+)*(?:=|%3d)(?:\s+|%20|\+)*\d+', 'query', 40, TRUE, 'Detects classic boolean-based SQLi tautologies such as OR/AND n=n in the query string.', 'AI_MINED', 'SQLI-BOOLEAN-001', '1.0.0'),
('SQLI-BOOLEAN-002', 'String equality tautology', 'SQLI', '(?i)(\bor\b|\band\b)(?:\s+|%20|\+)*(?:''|%27)[^'']{0,32}(?:''|%27)(?:\s+|%20|\+)*(?:=|%3d)(?:\s+|%20|\+)*(?:''|%27)', 'query', 40, TRUE, 'Detects quoted string tautologies such as OR ''a''=''a'' in query values.', 'AI_MINED', 'SQLI-BOOLEAN-002', '1.0.0'),
('SQLI-COMMENT-001', 'SQL comment after quote', 'SQLI', '(?i)(?:''|%27)(?:\s+|%20|\+)*(?:--|#|%23|/\*)', 'query', 35, TRUE, 'Detects quote-then-comment evasion (''-- , ''# , ''/*) that short-circuits the rest of a SQL statement.', 'AI_MINED', 'SQLI-COMMENT-001', '1.0.0'),
('SQLI-STACKED-001', 'Stacked SQL statement', 'SQLI', '(?i);(?:\s+|%20|\+)*(select|insert|update|delete|drop|alter|exec|waitfor)\b', 'query', 50, TRUE, 'Detects stacked queries that start a second SQL statement after a semicolon.', 'AI_MINED', 'SQLI-STACKED-001', '1.0.0'),
('SQLI-TIME-001', 'Time-based SQL delay', 'SQLI', '(?i)(?:(?:sleep|pg_sleep|benchmark)\s*\(|waitfor(?:\s+|%20|\+)+delay)', 'query', 60, TRUE, 'Detects time-based SQLi primitives (SLEEP / pg_sleep / BENCHMARK / WAITFOR DELAY).', 'AI_MINED', 'SQLI-TIME-001', '1.0.0'),
('SQLI-UNION-001', 'UNION SELECT injection', 'SQLI', '(?i)\bunion\b(?:\s+|%20|\+)+(?:all(?:\s+|%20|\+)+)?\bselect\b', 'query', 55, TRUE, 'Detects UNION SELECT used to append an attacker query in the query string.', 'AI_MINED', 'SQLI-UNION-001', '1.0.0'),
('XSS-ENCODED-001', 'Percent-encoded XSS tag', 'XSS', '(?i)(?:%3c|%253c)\s*(?:script|img|svg|iframe|body)\b', 'query', 40, TRUE, 'Detects percent-encoded opening tags (%3cscript, %3cimg) typical of query-string XSS.', 'AI_MINED', 'XSS-ENCODED-001', '1.0.0'),
('XSS-EVENT-001', 'HTML event-handler XSS', 'XSS', '(?i)(?:<|%3c|%253c)[^>]{0,80}on(?:error|load|click|mouseover|focus|submit)\s*(?:=|%3d)', 'query', 45, TRUE, 'Detects event-handler XSS such as <img ... onerror=...> in query values.', 'AI_MINED', 'XSS-EVENT-001', '1.0.0'),
('XSS-JSURI-001', 'javascript: URI XSS', 'XSS', '(?i)(?:javascript|vbscript)(?:\s+|%20|\+)*(?::|%3a)', 'query', 50, TRUE, 'Detects javascript: / vbscript: URIs used as XSS vectors in query or path.', 'AI_MINED', 'XSS-JSURI-001', '1.0.0'),
('XSS-SCRIPT-001', 'HTML script/iframe/svg tag', 'XSS', '(?i)(?:<|%3c|%253c)(?:\s+|%20|\+)*(script|iframe|svg)\b', 'query', 55, TRUE, 'Detects injected <script>, <iframe> or <svg> tags, including percent-encoded <.', 'AI_MINED', 'XSS-SCRIPT-001', '1.0.0');
