from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from . import SERVER_VERSION
from .acceptances import router as acceptances_router
from .admin import router as admin_router
from .btb import router as btb_router
from .capabilities import router as capabilities_router
from .config import settings
from .database import ping
from .defects import router as defects_router
from .devices import admin_router as admin_devices_router
from .devices import router as devices_router
from .files import router as files_router
from .migrations import status as migration_status
from .portal import router as portal_router
from .survey_originals import router as survey_originals_router
from .survey_parser import router as survey_parser_router
from .survey_projection import router as survey_projection_router
from .survey_sections import router as survey_sections_router
from .sync import router as sync_router
from .track_data import router as track_data_router
from .worksites import router as worksites_router


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings.storage_root.mkdir(parents=True, exist_ok=True)
    yield


app = FastAPI(
    title="TB-Helfer Server",
    version=SERVER_VERSION,
    lifespan=lifespan,
)
app.include_router(capabilities_router)
app.include_router(portal_router)
app.include_router(admin_router)
app.include_router(admin_devices_router)
app.include_router(devices_router)
app.include_router(worksites_router)
app.include_router(btb_router)
app.include_router(sync_router)
app.include_router(files_router)
app.include_router(defects_router)
app.include_router(acceptances_router)
app.include_router(survey_originals_router)
app.include_router(survey_parser_router)
app.include_router(survey_projection_router)
app.include_router(survey_sections_router)
app.include_router(track_data_router)


@app.get("/health/live", tags=["system"])
def health_live() -> dict:
    return {"status": "ok", "server_version": SERVER_VERSION}


@app.get("/health/ready", tags=["system"])
def health_ready():
    if not ping():
        return JSONResponse(
            status_code=503,
            content={"status": "not_ready", "database": "down"},
        )
    try:
        migration = migration_status()
    except Exception as exc:
        return JSONResponse(
            status_code=503,
            content={
                "status": "not_ready",
                "database": "up",
                "migrations": "invalid",
                "detail": str(exc),
            },
        )
    if migration["pending"]:
        return JSONResponse(
            status_code=503,
            content={
                "status": "not_ready",
                "database": "up",
                "migrations": "pending",
                "pending": migration["pending"],
            },
        )
    return {
        "status": "ok",
        "database": "up",
        "migrations": "ok",
        "server_version": SERVER_VERSION,
    }
