-- Read-only role QueryForge uses to analyze this database. The settings are
-- defense in depth: the API also opens read-only transactions with a
-- statement timeout and always rolls them back.

CREATE ROLE queryforge_reader LOGIN PASSWORD :'reader_password';

GRANT CONNECT ON DATABASE :"DBNAME" TO queryforge_reader;
GRANT USAGE ON SCHEMA public TO queryforge_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO queryforge_reader;

ALTER ROLE queryforge_reader SET default_transaction_read_only = on;
ALTER ROLE queryforge_reader SET statement_timeout = '30s';
