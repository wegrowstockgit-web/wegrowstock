DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') THEN
    CREATE ROLE app_user WITH LOGIN PASSWORD 'app_user_secret' NOBYPASSRLS;
  END IF;
END
$$;

CREATE EXTENSION IF NOT EXISTS vector;
