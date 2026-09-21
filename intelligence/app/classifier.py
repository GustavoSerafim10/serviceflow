"""Modelo de classificação de chamados: monta os pipelines, carrega o modelo
treinado e produz sugestões (categoria e prioridade) com grau de confiança.

Conceitos usados aqui (para explicar em entrevista):

* TF-IDF: transforma texto em números. Cada termo ganha um peso que sobe
  quando ele aparece muito NESTE chamado e desce quando aparece em TODOS os
  chamados (termos como "não" ou "o" dizem pouco; "toner" diz muito).
* N-gramas de CARACTERES (2 a 5) em vez de palavras inteiras: "impressora",
  "imprimir" e "impressão" compartilham pedaços ("impr", "ress"), então o modelo
  aproxima palavras da mesma família e tolera erros de digitação. Num teste
  comparativo com validação cruzada, subiu a acurácia da categoria de ~68% para
  ~74% sobre palavras + bigramas (com poucos dados, isso importa bastante).
* Regressão logística: classificador linear simples e rápido. Além do rótulo
  previsto, devolve uma PROBABILIDADE para cada classe, que usamos como
  confiança.
* Pipeline: encadeia "texto -> TF-IDF -> classificador" num objeto só, para
  que treino e previsão sempre passem exatamente pelas mesmas etapas.
"""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import joblib
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline


def build_pipeline() -> Pipeline:
    """Pipeline TF-IDF + regressão logística (usado para categoria e para prioridade)."""
    return Pipeline(
        [
            (
                "tfidf",
                TfidfVectorizer(
                    lowercase=True,
                    strip_accents="unicode",  # "conexão" e "conexao" viram a mesma palavra
                    analyzer="char_wb",       # n-gramas de caracteres dentro dos limites de cada palavra
                    ngram_range=(2, 5),
                    sublinear_tf=True,
                ),
            ),
            # class_weight="balanced": classes com menos exemplos pesam mais no
            # treino, evitando que o modelo ignore as raras.
            ("clf", LogisticRegression(max_iter=1000, C=10.0, class_weight="balanced")),
        ]
    )


def combine_text(title: str, description: str) -> str:
    """Texto que o modelo enxerga: título + descrição."""
    return f"{title}. {description}"


@dataclass(frozen=True)
class Score:
    label: str
    confidence: float


@dataclass(frozen=True)
class Prediction:
    label: str
    confidence: float
    alternatives: list[Score]  # demais candidatos, do mais para o menos provável


@dataclass(frozen=True)
class Suggestion:
    category: Prediction
    priority: Prediction
    model_version: str


class TicketClassifier:
    """Guarda os dois modelos treinados e converte texto em sugestão."""

    def __init__(self, category_model: Pipeline, priority_model: Pipeline, version: str):
        self._category_model = category_model
        self._priority_model = priority_model
        self.version = version

    @classmethod
    def load(cls, model_path: Path) -> "TicketClassifier":
        if not model_path.exists():
            raise FileNotFoundError(
                f"Modelo não encontrado em '{model_path}'. Treine com: python -m app.train"
            )
        bundle = joblib.load(model_path)
        return cls(bundle["category"], bundle["priority"], bundle["version"])

    def suggest(self, title: str, description: str, top_k: int = 3) -> Suggestion:
        text = combine_text(title, description)
        return Suggestion(
            category=self._predict(self._category_model, text, top_k),
            priority=self._predict(self._priority_model, text, top_k),
            model_version=self.version,
        )

    @staticmethod
    def _predict(model: Pipeline, text: str, top_k: int) -> Prediction:
        probabilities = model.predict_proba([text])[0]
        classes = model.classes_
        ranking = np.argsort(probabilities)[::-1][:top_k]  # índices, da maior para a menor probabilidade
        scores = [Score(str(classes[i]), round(float(probabilities[i]), 4)) for i in ranking]
        return Prediction(label=scores[0].label, confidence=scores[0].confidence, alternatives=scores[1:])
