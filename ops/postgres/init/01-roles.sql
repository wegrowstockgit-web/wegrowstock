-- Bootstrap roles for RLS-enforced runtime vs migration owner
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_owner') THEN
    CREATE ROLE app_owner WITH LOGIN PASSWORD 'app_owner_secret' CREATEDB;
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'app_user') THEN
    CREATE ROLE app_user WITH LOGIN PASSWORD 'app_user_secret' NOBYPASSRLS;
  END IF;
END
$$;

GRANT ALL PRIVILEGES ON DATABASE invsys TO app_owner;
ALTER DATABASE invsys OWNER TO app_owner;
