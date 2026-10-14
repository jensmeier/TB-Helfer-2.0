"""Optional independent TB-Helfer MCP gateway; no DB access and no write tools."""
from __future__ import annotations

from contextlib import asynccontextmanager

from mcp.server.fastmcp import FastMCP
from starlette.applications import Starlette
from starlette.routing import Mount

from .auth import BearerTokenGate
from . import diagnostics


mcp = FastMCP(
    "TB-Helfer Server Diagnose",
    instructions="Nur Serverzustand prüfen; keine Kundendaten und keine Schreibzugriffe.",
    stateless_http=True,
    json_response=True,
)


@mcp.tool()
def server_gesundheit() -> dict:
    """Erreichbarkeit, Betriebsbereitschaft, DB-/Migrationsstatus der bestehenden Server-API."""
    return diagnostics.server_gesundheit()


@mcp.tool()
def monitor_uebersicht() -> dict:
    """Vorhandene Servermonitor-Prüfungen aus read-only Snapshot, ohne Rohmeldungen."""
    return diagnostics.monitor_uebersicht()


@mcp.tool()
def server_probleme() -> dict:
    """Anomalien aus API-Health und Monitor verständlich und datensparsam zusammenfassen."""
    return diagnostics.server_probleme()


@asynccontextmanager
async def lifespan(_app: Starlette):
    async with mcp.session_manager.run():
        yield


inner_app = Starlette(routes=[Mount("/", app=mcp.streamable_http_app())], lifespan=lifespan)
app = BearerTokenGate(inner_app)
