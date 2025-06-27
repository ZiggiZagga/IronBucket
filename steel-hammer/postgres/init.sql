CREATE USER keycloak_db_user WITH PASSWORD 'keycloak_db_pass';
CREATE DATABASE keycloak OWNER keycloak_db_user;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak_db_user;
