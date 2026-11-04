#!/bin/sh
# Initializes the sample target database on the container's first start.
# Docker's postgres entrypoint runs (or, if not executable, sources) this file.
# The SQL lives in sql/, which the entrypoint does not run on its own.
#
#   SAMPLE_SCALE           dataset size multiplier (default 1 = 500k orders)
#   SAMPLE_READER_PASSWORD password for the read-only queryforge_reader role
set -e

: "${SAMPLE_READER_PASSWORD:?SAMPLE_READER_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 \
	--username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
	-v scale="${SAMPLE_SCALE:-1}" \
	-v reader_password="$SAMPLE_READER_PASSWORD" \
	-f /docker-entrypoint-initdb.d/sql/schema.sql \
	-f /docker-entrypoint-initdb.d/sql/data.sql \
	-f /docker-entrypoint-initdb.d/sql/reader-role.sql
