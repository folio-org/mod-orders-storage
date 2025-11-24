# Remove Tenant IDs from PoLine Locations

This script removes `tenantId` fields from all location objects in PoLine records where the source is set to 'API'.

## Purpose

The script processes the JSONB `locations` array in the `po_line` table and removes the `tenantId` field from each location object for PoLines created via API.

## Usage

### Prerequisites

- PostgreSQL database access with the appropriate permissions
- Connection to the database where the `po_line` table exists

### Running the Script

#### Option 1: Using psql (Command Line)

1. Connect to your PostgreSQL database:
   ```bash
   psql -h <host> -U <username> -d <database>
   ```

2. Set the schema (replace `<tenant_name>` with your tenant):
   ```sql
   SET search_path TO <tenant_name>_mod_orders_storage;
   ```

3. Execute the script:
   ```sql
   \i delete_tenant_ids.sql
   ```

   Or run it directly:
   ```bash
   psql -h <host> -U <username> -d <database> -f delete_tenant_ids.sql
   ```

#### Option 2: Using a DB Editor (DBeaver, pgAdmin, DataGrip, etc.)

1. Connect to the database (default port: 5432)
2. Determine your schema name: `<tenant_name>_mod_orders_storage` (e.g., `diku_mod_orders_storage`)
3. Open a new SQL editor and execute:
   ```sql
   SET search_path TO <tenant_name>_mod_orders_storage;
   ```
4. Open and execute the `delete_tenant_ids.sql` file (or copy/paste its contents)
5. Verify the number of rows updated

### What the Script Does

The script:
1. Identifies all PoLine records with a `locations` array
2. Filters only PoLines where `source` is set to 'API'
3. Removes the `tenantId` field from each location object in the array
4. Updates the record only if `tenantId` fields exist in the locations

### Example

Before:
```json
{
  "locations": [
    {
      "locationId": "123",
      "tenantId": "tenant1",
      "quantity": 1
    }
  ],
  "source": "API"
}
```

After:
```json
{
  "locations": [
    {
      "locationId": "123",
      "quantity": 1
    }
  ],
  "source": "API"
}
```

### Safety Notes

- The script checks if central ordering is enabled (`ALLOW_ORDERING_WITH_AFFILIATED_LOCATIONS=true`)
- If central ordering is enabled, the script will abort with an error
- The script only updates records where `locations` exists and contains `tenantId` fields
- Only PoLines with `source='API'` are affected
- It's recommended to backup your database before running the script
- Test on a non-production environment first

