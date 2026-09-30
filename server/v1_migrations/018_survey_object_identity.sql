-- TB-Helfer 2.0 - fachliche Objektidentitaet fuer Weichen/WVB
-- Raw survey originals remain immutable. The catalog axis gets a stable object key.

ALTER TABLE tb.survey_catalog_axes
    ADD COLUMN object_key text;

CREATE INDEX survey_catalog_axes_object_idx
    ON tb.survey_catalog_axes(scope, company_id, station_code, axis_type, object_key);

UPDATE tb.survey_catalog_axes
SET object_key = 'W:' || upper((regexp_match(display_name, '(?i)^W[[:space:]]+([0-9]+[A-Z]?)'))[1])
WHERE object_key IS NULL
  AND axis_type = 'WEICHE'
  AND display_name ~* '^W[[:space:]]+[0-9]+[A-Z]?';

UPDATE tb.survey_catalog_axes
SET object_key = 'WVB:' || upper((regexp_match(display_name, '(?i)^WVB[[:space:]]+([0-9]+[A-Z]?[-][0-9]+[A-Z]?)'))[1])
WHERE object_key IS NULL
  AND axis_type = 'WEICHENVERBINDUNG'
  AND display_name ~* '^WVB[[:space:]]+[0-9]+[A-Z]?[-][0-9]+[A-Z]?';
