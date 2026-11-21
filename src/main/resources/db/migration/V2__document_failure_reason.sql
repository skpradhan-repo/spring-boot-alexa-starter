-- 1) Coarse, client-safe failure code for documents in FAILED state (e.g. EMPTY_DOCUMENT,
--    EXTRACTION_FAILED, EMBEDDING_UNAVAILABLE). Detailed errors stay in the server log only.
ALTER TABLE documents ADD COLUMN failure_reason TEXT;

-- 2) Versions are numbered per access group. With a global (name, version) key, the version number a
--    caller received would reveal that another group had uploaded a file with the same name.
ALTER TABLE documents DROP CONSTRAINT documents_name_version_uq;
ALTER TABLE documents ADD CONSTRAINT documents_group_name_version_uq UNIQUE (access_group, name, version);
