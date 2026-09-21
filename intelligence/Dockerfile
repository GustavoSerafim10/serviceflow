FROM python:3.12-slim

# PYTHONDONTWRITEBYTECODE: não gera .pyc; PYTHONUNBUFFERED: logs saem na hora.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /srv

# Dependências primeiro: enquanto o requirements.txt não mudar, esta camada
# (a mais pesada: numpy, scipy, scikit-learn) fica em cache.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY data ./data

# O modelo é treinado DURANTE o build e fica embutido na imagem: a imagem é
# autocontida e cada versão dela corresponde a um modelo/dataset conhecidos.
# (O build falha se o treino falhar.)
RUN python -m app.train --data data/tickets.csv --out models

# Não roda como root.
RUN useradd --system --no-create-home appuser
USER appuser

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
