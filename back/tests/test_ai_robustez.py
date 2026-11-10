"""Testes de robustez da geração por IA (H3-22/H3-25).

Funções puras: parse tolerante, salvamento parcial, resumo por request,
check ID↔nome. Testes de endpoint usam cliente HTTP falso (sem SenseNova).
"""

import json
import os
import re
import tempfile

import httpx
import pytest
from fastapi.testclient import TestClient

from app import database as db
from app import main
from app.plan_builder import (
    _build_pool,
    _is_strength_exercise,
    catalog_summary_for_request,
    catalog_summary_metrics,
    id_name_mismatches,
    resolve_plan_ids,
    resolve_exercise_names,
)


def setup_test_db():
    fd, path = tempfile.mkstemp(suffix=".db")
    os.close(fd)
    db.DB_PATH = path
    db.init_db()
    return path


def _client():
    setup_test_db()
    return TestClient(main.app)


def _payload(**overrides):
    payload = {
        "deviceId": "d1",
        "goal": "hypertrophy",
        "daysPerWeek": 2,
        "availableEquipment": ["barbell", "dumbbell", "body only"],
        "targetMuscles": ["chest", "back"],
        "sessionDurationMinutes": 60,
        "currentLevel": "intermediate",
    }
    payload.update(overrides)
    return payload


class _FakeResponse:
    def __init__(self, content, status_code=200, text=""):
        self.status_code = status_code
        self.text = text
        self._content = content

    def json(self):
        return {"choices": [{"message": {"content": self._content}}]}


class _SequenceClient:
    """httpx.AsyncClient falso com sequência de respostas/erros por chamada."""

    steps = []

    def __init__(self, *args, **kwargs):
        type(self).calls = getattr(type(self), "calls", 0)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, *args, **kwargs):
        idx = min(type(self).calls, len(type(self).steps) - 1)
        type(self).calls += 1
        step = type(self).steps[idx]
        if isinstance(step, Exception):
            raise step
        if isinstance(step, tuple):
            content, status, text = step
            return _FakeResponse(content, status_code=status, text=text)
        return _FakeResponse(step)


def _strength_plan(exercise_ids, target_muscles=None):
    target_muscles = target_muscles or ["chest", "back"]
    return {
        "name": "Plano da IA",
        "description": "",
        "goal": "hypertrophy",
        "daysPerWeek": 1,
        "days": [
            {
                "name": "Dia 1",
                "dayOrder": 0,
                "weekday": 1,
                "modality": "strength",
                "isRestDay": False,
                "exercises": [
                    {
                        "exerciseId": ex_id,
                        "exerciseOrder": i,
                        "sets": 3,
                        "targetReps": "8-12",
                        "targetRpe": 8.0,
                        "restSeconds": 90,
                        "notes": "",
                        "isSupersetWithPrevious": False,
                        "metricType": "reps_weight",
                    }
                    for i, ex_id in enumerate(exercise_ids)
                ],
            }
        ],
    }


def _valid_ids(n=6):
    # IDs reais do catálogo, filtrados por força, distintos.
    ids = []
    seen = set()
    for ex in main.EXERCISE_CATALOG:
        ex_id = ex.get("id")
        if not ex_id or ex_id in seen:
            continue
        cat = str(ex.get("category", "")).lower()
        if cat in {"stretching", "cardio"}:
            continue
        ids.append(ex_id)
        seen.add(ex_id)
        if len(ids) >= n:
            break
    return ids


def _request_for_parse(target_muscles=None, equipment=None):
    return main.GenerateRequest(
        deviceId="d1",
        goal="hypertrophy",
        daysPerWeek=2,
        availableEquipment=equipment or ["barbell", "dumbbell", "body only", "cable", "machine"],
        targetMuscles=target_muscles or ["chest", "back", "legs"],
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )


# ---------------- parse tolerante ----------------


def test_parse_remove_cercas_de_codigo():
    plano = _strength_plan(_valid_ids(4))
    content = "```json\n" + json.dumps(plano) + "\n```"
    parsed = main.parse_ai_plan(content, main.EXERCISE_CATALOG, _request_for_parse())
    assert parsed["days"][0]["exercises"]


def test_parse_extrai_objeto_balanceado():
    plano = _strength_plan(_valid_ids(4))
    content = "Aqui está o plano:\n" + json.dumps(plano) + "\nObrigado."
    parsed = main.parse_ai_plan(content, main.EXERCISE_CATALOG, _request_for_parse())
    assert parsed["name"] == "Plano da IA"
    assert parsed["days"]


# ---------------- salvamento parcial ----------------


def test_id_invalido_e_removido_e_sessao_mantida():
    ids = _valid_ids(6)
    ids[2] = "ZZZ_Fake_Exercise"
    plano = _strength_plan(ids)
    parsed = main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, _request_for_parse())
    got_ids = [ex["exerciseId"] for ex in parsed["days"][0]["exercises"]]
    assert "ZZZ_Fake_Exercise" not in got_ids
    assert len(got_ids) == 5


def test_sessao_com_menos_de_tres_e_reconstruida():
    ids = _valid_ids(6)
    # 4 inválidos => sobram 2 => reconstrução local completa a sessão.
    for i in range(4):
        ids[i] = f"ZZZ_Fake_{i}"
    plano = _strength_plan(ids)
    req = _request_for_parse(target_muscles=["chest", "back", "legs"])
    parsed = main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, req)
    assert len(parsed["days"][0]["exercises"]) >= 3
    # Todos os exercícios finais existem no catálogo.
    catalog_ids = {ex["id"] for ex in main.EXERCISE_CATALOG}
    for ex in parsed["days"][0]["exercises"]:
        assert ex["exerciseId"] in catalog_ids


def test_todos_invalidos_vira_fallback_total():
    plano = _strength_plan([f"ZZZ_Fake_{i}" for i in range(6)])
    with pytest.raises(main.AiFailure) as excinfo:
        main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, _request_for_parse())
    assert excinfo.value.reason == "unresolved_ids"


# ---------------- resolução por nome (H3-25) ----------------


def test_resolve_exercise_names_preenche_id_vazio_com_nome_canonico():
    """resolve_exercise_names preenche exerciseId quando exerciseName é canônico."""
    req = _request_for_parse()
    # Pega um exercício real do catálogo
    ex = next(e for e in main.EXERCISE_CATALOG if e.get("namePt") and e.get("equipment") in ["barbell", "dumbbell", "body only", "cable", "machine"])
    canonical_name = ex["namePt"]
    ex_id = ex["id"]

    plan = {
        "days": [
            {
                "exercises": [
                    {"exerciseName": canonical_name, "exerciseId": "", "exerciseOrder": 0, "metricType": "reps_weight"}
                ]
            }
        ]
    }
    resolved, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert not drops
    assert resolved["days"][0]["exercises"][0]["exerciseId"] == ex_id


def test_resolve_exercise_names_drop_nome_nao_resolvivel():
    """Nome inexistente gera drop com reason=unresolved_name."""
    req = _request_for_parse()
    plan = {
        "days": [
            {
                "exercises": [
                    {"exerciseName": "Exercício Inexistente XYZ", "exerciseId": "", "exerciseOrder": 0, "metricType": "reps_weight"}
                ]
            }
        ]
    }
    resolved, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert len(drops) == 1
    assert drops[0]["reason"] == "unresolved_name"
    assert resolved["days"][0]["exercises"] == []


def test_resolve_exercise_names_preserva_sinteticos():
    """run_*/mobility_* não passam por resolução de nome do catálogo."""
    req = _request_for_parse()
    plan = {
        "days": [
            {"exercises": [{"exerciseId": "run_easy", "exerciseOrder": 0, "metricType": "distance_duration"}]},
            {"exercises": [{"exerciseId": "mobility_full", "exerciseOrder": 0, "metricType": "duration"}]},
        ]
    }
    resolved, drops = resolve_exercise_names(plan, main.EXERCISE_CATALOG, req)
    assert not drops
    assert resolved["days"][0]["exercises"][0]["exerciseId"] == "run_easy"
    assert resolved["days"][1]["exercises"][0]["exerciseId"] == "mobility_full"


# ---------------- resumo por request (formato compacto H3-25) ----------------


def test_resumo_formato_compacto_sem_id_inline():
    """Novo formato: ' - <nome canônico> (<eq>)' sem ID inline."""
    req = main.GenerateRequest(
        targetMuscles=["chest"],
        availableEquipment=["barbell", "dumbbell", "body only"],
        daysPerWeek=2,
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    lines = [l for l in summary.splitlines() if l.strip()]
    # Cabeçalho com instrução
    assert "copie o nome exatamente como listado" in lines[0]
    assert "o backend resolve o ID" in lines[0]
    assert "não invente nomes" in lines[0]
    # Linhas de exercício do Tier A: formato '  - <nome> (<eq>)'.
    tier_a_part = summary.split("ÍNDICE DE EXERCÍCIOS COMPLEMENTARES")[0]
    exercise_lines = [l for l in tier_a_part.splitlines() if l.strip().startswith("- ")]
    assert exercise_lines, "Esperava linhas de exercício no Tier A"
    for line in exercise_lines:
        # Não deve ter o padrão antigo de ID inline: "<ID> — <nome> (<eq>)"
        assert " — " not in line.split("(")[0], f"Parece conter ID inline: {line}"
        assert "(" in line and ")" in line, f"Formato inesperado: {line}"


def test_resumo_inclui_todos_elegiveis_sem_cap_por():
    """Pool completo do request: o Tier A cobre todos os exercicios do foco
    (sem cap por grupo) e o Tier B cobre o restante como IDs."""
    req = main.GenerateRequest(
        targetMuscles=["chest", "back", "legs", "shoulders", "arms", "abs", "glutes", "calves"],
        availableEquipment=["barbell", "dumbbell", "body only", "cable", "machine",
                            "kettlebells", "bands", "medicine ball"],
        daysPerWeek=4,
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    # Conta itens em CHEST no Tier A (ate 8 detalhados) + Tier B (restante).
    tier_a_part = summary.split("ÍNDICE DE EXERCÍCIOS COMPLEMENTARES")[0]
    tier_b_part = summary.split("ÍNDICE DE EXERCÍCIOS COMPLEMENTARES")[1]
    in_a = False
    chest_a = 0
    for l in tier_a_part.splitlines():
        if l.strip() == "CHEST:":
            in_a = True
            continue
        if in_a and l.endswith(":"):
            break
        if in_a and l.strip().startswith("- "):
            chest_a += 1
    chest_b = 0
    for l in tier_b_part.splitlines():
        if l.strip().startswith("- "):
            chest_b += 1
    assert chest_a >= 8, f"Esperava >= 8 detalhados em chest no Tier A, got {chest_a}"
    assert chest_b > 0, "Esperava exercicios restantes no Tier B"
def test_resumo_nao_lista_equipamento_fora_request():
    """Equipamento não informado não aparece no resumo."""
    req = main.GenerateRequest(
        targetMuscles=["chest"],
        availableEquipment=["body only"],
        daysPerWeek=2,
        sessionDurationMinutes=45,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    # Complementar estrito
    comp = main.GenerateRequest(
        environment="bodyweight",
        focusMuscles=["abs"],
        daysPerWeek=2,
        sessionDurationMinutes=30,
        currentLevel="intermediate",
    )
    comp_summary = catalog_summary_for_request(comp, main.EXERCISE_CATALOG)
    for forbidden in ["(barbell", "(dumbbell", "(machine", "(cable", "(kettlebell", "(bands"]:
        assert forbidden not in comp_summary.lower()


def test_resumo_metrics_baseline_compativel():
    """Métricas chars/linhas/tokens_aprox para comparação com baseline do experimento."""
    req = _request_for_parse()
    metrics = catalog_summary_metrics(req, main.EXERCISE_CATALOG)
    assert metrics["chars"] > 0
    assert metrics["linhas"] > 0
    assert metrics["tokens_aprox"] == metrics["chars"] // 4
    # Formato compacto deve ser menor que o antigo com IDs (T1: 37.819 chars)
    assert metrics["chars"] < 37819


# ---------------- retry 429 ----------------


def test_retry_unico_em_429(monkeypatch):
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    _SequenceClient.calls = 0
    _SequenceClient.steps = [
        ("", 429, "rate limited"),
        json.dumps(_strength_plan(_valid_ids(4))),
    ]
    monkeypatch.setattr(main.httpx, "AsyncClient", _SequenceClient)

    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "ai"
    assert body["fallbackReason"] == ""
    assert _SequenceClient.calls == 2


# ---------------- retry content vazio (T5) ----------------


def test_retry_em_content_vazio_http200(monkeypatch):
    """HTTP 200 com content vazio/inútil → 1 retry → fallback json_parse se persistir."""
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    _SequenceClient.calls = 0
    # Primeira resposta: HTTP 200 mas content vazio
    # Segunda resposta: também vazia → fallback
    _SequenceClient.steps = [
        ("", 200, ""),
        ("", 200, ""),
    ]
    monkeypatch.setattr(main.httpx, "AsyncClient", _SequenceClient)

    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "local"
    assert body["fallbackReason"] == "json_parse"
    assert _SequenceClient.calls == 2


# ---------------- orçamento total de IA (deadline) ----------------


class _FakeClock:
    """Relógio controlado pelo teste: monotonic() sem dormir de verdade."""

    def __init__(self):
        self.now = 0.0

    def __call__(self):
        return self.now


class _HangingAIClient:
    """IA que nunca responde: cada post queima todo o read timeout no relógio.

    Registra o timeout real que o endpoint passou ao httpx.AsyncClient, para
    medir o pior caso de latência sem dormir de verdade.
    """

    read_timeouts = []
    clock = None

    def __init__(self, *args, **kwargs):
        self._read = kwargs["timeout"].read
        type(self).read_timeouts.append(self._read)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, *args, **kwargs):
        type(self).clock.now += self._read
        raise httpx.ReadTimeout("IA não respondeu dentro do read timeout")


def test_pior_caso_ia_lenta_responde_antes_do_timeout_do_app(monkeypatch):
    """IA pendurada: o backend ainda responde (fallback) dentro dos 90s do app.

    O app desiste após 90s de read timeout (BackendClient.kt) e mostra
    "O servidor demorou demais para responder". Com a IA pendurada, o pior
    caso é o orçamento total da IA; ele precisa ficar abaixo dos 90s, senão
    o usuário nunca recebe o plano substituto. Timeout na 1ª esgota o
    orçamento: não há retry, o fallback local responde direto.
    """
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.delenv("AI_TIMEOUT_TOTAL_SECONDS", raising=False)
    clock = _FakeClock()
    monkeypatch.setattr(main, "monotonic", clock)
    _HangingAIClient.read_timeouts = []
    _HangingAIClient.clock = clock
    monkeypatch.setattr(main.httpx, "AsyncClient", _HangingAIClient)

    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "local"
    assert body["fallbackReason"] == "timeout"
    assert len(_HangingAIClient.read_timeouts) == 1
    pior_caso = sum(_HangingAIClient.read_timeouts)
    assert pior_caso < 90.0, (
        f"pior caso {pior_caso}s ≥ read timeout do app (90s): "
        "o app desiste antes de o backend responder"
    )
    assert clock.now < 90.0


class _SlowInvalidJsonClient:
    """IA responde JSON inválido 1s antes de estourar o read timeout."""

    calls = 0
    clock = None

    def __init__(self, *args, **kwargs):
        self._read = kwargs["timeout"].read

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args):
        return False

    async def post(self, *args, **kwargs):
        type(self).calls += 1
        type(self).clock.now += self._read - 1.0
        return _FakeResponse("isso não é json")


def test_resposta_lenta_invalida_nao_ganha_retry_que_estoura_orcamento(monkeypatch):
    """Resposta inválida após ~79s: retry de timeout fixo estouraria os 90s do app.

    Com orçamento total, o retry só existe com tempo restante; aqui sobra ~1s
    (< mínimo de 3s), então o endpoint cai no fallback local ainda dentro dos
    90s do app.
    """
    monkeypatch.setattr(main, "SENSENOVA_API_KEY", "chave-de-teste")
    monkeypatch.delenv("AI_TIMEOUT_TOTAL_SECONDS", raising=False)
    clock = _FakeClock()
    monkeypatch.setattr(main, "monotonic", clock)
    _SlowInvalidJsonClient.calls = 0
    _SlowInvalidJsonClient.clock = clock
    monkeypatch.setattr(main.httpx, "AsyncClient", _SlowInvalidJsonClient)

    response = _client().post("/v1/generate-workout", json=_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "local"
    assert body["fallbackReason"] == "json_parse"
    assert _SlowInvalidJsonClient.calls == 1
    assert clock.now < 90.0


# ---------------- check ID↔nome ----------------


def test_par_id_nome_divergente_e_removido():
    ids = _valid_ids(6)
    plano = _strength_plan(ids)
    catalog_by_id = {ex["id"]: ex for ex in main.EXERCISE_CATALOG}
    # Força divergência clara: nome declarado nada a ver com o oficial.
    target = ids[0]
    plano["days"][0]["exercises"][0]["exerciseId"] = target
    plano["days"][0]["exercises"][0]["exerciseName"] = "Completely Different Exercise XYZ"
    # Garante que o nome oficial não é esse.
    assert catalog_by_id[target]["name"] != "Completely Different Exercise XYZ"

    parsed = main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, _request_for_parse())
    got_ids = [ex["exerciseId"] for ex in parsed["days"][0]["exercises"]]
    assert target not in got_ids
    assert len(got_ids) >= 3


def test_nome_aproximado_nao_e_divergencia():
    ids = _valid_ids(4)
    plano = _strength_plan(ids)
    catalog_by_id = {ex["id"]: ex for ex in main.EXERCISE_CATALOG}
    target = ids[0]
    official = catalog_by_id[target]["name"]
    # Nome abreviado/aproximado: usa parte do oficial.
    plano["days"][0]["exercises"][0]["exerciseName"] = official.split()[0] if official.split() else official
    # Se abreviação ficar vazia/igual, usa lowercase do oficial.
    if not plano["days"][0]["exercises"][0]["exerciseName"]:
        plano["days"][0]["exercises"][0]["exerciseName"] = official.lower()

    mismatches = id_name_mismatches(plano, main.EXERCISE_CATALOG)
    mismatch_ids = {m["exerciseId"] for m in mismatches}
    assert target not in mismatch_ids

    parsed = main.parse_ai_plan(json.dumps(plano), main.EXERCISE_CATALOG, _request_for_parse())
    got_ids = [ex["exerciseId"] for ex in parsed["days"][0]["exercises"]]
    assert target in got_ids


# ---------------- plano B (dois níveis, H3-22) ----------------


def test_plano_b_tier_b_entra_quando_tier_a_estoura_limite():
    """Request amplo (muitos músculos/equipamentos) ativa o Tier B."""
    req = main.GenerateRequest(
        targetMuscles=["chest", "back", "legs", "shoulders", "arms", "abs", "glutes", "calves"],
        availableEquipment=["barbell", "dumbbell", "body only", "cable", "machine",
                            "kettlebells", "bands", "medicine ball"],
        daysPerWeek=4,
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    assert "ÍNDICE DE EXERCÍCIOS COMPLEMENTARES" in summary
    assert "prefira sempre os nomes do bloco acima" in summary


def test_plano_b_nao_entra_em_request_estreito():
    """Request com poucos exercícios por músculo e todos do foco: Tier A só."""
    req = main.GenerateRequest(
        targetMuscles=["chest"],
        availableEquipment=["barbell"],
        daysPerWeek=2,
        sessionDurationMinutes=45,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    assert "ÍNDICE DE EXERCÍCIOS COMPLEMENTARES" not in summary


def test_plano_b_entra_quando_cap_por_musulo_e_excedido():
    """Request amplo (muitos músculos/equipamentos) ativa o Tier B."""
    req = main.GenerateRequest(
        targetMuscles=["chest", "back", "legs", "shoulders", "arms", "abs", "glutes", "calves"],
        availableEquipment=["barbell", "dumbbell", "body only", "cable", "machine",
                            "kettlebells", "bands", "medicine ball"],
        daysPerWeek=4,
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    assert "ÍNDICE DE EXERCÍCIOS COMPLEMENTARES" in summary
def test_plano_b_nenhum_id_elegivel_sai_do_prompt():
    """Cada ID do pool elegível do request aparece no prompt (Tier A ou Tier B)."""
    from app.plan_builder import _build_pool, _is_strength_exercise, normalize
    req = main.GenerateRequest(
        targetMuscles=["chest", "back", "legs", "shoulders", "arms", "abs", "glutes", "calves"],
        availableEquipment=["barbell", "dumbbell", "body only", "cable", "machine",
                            "kettlebells", "bands", "medicine ball"],
        daysPerWeek=4,
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )
    pool = [
        e for e in _build_pool(main.EXERCISE_CATALOG, req.availableEquipment, req.currentLevel)
        if _is_strength_exercise(e)
    ]
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    tier_b_part = summary.split("ÍNDICE DE EXERCÍCIOS COMPLEMENTARES")[1]
    tier_a_part = summary.split("ÍNDICE DE EXERCÍCIOS COMPLEMENTARES")[0]
    tier_b_ids = set(re.findall(r"^\s+- (\S+)$", tier_b_part, re.M))
    by_name = {}
    for e in pool:
        for key in (e.get("name", ""), e.get("namePt", "")):
            if key:
                by_name.setdefault(normalize(key), e["id"])
    # Formato do Tier A: '  - <nome> (<eq>)' — o nome pode conter parênteses
    # (ex.: "Butt Lift (Bridge) (body only)"), então captura o (<eq>) do final.
    tier_a_names = re.findall(r"^\s+- (.+) \(([^)]+)\)\s*$", tier_a_part, re.M)
    tier_a_ids = {by_name.get(normalize(n)) for n, _ in tier_a_names if by_name.get(normalize(n))}
    prompt_ids = tier_a_ids | tier_b_ids
    missing = [e["id"] for e in pool if e["id"] not in prompt_ids]
    assert not missing, f"IDs do pool que saíram do prompt: {missing[:10]}"


def test_plano_b_exercício_atende_a_varios_grupos():
    """Exercício com múltiplos primaryMuscles aparece em todos os grupos que atende."""
    req = main.GenerateRequest(
        targetMuscles=["chest", "back", "legs", "shoulders", "arms", "abs", "glutes", "calves"],
        availableEquipment=["barbell", "dumbbell", "body only", "cable", "machine",
                            "kettlebells", "bands", "medicine ball"],
        daysPerWeek=4,
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )
    summary = catalog_summary_for_request(req, main.EXERCISE_CATALOG)
    # Exercícios com múltiplos músculos primários devem aparecer em cada grupo
    # que atendem (ex.: um exercício de peito+ombros em CHEST e SHOULDERS).
    multi = [e for e in main.EXERCISE_CATALOG
             if len(e.get("primaryMuscles", [])) > 1
             and _is_strength_exercise(e)
             and e.get("equipment") in {"barbell", "dumbbell", "body only", "cable", "machine", "kettlebells"}]
    assert multi, "Esperava pelo menos um exercício com múltiplos músculos primários"
    sample = multi[0]
    # O exemplo deve aparecer no prompt: ou por ID (Tier B) ou por nome canônico (Tier A).
    tier_b_part = summary.split("ÍNDICE DE EXERCÍCIOS COMPLEMENTARES")[1]
    tier_a_part = summary.split("ÍNDICE DE EXERCÍCIOS COMPLEMENTARES")[0]
    in_tier_b = sample["id"] in tier_b_part
    in_tier_a = (sample.get("name") or "") in tier_a_part or (sample.get("namePt") or "") in tier_a_part
    assert in_tier_b or in_tier_a, f"{sample['id']} ({sample.get('name')}) saiu do prompt"
def test_plano_b_tamaho_prompt_abaixo_do_baseline():
    """Prompt com Tier B é menor que o baseline de 23.019 chars (H3-22 medição)."""
    req = main.GenerateRequest(
        targetMuscles=["chest", "back", "legs", "shoulders", "arms", "abs", "glutes", "calves"],
        availableEquipment=["barbell", "dumbbell", "body only", "cable", "machine",
                            "kettlebells", "bands", "medicine ball"],
        daysPerWeek=4,
        sessionDurationMinutes=60,
        currentLevel="intermediate",
    )
    metrics = catalog_summary_metrics(req, main.EXERCISE_CATALOG)
    assert metrics["chars"] < 23019, f"Prompt ainda grande: {metrics['chars']} chars"
