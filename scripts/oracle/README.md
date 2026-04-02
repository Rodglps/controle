# Oracle Database Initialization

## Automatic Initialization

The Oracle container (`gvenzl/oracle-xe:21-slim`) is configured to automatically execute SQL scripts on first startup.

### Configuration in docker-compose.yml

```yaml
oracle:
  image: gvenzl/oracle-xe:21-slim
  volumes:
    - ./scripts/ddl:/docker-entrypoint-initdb.d/startup
```

### Execution Order

The Oracle container will automatically execute all `.sql` files in `/docker-entrypoint-initdb.d/startup` in alphabetical order:

1. `00_run_all.sql` - Master script that calls all other scripts
2. `01_create_table_server.sql` - Creates SERVER table
3. `02_create_table_sever_paths.sql` - Creates SEVER_PATHS table
4. `03_create_table_sever_paths_in_out.sql` - Creates SEVER_PATHS_IN_OUT table
5. `04_create_table_file_origin.sql` - Creates FILE_ORIGIN table
6. `05_insert_test_data.sql` - Inserts test data

### Database Credentials

The following credentials are configured in docker-compose.yml:

- **System Password**: `oracle`
- **Application User**: `edi_user`
- **Application Password**: `edi_pass`
- **Database**: `XEPDB1` (pluggable database)

### Connection String

```
jdbc:oracle:thin:@localhost:1521/XEPDB1
```

### First Startup

On first startup, the container will:
1. Initialize the Oracle database
2. Create the application user (`edi_user`)
3. Execute all DDL scripts from `/docker-entrypoint-initdb.d/startup`
4. Create tables, sequences, triggers, and constraints
5. Insert test data

This process takes approximately 2-3 minutes.

### Verification

After the container is healthy, verify the initialization:

```bash
# Connect to the database
docker exec -it edi-oracle sqlplus edi_user/edi_pass@XEPDB1

# Check tables
SELECT table_name FROM user_tables;

# Check test data
SELECT cod_server FROM server;
SELECT des_path FROM sever_paths;
SELECT des_link_type FROM sever_paths_in_out;
```

### Re-initialization

To re-initialize the database:

```bash
# Stop and remove containers and volumes
docker-compose down -v

# Start fresh
docker-compose up -d oracle
```

The `-v` flag removes volumes, forcing a complete re-initialization on next startup.

### Manual Script Execution

If you need to run scripts manually:

```bash
# Copy script to container
docker cp scripts/ddl/01_create_table_server.sql edi-oracle:/tmp/

# Execute script
docker exec -it edi-oracle sqlplus edi_user/edi_pass@XEPDB1 @/tmp/01_create_table_server.sql
```

### Health Check

The Oracle container includes a health check that verifies the database is ready:

```yaml
healthcheck:
  test: ["CMD", "healthcheck.sh"]
  interval: 30s
  timeout: 10s
  retries: 5
  start_period: 60s
```

Wait for the health check to pass before connecting applications:

```bash
docker-compose ps
# Look for "healthy" status
```

### Troubleshooting

**Container keeps restarting:**
- Check logs: `docker logs edi-oracle`
- Ensure sufficient disk space (Oracle XE requires ~2GB)
- Wait for full initialization (can take 2-3 minutes)

**Scripts not executing:**
- Verify volume mount: `docker exec edi-oracle ls /docker-entrypoint-initdb.d/startup`
- Check script permissions: `chmod +x scripts/ddl/*.sql`
- Review container logs for SQL errors

**Connection refused:**
- Wait for health check to pass
- Verify port mapping: `docker ps | grep 1521`
- Check firewall rules

**ORA-12154: TNS:could not resolve the connect identifier:**
- Use `XEPDB1` as the service name, not `XE`
- Full connection string: `jdbc:oracle:thin:@localhost:1521/XEPDB1`

