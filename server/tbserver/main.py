from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from . import SERVER_VERSION
from .capabilities import router as capabilities_router
from .config import settings
from .database import ping
from .migrations import status as migration_status


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
