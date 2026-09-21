"""Treino e avaliação dos modelos.

Uso:
  python -m app.train                                          # só o dataset inicial
  python -m app.train --data data/tickets.csv data/local/real.csv   # inicial + chamados reais exportados

Os chamados reais vêm de GET /api/tickets/suggestions/training-data (ADMIN) da
API Java, que gera o CSV no mesmo formato (title,description,category,priority).

Etapas:
  1. lê os CSVs e descarta linhas inválidas (campo vazio ou prioridade fora de P1-P4);
  2. AVALIA com validação cruzada: divide os dados em partes, treina em N-1 e
     testa na restante, rotacionando. Assim medimos o desempenho em chamados que
     o modelo NÃO viu (medir no próprio treino seria "decorar a prova");
  3. treina o modelo final com todos os dados e salva em models/.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

import joblib
from sklearn.metrics import accuracy_score, classification_report, f1_score
from sklearn.model_selection import StratifiedKFold, cross_val_predict

from app.classifier import build_pipeline, combine_text

MODEL_FILE = "model.joblib"
METRICS_FILE = "metrics.json"
VALID_PRIORITIES = {"P1", "P2", "P3", "P4"}
MAX_FOLDS = 5


def load_dataset(path: Path) -> tuple[list[str], list[str], list[str], int]:
    """Lê um CSV. Devolve (textos, categorias, prioridades, linhas_descartadas)."""
    texts, categories, priorities, skipped = [], [], [], 0
    with path.open(encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            title = (row.get("title") or "").strip()
            description = (row.get("description") or "").strip()
            category = (row.get("category") or "").strip()
            priority = (row.get("priority") or "").strip()
            if not (title and description and category) or priority not in VALID_PRIORITIES:
                skipped += 1
                continue
            texts.append(combine_text(title, description))
            categories.append(category)
            priorities.append(priority)
    return texts, categories, priorities, skipped


def load_datasets(paths: list[Path]) -> tuple[list[str], list[str], list[str]]:
    texts, categories, priorities = [], [], []
    for path in paths:
        t, c, p, skipped = load_dataset(path)
        print(f"  {path}: {len(t)} chamados" + (f" ({skipped} linhas inválidas descartadas)" if skipped else ""))
        texts += t
        categories += c
        priorities += p
    return texts, categories, priorities


def evaluate(texts: list[str], labels: list[str], name: str) -> dict:
    """Validação cruzada estratificada (mantém a proporção das classes em cada parte).

    Cada parte precisa de ao menos 1 exemplo de cada classe, então o número de
    partes é limitado pela menor classe. Com uma classe de 1 só exemplo (comum
    logo que o ADMIN cria uma categoria nova) não dá para avaliar: pulamos a
    avaliação em vez de quebrar o treino.
    """
    smallest_class = min(Counter(labels).values())
    n_splits = min(MAX_FOLDS, smallest_class)
    if n_splits < 2:
        print(f"\n=== {name}: avaliação PULADA (a menor classe tem só {smallest_class} exemplo) ===")
        return {"accuracy": None, "macro_f1": None}

    folds = StratifiedKFold(n_splits=n_splits, shuffle=True, random_state=42)
    predicted = cross_val_predict(build_pipeline(), texts, labels, cv=folds)
    print(f"\n=== {name}: validação cruzada ({n_splits} partes) ===")
    print(classification_report(labels, predicted, zero_division=0))
    return {
        "accuracy": round(float(accuracy_score(labels, predicted)), 4),
        "macro_f1": round(float(f1_score(labels, predicted, average="macro")), 4),
    }


def _summary(name: str, metrics: dict) -> str:
    if metrics["accuracy"] is None:
        return f"{name} -> não avaliada (poucos exemplos em alguma classe)"
    return f"{name} -> acurácia {metrics['accuracy']:.2%} | F1 macro {metrics['macro_f1']:.2f}"


def train(data: Path | list[Path], out_dir: Path) -> dict:
    paths = [data] if isinstance(data, Path) else list(data)
    print(f"Carregando {len(paths)} arquivo(s):")
    texts, categories, priorities = load_datasets(paths)
    if not texts:
        raise ValueError("Nenhum chamado válido encontrado nos arquivos informados.")
    print(f"Total: {len(texts)} chamados, {len(set(categories))} categorias")

    metrics = {
        "samples": len(texts),
        "category": evaluate(texts, categories, "CATEGORIA"),
        "priority": evaluate(texts, priorities, "PRIORIDADE"),
    }

    category_model = build_pipeline().fit(texts, categories)
    priority_model = build_pipeline().fit(texts, priorities)

    # A versão identifica os dados de treino: dados diferentes -> versão diferente.
    digest = hashlib.sha256()
    for path in paths:
        digest.update(path.read_bytes())
    version = "v1-" + digest.hexdigest()[:8]

    out_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump({"category": category_model, "priority": priority_model, "version": version},
                out_dir / MODEL_FILE)
    (out_dir / METRICS_FILE).write_text(json.dumps({"version": version, **metrics}, indent=2), encoding="utf-8")

    print(f"\nModelo {version} salvo em {out_dir / MODEL_FILE}")
    print(_summary("Categoria ", metrics["category"]))
    print(_summary("Prioridade", metrics["priority"]))
    return metrics


def main() -> None:
    parser = argparse.ArgumentParser(description="Treina os classificadores do ServiceFlow Intelligence")
    parser.add_argument("--data", type=Path, nargs="+", default=[Path("data/tickets.csv")],
                        help="um ou mais CSVs (title,description,category,priority)")
    parser.add_argument("--out", type=Path, default=Path("models"))
    args = parser.parse_args()
    train(args.data, args.out)


if __name__ == "__main__":
    main()
