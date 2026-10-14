FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY mcp_gateway/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt
COPY mcp_gateway /app/mcp_gateway
RUN useradd --uid 10001 --create-home --no-log-init mcpuser
USER 10001
EXPOSE 8090
CMD ["uvicorn", "mcp_gateway.service:app", "--host", "0.0.0.0", "--port", "8090"]
