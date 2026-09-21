"""Testes do treino com dados adicionais (o fluxo de retreino com chamados reais)."""
import shutil

import pytest

from app.classifier import TicketClassifier
from app.train import MODEL_FILE, load_dataset, train
from tests.conftest import DATA_PATH

HEADER = "title,description,category,priority\n"


def write_csv(path, body):
    # write_bytes: sem a conversão "\n" -> "\r\n" que o write_text faz no Windows,
    # para o arquivo ser idêntico ao baixado da API Java.
    path.write_bytes((HEADER + body).encode("utf-8"))
    return path


def test_rows_with_missing_fields_or_invalid_priority_are_discarded(tmp_path):
    csv_file = write_csv(tmp_path / "x.csv",
        "Ok,Descricao ok,Rede,P1\n"
        ",sem titulo,Rede,P1\n"                 # título vazio
        "Sem cat,Descricao,,P2\n"               # categoria vazia
        "Prio ruim,Descricao,Rede,P9\n"         # prioridade inválida
        "Sem prio,Descricao,Rede,\n")           # prioridade vazia

    texts, categories, priorities, skipped = load_dataset(csv_file)

    assert len(texts) == 1
    assert skipped == 4
    assert (categories, priorities) == (["Rede"], ["P1"])


def test_parses_the_csv_produced_by_the_java_export(tmp_path):
    # Formato exato do TrainingDataService: campos com vírgula/quebra de linha entre aspas,
    # e fórmulas neutralizadas com apóstrofo.
    csv_file = write_csv(tmp_path / "export.csv",
        "\"'=cmd|' /C calc'!A0\",\"linha 1, com vírgula\nlinha 2\",Rede,P2\n"
        "Impressora parada,\"disse \"\"oi\"\"\",Impressora,P3\n")

    texts, categories, priorities, skipped = load_dataset(csv_file)

    assert skipped == 0
    assert len(texts) == 2
    assert "linha 1, com vírgula\nlinha 2" in texts[0]
    assert 'disse "oi"' in texts[1]


def test_training_with_extra_data_learns_new_categories_and_changes_the_version(tmp_path):
    extra = write_csv(tmp_path / "real.csv", "".join(
        f"Ramal {i} mudo,O telefone do ramal {i} nao completa ligacoes,Telefonia,P3\n" for i in range(8)))

    base_dir, mixed_dir = tmp_path / "base", tmp_path / "mixed"
    base_metrics = train(DATA_PATH, base_dir)
    mixed_metrics = train([DATA_PATH, extra], mixed_dir)

    assert mixed_metrics["samples"] == base_metrics["samples"] + 8

    base = TicketClassifier.load(base_dir / MODEL_FILE)
    mixed = TicketClassifier.load(mixed_dir / MODEL_FILE)
    assert mixed.version != base.version                       # dados diferentes -> versão diferente
    assert mixed.suggest("Ramal mudo", "O telefone do ramal nao completa ligacoes").category.label == "Telefonia"


def test_a_class_with_a_single_example_does_not_break_training(tmp_path):
    # Situação real: o ADMIN acabou de criar a categoria "Telefonia" e só há 1 chamado nela.
    extra = write_csv(tmp_path / "poucos.csv", "Ramal mudo,Telefone sem ligacoes,Telefonia,P3\n")
    combined = tmp_path / "combined.csv"
    shutil.copy(DATA_PATH, combined)
    combined.write_text(combined.read_text(encoding="utf-8") + "Ramal mudo,Telefone sem ligacoes,Telefonia,P3\n",
                        encoding="utf-8")

    metrics = train(combined, tmp_path / "out")

    assert metrics["category"]["accuracy"] is None            # não deu para avaliar: sinalizado, não quebrado
    assert metrics["priority"]["accuracy"] is not None        # prioridades continuam avaliáveis
    assert TicketClassifier.load(tmp_path / "out" / MODEL_FILE).suggest("x", "y").category.label


def test_training_without_valid_rows_fails_with_a_clear_message(tmp_path):
    empty = write_csv(tmp_path / "vazio.csv", ",,,\n")

    with pytest.raises(ValueError, match="Nenhum chamado válido"):
        train(empty, tmp_path / "out")
