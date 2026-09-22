-- Week 2: PATH_TRAVERSAL payloads often land in query string on access-log labs
-- (Nginx rejects / normalizes literal ../ in $uri). Scan RAW = path + "?" + query.

UPDATE detection_rules
SET target_field = 'raw'
WHERE category = 'PATH_TRAVERSAL'
  AND target_field = 'path';
