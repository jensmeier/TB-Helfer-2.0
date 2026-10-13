\set ON_ERROR_STOP on

DO $$
DECLARE
    missing text[];
BEGIN
    SELECT array_agg(required.col ORDER BY required.col)
      INTO missing
      FROM (VALUES
        ('pdf_logo_width_pt'),
        ('pdf_logo_offset_x_pt'),
        ('pdf_logo_offset_y_pt'),
        ('pdf_logo_show_company_name'),
        ('pdf_logo_show_company_email'),
        ('pdf_logo_show_company_phone'),
        ('pdf_logo_contacts_in_image'),
        ('pdf_logo_layout_version')
      ) AS required(col)
      WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns c
        WHERE c.table_schema='tb' AND c.table_name='company_profiles' AND c.column_name=required.col
      );
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'SCHEMA12_MISSING_COLUMNS:%', array_to_string(missing, ',');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM tb.schema_versions WHERE version=12 AND name='012_company_pdf_logo_layout') THEN
        RAISE EXCEPTION 'SCHEMA12_VERSION_MISSING';
    END IF;
END $$;

SELECT 'SCHEMA12_OK' AS result;
