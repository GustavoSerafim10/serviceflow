from pathlib import Path

import pytest

from app.train import MODEL_FILE, train

DATA_PATH = Path(__file__).resolve().parent.parent / "data" / "tickets.csv"


@pytest.fixture(scope="session")
def trained(tmp_path_factory):
    """Treina UMA vez por sessão de testes (o treino leva alguns segundos) num
    diretório temporário: os testes nunca dependem de modelo pré-existente."""
    out_dir = tmp_path_factory.mktemp("models")
    metrics = train(DATA_PATH, out_dir)
    return out_dir / MODEL_FILE, metrics
