"""Testes dos templates de corrida (H3-11): rotação, escala e benchmark."""

from app.run_templates import template_for, RUN_TEMPLATES, TEMPLATE_ORDER


def test_rotaciona_templates_por_sessao():
    """Sessões diferentes usam templates diferentes (rotação)."""
    ids = [
        template_for("intermediate", None, i, 40)["exerciseId"]
        for i in range(6)
    ]
    assert len(set(ids)) > 1, f"templates não rotacionaram: {ids}"


def qualquer_tipo_usado(ids, prefix):
    return any(i.startswith(prefix) for i in ids)


def test_escala_intervalos_pela_duracao():
    """Duração maior → mais sets (clamp entre 3 e 20)."""
    curto = template_for("intermediate", None, 0, 30)["sets"]
    longo = template_for("intermediate", None, 0, 90)["sets"]
    assert 3 <= curto <= 20
    assert 3 <= longo <= 20
    assert longo >= curto


def test_benchmark_invalido_usa_rpe():
    """Benchmark inválido (0, negativo, > 3h) não define pace (usa RPE)."""
    for inv in [0, -100, 20000]:
        block = template_for("intermediate", inv, 0, 40)
        assert block["targetPaceSecondsPerKm"] is None, inv


def test_corrida_iniciante_usa_caminhada():
    """Iniciante sem benchmark usa run_walk e pace None."""
    block = template_for("beginner", None, 0, 40)
    assert block["exerciseId"] == "run_walk"
    assert block["targetPaceSecondsPerKm"] is None


def test_todas_as_sessoes_usam_template_valido():
    """Cada sessão (índice 0..N) retorna um template com os campos obrigatórios."""
    obrigatorios = {"exerciseId", "exerciseName", "sets", "metricType", "targetRpe"}
    for i in range(12):
        block = template_for("advanced", 1800, i, 60)
        assert obrigatorios <= set(block.keys()), block
