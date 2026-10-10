-- Retire the Source OpenAPI administration permissions. Keep V10 checksum and client data intact.
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT menu_id FROM sys_menu
    WHERE component = 'system/openApiClient/index' OR perms LIKE 'system:openApiClient:%'
);
DELETE FROM sys_menu
WHERE component = 'system/openApiClient/index' OR perms LIKE 'system:openApiClient:%';
-- Historical open_api_client/open_api_client_api tables are unused, not destructively dropped.
