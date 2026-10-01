CREATE ROLE flow_app LOGIN PASSWORD 'flow_password';
GRANT CONNECT ON DATABASE credit_flow_test TO flow_app;
GRANT USAGE ON SCHEMA public TO flow_app;
