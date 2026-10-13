from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


migration = read("server/v1_migrations/011_global_catalog_identity.sql")
catalog = read("server/tbserver/catalog.py")
admin = read("server/tbserver/admin.py")
portal = read("server/tbserver/portal.py")
profile = read("server/tbserver/profile.py")
main = read("server/tbserver/main.py")
caps = read("server/tbserver/capabilities.py")
admin_users = read("server/templates/v1_admin_users.html")
activation_card = read("server/templates/v1_device_activation_card.html")
portal_activate = read("server/templates/v1_portal_activate.html")
portal_home = read("server/templates/v1_portal_home.html")
sync_catalog = read("app/src/main/java/de/tbhelfer/server/SyncCatalogApi.kt")
sync_detail = read("app/src/main/java/de/tbhelfer/server/SyncDetailApi.kt")
sync_change = read("app/src/main/java/de/tbhelfer/server/SyncChangeApi.kt")
offline_editor = read("app/src/main/java/de/tbhelfer/datenbank/SyncOfflineEditor.kt")
offline_dialogs = read("app/src/main/java/de/tbhelfer/ui/sync/SyncOfflineDialogs.kt")
sync_fragment = read("app/src/main/java/de/tbhelfer/ui/sync/SyncKatalogFragment.kt")
offline_repo = read("app/src/main/java/de/tbhelfer/datenbank/OfflineSyncRepository.kt")
gradle = read("app/build.gradle.kts")

for table in (
    "tb.catalog_routes",
    "tb.catalog_stations",
    "tb.catalog_sections",
    "tb.catalog_objects",
    "tb.catalog_history",
):
    assert table in migration, table
assert "before_data jsonb" in migration
assert "after_data jsonb NOT NULL" in migration
assert "changed_by_user_id" in migration and "changed_by_company_id" in migration

assert 'router = APIRouter(prefix="/api/v1/catalog"' in catalog
assert '@router.get("/changes")' in catalog
assert '@router.get("/history")' in catalog
assert '@router.post("/mutations")' in catalog
assert "CATALOG_DELETE_NOT_ALLOWED" in catalog
assert "BASE_VERSION_REQUIRED" in catalog
assert "CLIENT_CHANGE_ID_REUSED" in catalog
assert "INSERT INTO tb.catalog_history" in catalog

user_create = admin.split("class UserCreate", 1)[1].split("class UserAdminUpdate", 1)[0]
assert "display_name:" not in user_create
assert "login_name:" not in user_create
assert "personal_email:" not in user_create
assert "_provisional_login" in admin
assert "USER_COMPANY_CHANGE_BLOCKED_BY_HISTORY" in admin
assert '@router.patch("/users/{user_id}")' in admin

assert "login_name: str" in portal and "display_name: str" in portal
assert "personal_email: str | None" in portal and "personal_phone: str | None" in portal
assert "PORTAL_FIRST_ACTIVATION" in portal
assert '@router.get("/profile")' in profile
assert '@router.put("/profile")' in profile
assert '@router.patch("/profile")' in profile
assert '@router.get("/directory")' in profile

assert "catalog_router" in main and "profile_router" in main
for cap in (
    "global_catalog_v1",
    "catalog_history_v1",
    "catalog_no_global_delete_v1",
    "user_profile_v1",
    "user_directory_v1",
    "admin_user_edit_v1",
    "portal_identity_activation_v1",
):
    assert cap in caps

assert "Vorläufiger Portal-Login" in activation_card
assert "first_name" in admin_users and "personal_email" in admin_users
assert "/api/v1/portal/activate" in portal_activate
assert "/api/v1/user/directory" in portal_home

assert '"/api/v1/catalog"' in sync_catalog
assert '"/api/v1/catalog/routes/' in sync_detail
assert '"/api/v1/catalog/stations/' in sync_detail
assert '"/api/v1/catalog/changes' in sync_change
assert '"/api/v1/catalog/ack' in sync_change
assert '"/api/v1/catalog/mutations' in sync_change

assert "createRoute(" in offline_editor
assert "createStation(" in offline_editor
assert "createObject(" in offline_editor
assert "showCreateRoute(" in offline_dialogs
assert "showCreateStation(" in offline_dialogs
assert "Gleis 28" in offline_dialogs
assert "showObjectsAndCreate" in sync_fragment
assert "＋ Strecke ergänzen" in sync_fragment
assert "＋ Bahnhof ergänzen" in sync_fragment
assert "mergedRoutes" in offline_repo and "preservedRoutes" in offline_repo
assert "dao.pendingChanges() + dao.conflicts()" in offline_repo
assert 'existing?.objectType ?: "OTHER"' in offline_repo
assert "versionCode = 200055" in gradle
assert 'versionName = "1.3.5"' in gradle

print("OK: identity/global catalog source contract")
