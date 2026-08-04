FROM python:3.13-slim AS ingestion

RUN pip install --no-cache-dir \
    openai==2.43.0 \
    beautifulsoup4==4.15.0 \
    opensearch-py==2.4.2 \
    langchain-text-splitters==0.3.5 \
    pypdf==5.1.0 \
    python-docx==1.1.2 \
    openpyxl==3.1.5

WORKDIR /app
COPY ingestion.py /app/
COPY data /app/data
CMD ["python", "ingestion.py"]

FROM python:3.13-slim AS api

RUN pip install --no-cache-dir \
    openai==2.43.0 \
    fastapi==0.137.1 \
    uvicorn[standard]==0.49.0 \
    beautifulsoup4==4.15.0 \
    hypercorn==0.18.0 \
    opensearch-py==2.4.2 \
    jinja2==3.1.6

WORKDIR /app
COPY api.py /app/
COPY shared.py /app/
COPY bedrock_api.py /app/
COPY index.html /app/
CMD ["hypercorn", "api:app", "--bind", "0.0.0.0:8080", "--access-logfile", "-"]