"""Contratos da API (entrada e saída), definidos com Pydantic.

Pydantic valida os dados automaticamente: se o cliente enviar título vazio ou
descrição gigante, o FastAPI responde 422 com o detalhe, sem código nosso.
É o equivalente Python do Bean Validation + DTOs do lado Java.
"""
from typing import Annotated

from pydantic import BaseModel, ConfigDict, Field, StringConstraints
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """Base: o JSON usa camelCase (modelVersion), como a API Java; o Python usa snake_case."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


# Mesmos limites da API Java (título 150, descrição 4000), com espaços das pontas removidos.
Title = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=150)]
Description = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=4000)]


class SuggestionRequest(CamelModel):
    title: Title
    description: Description


class Score(CamelModel):
    label: str
    confidence: float = Field(ge=0, le=1)


class Prediction(CamelModel):
    label: str
    confidence: float = Field(ge=0, le=1)
    alternatives: list[Score]


class SuggestionResponse(CamelModel):
    category: Prediction
    priority: Prediction
    model_version: str


class HealthResponse(CamelModel):
    status: str
    model_version: str
