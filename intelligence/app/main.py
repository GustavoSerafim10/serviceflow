"""API HTTP do serviço (FastAPI).

Execução local:  uvicorn app.main:app --reload
Documentação interativa (gerada automaticamente): http://localhost:8000/docs
"""
from __future__ import annotations

import os
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, Request

from app.classifier import TicketClassifier
from app.schemas import HealthResponse, Prediction, Score, SuggestionRequest, SuggestionResponse

DEFAULT_MODEL_PATH = Path(os.getenv("MODEL_PATH", "models/model.joblib"))


def create_app(model_path: Path | None = None) -> FastAPI:
    """Fábrica da aplicação. Receber o caminho do modelo por parâmetro permite
    que os testes usem um modelo próprio, sem depender de variáveis globais."""
    path = model_path or DEFAULT_MODEL_PATH

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        # Roda UMA vez na subida: carrega o modelo do disco para a memória.
        # Se o arquivo não existir, a aplicação nem sobe (falha rápida e clara),
        # em vez de responder 500 em cada requisição.
        app.state.classifier = TicketClassifier.load(path)
        yield

    app = FastAPI(
        title="ServiceFlow Intelligence",
        description="Sugere categoria e prioridade de chamados de TI a partir do texto.",
        version="0.1.0",
        lifespan=lifespan,
    )

    @app.get("/health", response_model=HealthResponse, tags=["Operação"])
    def health(request: Request) -> HealthResponse:
        """Liveness/readiness: só responde se o modelo foi carregado com sucesso."""
        return HealthResponse(status="UP", model_version=request.app.state.classifier.version)

    @app.post("/v1/suggestions", response_model=SuggestionResponse, tags=["Sugestões"])
    def suggest(body: SuggestionRequest, request: Request) -> SuggestionResponse:
        """Sugere categoria e prioridade com a confiança de cada uma.

        A confiança (0 a 1) é a probabilidade que o modelo atribui ao rótulo.
        Quem chama decide o que fazer com ela (ex: só exibir a sugestão acima
        de certo valor). `alternatives` traz os demais candidatos.
        """
        result = request.app.state.classifier.suggest(body.title, body.description)

        def to_schema(p) -> Prediction:
            return Prediction(
                label=p.label,
                confidence=p.confidence,
                alternatives=[Score(label=a.label, confidence=a.confidence) for a in p.alternatives],
            )

        return SuggestionResponse(
            category=to_schema(result.category),
            priority=to_schema(result.priority),
            model_version=result.model_version,
        )

    return app


app = create_app()
