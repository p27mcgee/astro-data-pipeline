# Database Migration Strategy

## Overview

This project uses a **hybrid approach** for database schema management:

- **JPA Hibernate** for initial schema creation and development
- **Flyway** for production schema migrations (when data preservation is required)

## Current State: Pre-Production

### Configuration (Default)

```yaml
spring.jpa.hibernate.ddl-auto: update  # JPA manages schema
spring.flyway.enabled: false           # Flyway disabled
```

### Behavior

- ✅ JPA automatically creates/updates database schema from entity classes
- ✅ Fresh deployments work out-of-the-box
- ✅ Development and testing environments rebuild schema as needed
- ✅ No migration files required

## Future State: Production with Data

### When to Switch

Enable Flyway when you have a **production database with data that must be preserved**.

### Configuration (Production with Data)

```bash
# Environment variables
export FLYWAY_ENABLED=true
export JPA_DDL_AUTO=validate
```

```yaml
spring.jpa.hibernate.ddl-auto: validate  # JPA only validates, doesn't change schema
spring.flyway.enabled: true              # Flyway manages migrations
```

### Behavior

- ✅ Flyway manages all schema changes via versioned migration files
- ✅ Production data is never lost
- ✅ Schema changes are tracked and auditable
- ✅ JPA validates schema matches entity definitions

## Migration to Flyway (First Time)

### Step 1: Generate Baseline Migration

When you first deploy to production and accumulate real data, create a baseline migration:

```bash
# Option A: Generate from JPA entities (recommended)
./gradlew bootRun --args='--spring.jpa.properties.javax.persistence.schema-generation.scripts.action=create --spring.jpa.properties.javax.persistence.schema-generation.scripts.create-target=V1__Initial_schema.sql'

# Option B: Export from existing database
pg_dump -h localhost -U astro_user -d astro_catalog --schema-only > V1__Initial_schema.sql
```

### Step 2: Place Migration File

```bash
mkdir -p image-processor/src/main/resources/db/migration
mv V1__Initial_schema.sql image-processor/src/main/resources/db/migration/

mkdir -p catalog-service/src/main/resources/db/migration
# Create catalog service migration similarly
```

### Step 3: Baseline Existing Database

For databases already in production (created by JPA):

```bash
# Run Flyway baseline to mark V1 as already applied
./gradlew flywayBaseline -Dflyway.baselineVersion=1
```

### Step 4: Enable Flyway

Update configuration:

```bash
export FLYWAY_ENABLED=true
export JPA_DDL_AUTO=validate
```

Restart application - Flyway now manages future migrations.

## Adding New Migrations

### When Schema Changes Are Needed

1. Create new migration file in `db/migration/`:
   ```
   V2__Add_user_preferences_table.sql
   V3__Add_processing_metrics_columns.sql
   ```

2. Follow Flyway naming convention:
    - `V{version}__{description}.sql`
    - Version must be unique and sequential
    - Use underscores in description

3. Write SQL migration:
   ```sql
   -- V2__Add_user_preferences_table.sql
   CREATE TABLE user_preferences (
       id BIGSERIAL PRIMARY KEY,
       user_id VARCHAR(255) NOT NULL,
       preferences JSONB,
       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
   );

   CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
   ```

4. Test migration:
   ```bash
   # Dry run
   ./gradlew flywayInfo

   # Apply
   ./gradlew flywayMigrate
   ```

5. Update JPA entities to match:
   ```java
   @Entity
   @Table(name = "user_preferences")
   public class UserPreference {
       // Match migration schema
   }
   ```

## Environment-Specific Configurations

### Development

```yaml
spring.jpa.hibernate.ddl-auto: update
spring.flyway.enabled: false
```

- Fast iteration
- Schema rebuilds automatically

### Testing

```yaml
spring.jpa.hibernate.ddl-auto: create-drop
spring.flyway.enabled: false
```

- Clean state for each test
- In-memory H2 database

### Staging (Pre-Production)

```yaml
spring.jpa.hibernate.ddl-auto: update
spring.flyway.enabled: false
```

- Matches production initially
- Can use Flyway if testing migrations

### Production (Fresh Deploy)

```yaml
spring.jpa.hibernate.ddl-auto: update
spring.flyway.enabled: false
```

- JPA creates initial schema
- Switch to Flyway when data exists

### Production (With Data)

```yaml
spring.jpa.hibernate.ddl-auto: validate
spring.flyway.enabled: true
```

- Flyway manages all changes
- Data preservation guaranteed

## Best Practices

### DO:

- ✅ Keep JPA entities as source of truth during development
- ✅ Enable Flyway only when you have production data to protect
- ✅ Test migrations on staging before production
- ✅ Use descriptive migration names
- ✅ Make migrations reversible when possible (create rollback scripts)
- ✅ Version control all migration files

### DON'T:

- ❌ Don't edit applied migrations (create new ones instead)
- ❌ Don't skip Flyway validation in production
- ❌ Don't mix JPA DDL changes with Flyway in production
- ❌ Don't delete migration files after they're applied
- ❌ Don't use `ddl-auto: create-drop` in production

## Troubleshooting

### Schema Mismatch

If JPA validation fails:

```bash
# Check current schema
./gradlew flywayInfo

# Repair checksum mismatches
./gradlew flywayRepair

# Validate schema
./gradlew flywayValidate
```

### Migration Conflicts

If multiple developers create same version:

```bash
# Renumber migration
mv V2__my_change.sql V3__my_change.sql

# Or use timestamp versioning
V20241004120000__my_change.sql
```

### Rollback

Flyway doesn't support automatic rollback. Create manual rollback scripts:

```sql
-- V2__Add_column.sql
ALTER TABLE processing_jobs ADD COLUMN retry_count INTEGER DEFAULT 0;

-- U2__Rollback_add_column.sql (manual)
ALTER TABLE processing_jobs DROP COLUMN retry_count;
```

## References

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Spring Boot Flyway Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.flyway)
- [Hibernate DDL Auto](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.using-hibernate)
