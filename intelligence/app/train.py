"""Treino e avaliação dos modelos.

Uso:  python -m app.train --data data/tickets.csv --out models

Etapas:
  1. lê o CSV (title, description, category, priority);
  2. AVALIA com validação cruzada: divide os dados em 5 partes, treina em 4 e
     testa na quinta, rotacionando. Assim medimos o desempenho em chamados que
     o modelo NÃO viu (medir no próprio treino seria "decorar a prova");
  3. treina o modelo final com todos os dados e salva em models/.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from pathlib import Path

import joblib
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.model_selection import StratifiedKFold, cross_val_predict

from app.classifier import build_pipeline, combine_text

MODEL_FILE = "model.joblib"
METRICS_FILE = "metrics.json"


def load_dataset(path: Path) -> tuple[list[str], list[str], list[str]]:
    texts, categories, priorities = [], [], []
    with path.open(encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            texts.append(combine_text(row["title"], row["description"]))
            categories.append(row["category"])
            priorities.append(row["priority"])
    return texts, categories, priorities


def evaluate(texts: list[str], labels: list[str], name: str) -> dict:
    """Validação cruzada estratificada (mantém a proporção das classes em cada parte)."""
    folds = StratifiedKFold(n_splits=5, shuffle=True, random_state=42)
    predicted = cross_val_predict(build_pipeline(), texts, labels, cv=folds)
    print(f"\n=== {name}: validação cruzada (5 partes) ===")
    print(classification_report(labels, predicted, zero_division=0))
    return {
        "accuracy": round(float(accuracy_score(labels, predicted)), 4),
        "macro_f1": round(float(f1_score(labels, predicted, average="macro")), 4),
    }


def train(data_path: Path, out_dir: Path) -> dict:
    texts, categories, priorities = load_dataset(data_path)
    print(f"{len(texts)} chamados carregados de {data_path}")

    metrics = {
        "samples": len(texts),
        "category": evaluate(texts, categories, "CATEGORIA"),
        "priority": evaluate(texts, priorities, "PRIORIDADE"),
    }

    category_model = build_pipeline().fit(texts, categories)
    priority_model = build_pipeline().fit(texts, priorities)

    # A versão identifica os dados de treino: dados diferentes -> versão diferente.
    version = "v1-" + hashlib.sha256(data_path.read_bytes()).hexdigest()[:8]
    out_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump({"category": category_model, "priority": priority_model, "version": version},
                out_dir / MODEL_FILE)
    (out_dir / METRICS_FILE).write_text(json.dumps({"version": version, **metrics}, indent=2), encoding="utf-8")

    print(f"\nModelo {version} salvo em {out_dir / MODEL_FILE}")
    print(f"Categoria  -> acurácia {metrics['category']['accuracy']:.2%} | F1 macro {metrics['category']['macro_f1']:.2f}")
    print(f"Prioridade -> acurácia {metrics['priority']['accuracy']:.2%} | F1 macro {metrics['priority']['macro_f1']:.2f}")
    return metrics


def main() -> None:
    parser = argparse.ArgumentParser(description="Treina os classificadores do ServiceFlow Intelligence")
    parser.add_argument("--data", type=Path, default=Path("data/tickets.csv"))
    parser.add_argument("--out", type=Path, default=Path("models"))
    args = parser.parse_args()
    train(args.data, args.out)


if __name__ == "__main__":
    main()
