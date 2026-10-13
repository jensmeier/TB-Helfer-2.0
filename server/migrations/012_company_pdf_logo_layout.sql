BEGIN;

-- TB-Helfer Schema 12
-- Firmenweite Darstellung des zentralen Firmenlogos im BTB/PDF.
-- Das Original-Logo selbst bleibt unverändert; gespeichert werden nur Layoutparameter.

ALTER TABLE tb.company_profiles
    ADD COLUMN IF NOT EXISTS pdf_logo_width_pt integer,
    ADD COLUMN IF NOT EXISTS pdf_logo_offset_x_pt integer,
    ADD COLUMN IF NOT EXISTS pdf_logo_offset_y_pt integer,
    ADD COLUMN IF NOT EXISTS pdf_logo_show_company_name boolean,
    ADD COLUMN IF NOT EXISTS pdf_logo_show_company_email boolean,
    ADD COLUMN IF NOT EXISTS pdf_logo_show_company_phone boolean,
    ADD COLUMN IF NOT EXISTS pdf_logo_contacts_in_image boolean,
    ADD COLUMN IF NOT EXISTS pdf_logo_layout_version bigint;

UPDATE tb.company_profiles SET pdf_logo_width_pt=90 WHERE pdf_logo_width_pt IS NULL;
UPDATE tb.company_profiles SET pdf_logo_offset_x_pt=0 WHERE pdf_logo_offset_x_pt IS NULL;
UPDATE tb.company_profiles SET pdf_logo_offset_y_pt=0 WHERE pdf_logo_offset_y_pt IS NULL;
UPDATE tb.company_profiles SET pdf_logo_show_company_name=true WHERE pdf_logo_show_company_name IS NULL;
UPDATE tb.company_profiles SET pdf_logo_show_company_email=true WHERE pdf_logo_show_company_email IS NULL;
UPDATE tb.company_profiles SET pdf_logo_show_company_phone=true WHERE pdf_logo_show_company_phone IS NULL;
UPDATE tb.company_profiles SET pdf_logo_contacts_in_image=false WHERE pdf_logo_contacts_in_image IS NULL;
UPDATE tb.company_profiles SET pdf_logo_layout_version=1 WHERE pdf_logo_layout_version IS NULL;

ALTER TABLE tb.company_profiles
    ALTER COLUMN pdf_logo_width_pt SET DEFAULT 90,
    ALTER COLUMN pdf_logo_width_pt SET NOT NULL,
    ALTER COLUMN pdf_logo_offset_x_pt SET DEFAULT 0,
    ALTER COLUMN pdf_logo_offset_x_pt SET NOT NULL,
    ALTER COLUMN pdf_logo_offset_y_pt SET DEFAULT 0,
    ALTER COLUMN pdf_logo_offset_y_pt SET NOT NULL,
    ALTER COLUMN pdf_logo_show_company_name SET DEFAULT true,
    ALTER COLUMN pdf_logo_show_company_name SET NOT NULL,
    ALTER COLUMN pdf_logo_show_company_email SET DEFAULT true,
    ALTER COLUMN pdf_logo_show_company_email SET NOT NULL,
    ALTER COLUMN pdf_logo_show_company_phone SET DEFAULT true,
    ALTER COLUMN pdf_logo_show_company_phone SET NOT NULL,
    ALTER COLUMN pdf_logo_contacts_in_image SET DEFAULT false,
    ALTER COLUMN pdf_logo_contacts_in_image SET NOT NULL,
    ALTER COLUMN pdf_logo_layout_version SET DEFAULT 1,
    ALTER COLUMN pdf_logo_layout_version SET NOT NULL;

ALTER TABLE tb.company_profiles DROP CONSTRAINT IF EXISTS company_profiles_pdf_logo_width_ck;
ALTER TABLE tb.company_profiles ADD CONSTRAINT company_profiles_pdf_logo_width_ck
    CHECK (pdf_logo_width_pt BETWEEN 40 AND 250);
ALTER TABLE tb.company_profiles DROP CONSTRAINT IF EXISTS company_profiles_pdf_logo_offset_x_ck;
ALTER TABLE tb.company_profiles ADD CONSTRAINT company_profiles_pdf_logo_offset_x_ck
    CHECK (pdf_logo_offset_x_pt BETWEEN -120 AND 120);
ALTER TABLE tb.company_profiles DROP CONSTRAINT IF EXISTS company_profiles_pdf_logo_offset_y_ck;
ALTER TABLE tb.company_profiles ADD CONSTRAINT company_profiles_pdf_logo_offset_y_ck
    CHECK (pdf_logo_offset_y_pt BETWEEN -60 AND 180);
ALTER TABLE tb.company_profiles DROP CONSTRAINT IF EXISTS company_profiles_pdf_logo_layout_version_ck;
ALTER TABLE tb.company_profiles ADD CONSTRAINT company_profiles_pdf_logo_layout_version_ck
    CHECK (pdf_logo_layout_version >= 1);

INSERT INTO tb.schema_versions(version,name)
VALUES (12,'012_company_pdf_logo_layout')
ON CONFLICT (version) DO NOTHING;

COMMIT;
