FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy app
COPY app ./app

# Non-root user (mantido para uso fora do compose; o compose roda como root
# porque o bind mount ./back/data fica com dono root neste host).
RUN useradd --create-home --shell /bin/bash appuser && \
    chown -R appuser:appuser /app

EXPOSE 3000

CMD ["sh", "-c", "uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-3000}"]