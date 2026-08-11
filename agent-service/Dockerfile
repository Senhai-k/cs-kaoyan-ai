ARG CONTAINER_REGISTRY=docker.io
FROM ${CONTAINER_REGISTRY}/library/python:3.11-slim
ARG PYTORCH_INDEX_URL=https://download.pytorch.org/whl/cpu
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app
COPY requirements.txt .
RUN --mount=type=cache,target=/root/.cache/pip pip install --upgrade pip \
    && pip install torch --index-url ${PYTORCH_INDEX_URL} \
    && pip install -r requirements.txt
COPY app ./app
COPY evals ./evals
RUN useradd --system --uid 10001 --create-home appuser \
    && mkdir -p /app/data \
    && chown -R appuser:appuser /app
USER appuser
EXPOSE 18889
CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "18889"]
