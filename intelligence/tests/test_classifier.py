import pytest

from app.classifier import TicketClassifier


@pytest.fixture(scope="module")
def classifier(trained):
    model_path, _ = trained
    return TicketClassifier.load(model_path)


def test_model_beats_chance_on_unseen_data(trained):
    """Guarda contra regressões: se alguém piorar os dados/modelo, o teste acusa.
    Métricas vêm da validação cruzada (dados que o modelo não viu no treino)."""
    _, metrics = trained
    # Pisos com margem sobre o desempenho medido (~72% e ~57% em validação cruzada
    # repetida), só para pegar regressões grosseiras; não são metas de qualidade.
    assert metrics["category"]["accuracy"] >= 0.60   # 6 categorias: acaso seria ~17%
    assert metrics["priority"]["accuracy"] >= 0.45   # 4 prioridades: acaso ~25%, "sempre P4" ~32%


@pytest.mark.parametrize(
    "title, description, expected_category",
    [
        ("Impressora sem toner", "A impressora do meu setor parou de imprimir e o toner acabou", "Impressora"),
        ("Esqueci a senha", "Não consigo entrar no sistema porque esqueci minha senha de acesso", "Acesso e Senha"),
        ("Wifi caindo", "A conexão de internet do escritório cai toda hora", "Rede"),
        ("Outlook travando", "Meu e-mail não abre e as mensagens não chegam", "E-mail"),
        ("Monitor apagando", "A tela do monitor fica preta e o computador não liga direito", "Hardware"),
        ("Erro no ERP", "O sistema apresenta erro ao emitir relatório e trava o programa", "Software"),
    ],
)
def test_category_on_new_phrasings(classifier, title, description, expected_category):
    # frases que NÃO estão no CSV de treino
    suggestion = classifier.suggest(title, description)
    assert suggestion.category.label == expected_category


def test_prediction_structure_is_consistent(classifier):
    suggestion = classifier.suggest("Sem internet", "Toda a empresa está sem conexão")

    for prediction in (suggestion.category, suggestion.priority):
        scores = [prediction.confidence] + [a.confidence for a in prediction.alternatives]
        assert scores == sorted(scores, reverse=True)          # do mais para o menos provável
        assert all(0 <= s <= 1 for s in scores)
        assert prediction.label not in [a.label for a in prediction.alternatives]

    assert len(suggestion.category.alternatives) == 2           # top 3 = 1 + 2 alternativas
    assert suggestion.priority.label in {"P1", "P2", "P3", "P4"}
    assert suggestion.model_version.startswith("v1-")


def test_accents_and_case_do_not_change_the_prediction(classifier):
    with_accents = classifier.suggest("CONEXÃO CAIU", "Sem conexão de rede na sala")
    without = classifier.suggest("conexao caiu", "sem conexao de rede na sala")

    assert with_accents.category.label == without.category.label
    assert with_accents.category.confidence == without.category.confidence


def test_loading_a_missing_model_fails_with_clear_message(tmp_path):
    with pytest.raises(FileNotFoundError, match="python -m app.train"):
        TicketClassifier.load(tmp_path / "nao-existe.joblib")
