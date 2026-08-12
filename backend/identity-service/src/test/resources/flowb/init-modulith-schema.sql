-- Spring Modulith's JDBC event-publication registry creates its table but not its schema.
-- Mirrors docker-resources/postgres/init-schemas.sql for the identity-service slice.
CREATE SCHEMA IF NOT EXISTS dev_ticketing_modulith;
