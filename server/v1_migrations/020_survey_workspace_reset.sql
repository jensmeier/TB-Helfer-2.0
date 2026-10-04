-- Reversible source reset keeps original files and catalog history intact.
ALTER TABLE tb.survey_catalog_assets DROP CONSTRAINT survey_catalog_assets_data_status_check;
ALTER TABLE tb.survey_catalog_assets ADD CONSTRAINT survey_catalog_assets_data_status_check
 CHECK (data_status IN ('ACTIVE','REVIEW','ERROR','INACTIVE'));
