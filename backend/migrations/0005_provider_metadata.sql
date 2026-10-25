ALTER TABLE content ADD COLUMN metadata_json TEXT CHECK(metadata_json IS NULL OR json_valid(metadata_json));
