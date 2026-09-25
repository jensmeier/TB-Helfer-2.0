ALTER TABLE tb.files
ADD COLUMN photo_metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE tb.files
ADD CONSTRAINT files_photo_metadata_object_chk
CHECK (jsonb_typeof(photo_metadata) = 'object');
