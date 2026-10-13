-- BTB-spezifische, versionierte Foto-Fachzuordnung.
-- Das physische Foto und seine Aufnahme-Metadaten bleiben unverändert.
-- Fachliche Korrekturen (Punkt/Firma/Unterbaustelle/Text/KM) liegen auf der BTB-Verknüpfung.

ALTER TABLE tb.file_links
    ADD COLUMN link_metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    ADD COLUMN updated_by uuid REFERENCES tb.users(id) ON DELETE SET NULL,
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE tb.file_links
    ADD CONSTRAINT file_links_link_metadata_object_chk
    CHECK (jsonb_typeof(link_metadata) = 'object');
