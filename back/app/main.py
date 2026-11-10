import asyncio
import json
import os
import random
import re
import socket
import threading
import uuid
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path
from time import monotonic
from typing import Any, Dict, List, Literal, Optional

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, model_validator
from dotenv import load_dotenv
from zeroconf import ServiceInfo, Zeroconf

# Carrega back/.env antes de importar módulos que leem variáveis de ambiente
# (no Docker as variáveis já vêm injetadas).
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from . import database as db
from .plan_builder import (
    MUSCLE_ALIASES,
    build_local_plan,
    build_hybrid_plan,
    build_strength_session,
    catalog_summary_for_request,
    catalog_summary_metrics,
    complementary_exercise_count,
    complementary_focus_label,
    complementary_plan_name,
    enforce_complementary_plan,
    enrich_plan,
    id_name_mismatches,
    normalize,
    resolve_exercise_names,
    resolve_plan_ids,
    request_language,
    review_date,
    profile_adjustments,
    suggest_initial_load,
    review_plan,
    normalize_hybrid_plan,
    is_complementary_request,
    complementary_equipment_filter,
    _build_pool,
    _expand_muscles,
    _is_strength_exercise,
    build_shortlist,
    shortlist_summary_for_request,
    shortlist_summary_metrics,
)
from .run_templates import template_for

# Config
SENSENOVA_API_KEY = os.getenv("SENSENOVA_API_KEY")
SENSENOVA_BASE_URL = os.getenv("SENSENOVA_BASE_URL", "https://token.sensenova.ai/v1")
MODEL_ID = os.getenv("MODEL_ID", "sensenova-6.8-flash")
MDNS_ENABLED = os.getenv("MDNS_ENABLED", "true").strip().lower() not in {"0", "false", "no"}

# T7 experiment flag: usar shortlist com IDs + schema rígido
# Valores: "0"/"false"/"no" = desligado (padrão), "1"/"true"/"yes" = ligado
EXPERIMENT_SHORTLIST = os.getenv("EXPERIMENT_SHORTLIST", "0").strip().lower() in {"1", "true", "yes"}
SHORTLIST_PER_MUSCLE = int(os.getenv("SHORTLIST_PER_MUSCLE", "12"))

if not SENSENOVA_API_KEY:
    print("WARNING: SENSENOVA_API_KEY not set!")

# Load exercise catalog
CATALOG_PATH = os.path.join(os.path.dirname(__file__), "exercise_catalog.json")
if os.path.exists(CATALOG_PATH):
    with open(CATALOG_PATH, "r", encoding="utf-8") as f:
        EXERCISE_CATALOG = json.load(f)
else:
    EXERCISE_CATALOG = []
    print(f"WARNING: Exercise catalog not found at {CATALOG_PATH}")

SYSTEM_PROMPT_HYBRID_RULES = """Você é um gerador de planos de treino híbrido (musculação, corrida, mobilidade).

{catalog_summary}

REGRAS OBRIGATÓRIAS:
1. Para exercícios de musculação: use o **nome canônico listado no catálogo acima** como `exerciseName` (copie exatamente como aparece). O `exerciseId` é opcional para musculação — o backend resolve pelo nome. IDs sintéticos de corrida/mobilidade (abaixo) devem ser usados como `exerciseId`.
2. Corrida use EXATAMENTE um destes IDs sintéticos (não existem no catálogo, são válidos):
   `run_easy`, `run_walk`, `run_interval`, `run_tempo`, `run_long`.
3. Mobilidade use EXATAMENTE um destes IDs sintéticos: `mobility_full`,
   `mobility_back`, `mobility_hips`, `mobility_post_run` (não existem no catálogo, são válidos).
4. Nunca invente outro ID para corrida ou mobilidade (ex.: não use `Run_5K_Easy`,
   `Pigeon_Pose`, `Hip_Flexor_Stretch`).
5. Um `day` = uma sessão = uma única modalidade. Nunca misture musculação, corrida e mobilidade no mesmo `day`.
6. Musculação: 4 a 8 exercícios por `day`, todos `metricType = "reps_weight"`, categoria de força, sem alongamento/cardio.
7. Corrida: exatamente 1 bloco por `day`, `metricType = "distance_duration"`, com `targetDistanceMeters` e/ou `targetRpe`; `sets` até 20.
8. Mobilidade: exatamente 1 bloco por `day`, `metricType = "duration"`, `targetDurationSeconds` entre 300 e 900.
9. `weekday` 1..7. Várias sessões no mesmo `weekday` são `day`s separados com `dayOrder` diferente.
10. Respeite equipamentos, nível, objetivo e duração informados por modalidade.
11. Retorne JSON válido conforme schema WorkoutPlanDTO.

FORMATO DE SAÍDA (JSON):
{{
  "name": "string",
  "description": "string",
  "goal": "general",
  "daysPerWeek": inteiro (total de sessões, não dias distintos),
  "days": [
    {{
      "name": "string (ex: 'Academia A — Superiores', 'Corrida — intervalado', 'Mobilidade — costas')",
      "dayOrder": 0,
      "weekday": 1-7,
      "modality": "strength|run|mobility",
      "isRestDay": false,
      "exercises": [
        {{
          "exerciseId": "string (ID EXATO do catálogo OU sintético run_*/mobility_*; opcional para musculação se exerciseName for o nome canônico listado)",
          "exerciseName": "string (nome canônico listado no catálogo para musculação; para run/mobility use o ID sintético como nome)",
          "exerciseOrder": 0,
          "sets": 1-20,
          "targetReps": "string ou vazio para corrida/mobilidade",
          "targetRpe": 5.0-10.0,
          "restSeconds": 0-300,
          "notes": "string",
          "isSupersetWithPrevious": false,
          "metricType": "reps_weight|distance_duration|duration",
          "targetDurationSeconds": inteiro ou null,
          "targetDistanceMeters": número ou null,
          "targetPaceSecondsPerKm": inteiro ou null,
          "targetHrZone": null
        }}
      ]
    }}
  ]
}}"""

SYSTEM_PROMPT_STRENGTH_RULES = """Você é um gerador de treinos de musculação baseado em evidências.

{catalog_summary}

REGRAS OBRIGATÓRIAS:
1. Use o **nome canônico listado no catálogo acima** como `exerciseName` (copie exatamente como aparece). O `exerciseId` é opcional — o backend resolve pelo nome.
2. Retorne JSON válido conforme schema WorkoutPlanDTO
3. Não invente IDs para corrida/mobilidade fora dos casos híbridos.
4. Respeite equipamentos disponíveis do usuário
5. Balanceie volume entre grupos musculares
6. Inclua aquecimento e alongamento sugeridos nas notas quando fizer sentido
7. Retorne JSON válido conforme schema WorkoutPlanDTO.

FORMATO DE SAÍDA (JSON):
{{
  "name": "string",
  "description": "string",
  "goal": "hypertrophy|strength|endurance|power|fat_loss|general",
  "daysPerWeek": 1-6,
  "days": [
    {{
      "name": "string",
      "dayOrder": 0,
      "weekday": 1-7,
      "modality": "strength",
      "isRestDay": false,
      "exercises": [
        {{
          "exerciseId": "string (ID EXATO do catálogo; opcional se exerciseName for o nome canônico listado)",
          "exerciseName": "string (nome canônico listado no catálogo)",
          "exerciseOrder": 0,
          "sets": 1-20,
          "targetReps": "string (ex: '8-12', '10', 'AMRAP')",
          "targetRpe": 5.0-10.0,
          "restSeconds": 0-300,
          "notes": "string",
          "isSupersetWithPrevious": false
        }}
      ]
    }}
  ]
}}"""


# Restrições do plano complementar (H3-16) anexadas ao prompt de sistema quando
# o request traz `focusMuscles`. Sem foco, o prompt híbrido é exatamente o de hoje.
COMPLEMENTARY_SYSTEM_RULES = """

RESTRIÇÕES DE PLANO COMPLEMENTAR (obrigatórias):
1. Apenas sessões de força (musculação). Nenhum `day` de corrida ou mobilidade.
2. FOCO EXCLUSIVO: todo exercício deve ter primaryMuscles dentro do foco
   informado. Nenhum exercício de outro grupo muscular, nem para "completar".
3. EQUIPAMENTO ESTRICTO: use apenas os equipamentos permitidos informados;
   nunca substitua por barra, máquina ou polia fora da lista.
4. Sessões curtas: 3 a 6 exercícios por `day`, conforme a duração informada.
"""


# T7 — Shortlist + IDs + schema rígido
# Shortlist determinística (~12 por grupo) com IDs exatos; IA deve usar APENAS
# estes IDs. Prompt compacto, schema estrito, sem nomes livres.
SYSTEM_PROMPT_SHORTLIST = """Você é um gerador de treinos de musculação baseado em evidências.

{shortlist_summary}

REGRAS OBRIGATÓRIAS (ESTRITAS):
1. Use APENAS os IDs listados na SHORTLIST acima. Não invente IDs, não use IDs
   fora da lista, não use nomes livres. O `exerciseId` DEVE ser um dos IDs da
   shortlist. O `exerciseName` deve copiar exatamente o nome listado ao lado
   do ID.
2. Retorne JSON válido conforme schema WorkoutPlanDTO.
3. Respeite equipamentos, nível, objetivo e duração informados.
4. Musculação: 4 a 8 exercícios por `day`, todos `metricType = "reps_weight"`,
   categoria de força, sem alongamento/cardio.
5. Um `day` = uma sessão = uma única modalidade (apenas strength neste modo).
6. `weekday` 1..7. Várias sessões no mesmo `weekday` são `day`s separados.
7. Não inclua corrida nem mobilidade.

FORMATO DE SAÍDA (JSON):
{{
  "name": "string",
  "description": "string",
  "goal": "hypertrophy|strength|endurance|power|fat_loss|general",
  "daysPerWeek": 2-6,
  "days": [
    {{
      "name": "string",
      "dayOrder": 0,
      "weekday": 1-7,
      "modality": "strength",
      "isRestDay": false,
      "exercises": [
        {{
          "exerciseId": "string (ID EXATO da shortlist acima; OBRIGATÓRIO)",
          "exerciseName": "string (nome canônico listado ao lado do ID na shortlist)",
          "exerciseOrder": 0,
          "sets": 1-20,
          "targetReps": "string (ex: '8-12', '10', 'AMRAP')",
          "targetRpe": 5.0-10.0,
          "restSeconds": 0-300,
          "notes": "string",
          "isSupersetWithPrevious": false,
          "metricType": "reps_weight"
        }}
      ]
    }}
  ]
}}"""


def system_prompt_for_request(request: "GenerateRequest") -> str:
    """Prompt de sistema por request (H3-22): catálogo filtrado ao pool.

    T7 experiment: quando EXPERIMENT_SHORTLIST=true, usa shortlist com IDs
    exatos e schema rígido (apenas plano principal de força, sem modalidades
    híbridas nem complementar).
    """
    modalities = getattr(request, "modalities", None) or []
    is_complementary = is_complementary_request(request)

    # T7: shortlist mode apenas para plano principal (sem modalidades, sem foco)
    if EXPERIMENT_SHORTLIST and not modalities and not is_complementary:
        shortlist_summary = shortlist_summary_for_request(
            request, EXERCISE_CATALOG, SHORTLIST_PER_MUSCLE
        )
        base = SYSTEM_PROMPT_SHORTLIST.format(shortlist_summary=shortlist_summary)
    else:
        summary = catalog_summary_for_request(request, EXERCISE_CATALOG)
        if modalities or is_complementary:
            base = SYSTEM_PROMPT_HYBRID_RULES.format(catalog_summary=summary)
            if is_complementary:
                base += COMPLEMENTARY_SYSTEM_RULES
        else:
            base = SYSTEM_PROMPT_STRENGTH_RULES.format(catalog_summary=summary)

    # H3-36: o catálogo do summary já está no idioma do request; a diretiva
    # explícita evita a IA traduzir por conta própria (ou voltar ao EN).
    if request_language(request) == "pt":
        base += (
            "\n\nIDIOMA: o catálogo acima está em português. Escreva "
            "`exerciseName` e as notas em português, usando exatamente os nomes "
            "do catálogo. O `exerciseId` permanece o ID exato do catálogo."
        )
    else:
        base += (
            "\n\nIDIOMA: escreva `exerciseName` e as notas em inglês, usando "
            "exatamente os nomes do catálogo. O `exerciseId` permanece o ID "
            "exato do catálogo."
        )
    return base


def system_prompt_hybrid(complementary: bool = False) -> str:
    """Compatibilidade para callers legados; sem request usa catálogo vazio."""
    # Evita prompt global enorme; chamadores novos devem usar system_prompt_for_request.
    base = SYSTEM_PROMPT_HYBRID_RULES.format(
        catalog_summary="(catálogo filtrado por request; use system_prompt_for_request)"
    )
    if complementary:
        base += COMPLEMENTARY_SYSTEM_RULES
    return base


# Compat: export legado ainda referenciado em testes antigos, se houver.
SYSTEM_PROMPT = SYSTEM_PROMPT_STRENGTH_RULES.format(
    catalog_summary="(catálogo filtrado por request; use system_prompt_for_request)"
)
SYSTEM_PROMPT_HYBRID = SYSTEM_PROMPT_HYBRID_RULES.format(
    catalog_summary="(catálogo filtrado por request; use system_prompt_for_request)"
)


# mDNS/Zeroconf for LAN discovery
def get_local_ip():
    """Get the local IP address for mDNS registration"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

class MDNSService:
    def __init__(self, port: int = 3000):
        self.port = port
        self.zeroconf = None
        self.service_info = None
        self.running = False
    
    def start(self):
        if self.running:
            return
        try:
            self.zeroconf = Zeroconf()
            ip = get_local_ip()
            self.service_info = ServiceInfo(
                "_treinos._tcp.local.",
                "TreinosBackend._treinos._tcp.local.",
                addresses=[socket.inet_aton(ip)],
                port=self.port,
                properties={
                    b"version": b"1.0.0",
                    b"api": b"v1",
                    b"model": MODEL_ID.encode()
                },
                server=f"treinos-backend.local."
            )
            self.zeroconf.register_service(self.service_info)
            self.running = True
            print(f"mDNS service registered: TreinosBackend at {ip}:{self.port}")
        except Exception as e:
            print(f"Failed to register mDNS service: {e}")
    
    def stop(self):
        if self.running and self.zeroconf and self.service_info:
            try:
                self.zeroconf.unregister_service(self.service_info)
                self.zeroconf.close()
                self.running = False
                print("mDNS service unregistered")
            except Exception as e:
                print(f"Failed to unregister mDNS service: {e}")


mdns_service = MDNSService()

# Tasks dos jobs de geração em voo (H3-40), para cancel/shutdown.
_job_tasks: Dict[str, asyncio.Task] = {}

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    db.init_db()
    # H3-40: jobs pending/running de uma execução anterior não têm task viva
    # neste processo — o app oferece "Tentar de novo" a partir do failed.
    interrupted = db.mark_interrupted_jobs()
    if interrupted:
        print(f"[jobs] {interrupted} job(s) interrompido(s) marcados como failed")
    print("Treinos Backend started")
    print(f"Model: {MODEL_ID}")
    print(f"Catalog loaded: {len(EXERCISE_CATALOG)} exercises")
    print(f"SQLite: {db.DB_PATH}")
    if MDNS_ENABLED:
        # Não bloqueia a subida do servidor se a rede não permitir multicast.
        threading.Thread(target=mdns_service.start, name="mdns-register", daemon=True).start()
    else:
        print("mDNS disabled (MDNS_ENABLED=false)")
    yield
    # Shutdown: cancela os jobs em voo (best-effort); o sweep do próximo
    # startup cobre o que sobrar pending/running (ex.: SIGKILL).
    for task in list(_job_tasks.values()):
        task.cancel()
    if _job_tasks:
        await asyncio.gather(*list(_job_tasks.values()), return_exceptions=True)
    _job_tasks.clear()
    if MDNS_ENABLED:
        mdns_service.stop()
    print("Treinos Backend shutting down")


app = FastAPI(
    title="Treinos Backend",
    description="Backend para geração de treinos via IA (SenseNova)",
    version="1.0.0",
    lifespan=lifespan
)

# CORS for local development
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# Schemas
class ModalityRequest(BaseModel):
    """Uma modalidade da semana híbrida (strength / run / mobility)."""

    type: Literal["strength", "run", "mobility"]
    sessionsPerWeek: int = Field(ge=1, le=7, default=1)
    goal: Optional[Literal["hypertrophy", "strength", "endurance", "power", "fat_loss", "general"]] = None
    level: Optional[Literal["beginner", "intermediate", "advanced"]] = None
    availableEquipment: List[str] = Field(default_factory=list)
    targetMuscles: List[str] = Field(default_factory=list)
    focus: Optional[str] = None
    sessionDurationMinutes: int = Field(ge=5, le=120, default=60)
    recent5kSeconds: Optional[float] = None


class GenerateRequest(BaseModel):
    deviceId: str = "default"
    goal: Literal["hypertrophy", "strength", "endurance", "power", "fat_loss", "general"] = "hypertrophy"
    environment: Optional[Literal["gym", "home", "bodyweight"]] = None
    # Limites estáticos cobrem o modo complementar (1..6 dias, 15..120 min);
    # o validador abaixo recoloca os limites do plano principal (2..6, 30..120)
    # quando não há foco exclusivo.
    daysPerWeek: int = Field(ge=1, le=6, default=4)
    availableEquipment: List[str] = Field(default_factory=list)  # e.g., ["dumbbell", "barbell", "body only"]
    targetMuscles: List[str] = Field(default_factory=list)       # e.g., ["chest", "back", "legs"]
    injuriesOrLimitations: str = ""
    currentLevel: Literal["beginner", "intermediate", "advanced"] = "intermediate"
    sessionDurationMinutes: int = Field(ge=15, le=120, default=60)
    preferredSplit: Optional[Literal["full_body", "upper_lower", "push_pull_legs", "bro_split"]] = None
    reviewAfterWeeks: int = Field(ge=1, le=12, default=4)
    bodyWeightKg: Optional[float] = Field(default=None, ge=30.0, le=300.0)
    heightCm: Optional[int] = Field(default=None, ge=120, le=250)
    sex: Optional[Literal["male", "female", "other", "undisclosed"]] = None
    modalities: Optional[List[ModalityRequest]] = Field(
        default_factory=list,
        description="Lista de modalidades da semana híbrida (strength/run/mobility). "
        "Quando vazia, gera apenas musculação (compatível com v1).",
    )
    focusMuscles: Optional[List[str]] = Field(
        default=None,
        description="Foco EXCLUSIVO do plano complementar (H3-16), 1–4 grupos de "
        "MUSCLE_ALIASES (ex.: ['abs']). Presente ⇒ plano só de força.",
    )
    # H3-36: idioma do conteúdo do plano (nomes/instruções dos exercícios).
    # Ausente ⇒ "en" — comportamento atual preservado para app antigo; "pt" ⇒
    # exerciseName e instructions em português, com o mesmo exerciseId.
    language: Optional[Literal["pt", "en"]] = None

    @model_validator(mode="after")
    def validate_complementary(self) -> "GenerateRequest":
        """Validações condicionais do modo complementar (H3-16).

        Sem `focusMuscles`, as regras do plano principal valem como antes
        (2–6 dias, 30–120 min). Com `focusMuscles`, o plano é só de força,
        aceita 1–6 dias e 15–120 min, e cada grupo precisa existir em
        MUSCLE_ALIASES.
        """
        focus = self.focusMuscles
        if focus is None:
            if self.daysPerWeek < 2:
                raise ValueError(
                    "daysPerWeek deve ser entre 2 e 6 "
                    "(com foco exclusivo, 1 a 6)."
                )
            if self.sessionDurationMinutes < 30:
                raise ValueError(
                    "sessionDurationMinutes deve ser entre 30 e 120 "
                    "(com foco exclusivo, 15 a 120)."
                )
            return self

        if len(focus) == 0:
            raise ValueError(
                "focusMuscles não pode ser uma lista vazia: omita o campo "
                "quando não houver foco exclusivo."
            )
        if len(focus) > 4:
            raise ValueError(
                "focusMuscles aceita no máximo 4 grupos "
                f"(recebido {len(focus)})."
            )
        invalid = [m for m in focus if str(m).strip().lower() not in MUSCLE_ALIASES]
        if invalid:
            raise ValueError(
                "Grupo muscular inválido em focusMuscles: "
                f"{', '.join(invalid)}. "
                f"Valores válidos: {', '.join(sorted(MUSCLE_ALIASES))}."
            )
        if self.modalities:
            non_strength = [m.type for m in self.modalities if m.type != "strength"]
            if non_strength:
                raise ValueError(
                    "Plano complementar é apenas de força; modalidades "
                    f"inválidas: {', '.join(non_strength)}. "
                    "Use modalities = ['strength']."
                )
        return self


class ExerciseDTO(BaseModel):
    exerciseId: str
    exerciseOrder: int
    sets: int = Field(ge=1, le=20)
    targetReps: str
    targetRpe: Optional[float] = Field(default=None, ge=5.0, le=10.0)
    restSeconds: int = Field(ge=0, le=300, default=90)
    notes: str = ""
    isSupersetWithPrevious: bool = False
    exerciseName: str = ""
    primaryMuscles: List[str] = Field(default_factory=list)
    equipment: str = ""
    instructions: List[str] = Field(default_factory=list)
    images: List[str] = Field(default_factory=list)
    metricType: str = "reps_weight"
    targetDurationSeconds: Optional[int] = None
    targetDistanceMeters: Optional[float] = None
    targetPaceSecondsPerKm: Optional[int] = None
    targetHrZone: Optional[str] = None


class DayDTO(BaseModel):
    name: str
    dayOrder: int
    exercises: List[ExerciseDTO]
    isRestDay: bool = False
    weekday: Optional[int] = None
    modality: str = "strength"


class WorkoutPlanDTO(BaseModel):
    name: str
    description: str = ""
    goal: str
    daysPerWeek: int
    days: List[DayDTO]


class GenerateResponse(BaseModel):
    workout: WorkoutPlanDTO
    generationTimeMs: int
    modelUsed: str
    planId: int = 0
    nextReviewAt: str
    reviewAfterWeeks: int = 4
    source: str = "local"
    # Diagnóstico do fallback (H3-21): vazio quando source == "ai"; quando
    # source == "local" carrega um dos códigos fechados em FALLBACK_REASONS.
    fallbackReason: str = ""


class GenerateJobAck(BaseModel):
    """Resposta imediata do submit (H3-40): a IA roda em background."""

    jobId: str
    status: Literal["pending"] = "pending"


class GenerateJobStatus(BaseModel):
    jobId: str
    status: Literal["pending", "running", "done", "failed", "cancelled"]
    createdAt: str
    result: Optional[GenerateResponse] = None
    error: Optional[str] = None


class GenerateReviewRequest(BaseModel):
    deviceId: str = "default"
    plan: Dict[str, Any]
    history: Dict[str, Any] = Field(default_factory=dict)


class GenerateReviewResponse(BaseModel):
    workout: WorkoutPlanDTO
    changes: List[str] = Field(default_factory=list)
    source: str = "local"


# ----- Modelos de sincronização app -> backend -----

class SyncSetLog(BaseModel):
    localId: int
    setNumber: int
    reps: Optional[int] = None
    weightKg: Optional[float] = None
    rpe: Optional[float] = None
    durationSeconds: Optional[int] = None
    distanceMeters: Optional[float] = None
    isCompleted: bool = False
    isWarmup: bool = False
    restTakenSeconds: Optional[int] = None
    completedAt: Optional[int] = None


class SyncSessionExercise(BaseModel):
    localId: int
    exerciseId: str
    exerciseName: str = ""
    exerciseOrder: int = 0
    plannedSets: int = 0
    completedSets: int = 0
    startedAt: int = 0
    endedAt: Optional[int] = None
    sets: List[SyncSetLog] = Field(default_factory=list)


class SyncSession(BaseModel):
    localId: int
    planLocalId: int = 0
    dayLocalId: int = 0
    planName: str = ""
    dayName: str = ""
    status: str = ""
    startedAt: int = 0
    endedAt: Optional[int] = None
    totalDurationSeconds: int = 0
    notes: str = ""
    exercises: List[SyncSessionExercise] = Field(default_factory=list)
    modality: str = "strength"
    source: str = "plan"
    title: str = ""
    totalDistanceMeters: Optional[float] = None
    poolLengthMeters: Optional[int] = None
    stroke: Optional[str] = None
    sessionRpe: Optional[float] = None


class SyncPlanExercise(BaseModel):
    exerciseId: str
    exerciseOrder: int = 0
    sets: int = 3
    targetReps: str = ""
    targetRpe: Optional[float] = None
    restSeconds: int = 90
    notes: str = ""
    isSupersetWithPrevious: bool = False
    exerciseName: str = ""
    primaryMuscles: List[str] = Field(default_factory=list)
    equipment: str = ""
    metricType: str = "reps_weight"
    targetDurationSeconds: Optional[int] = None
    targetDistanceMeters: Optional[float] = None
    targetPaceSecondsPerKm: Optional[int] = None
    targetHrZone: Optional[str] = None


class SyncDay(BaseModel):
    localId: int
    name: str = ""
    dayOrder: int = 0
    notes: str = ""
    isRestDay: bool = False
    exercises: List[SyncPlanExercise] = Field(default_factory=list)
    modality: str = "strength"
    weekday: Optional[int] = None


class SyncPlan(BaseModel):
    localId: int
    name: str = ""
    description: str = ""
    goal: str = ""
    daysPerWeek: int = 0
    isActive: bool = False
    generatedByAi: bool = False
    createdAt: int = 0
    updatedAt: int = 0
    version: int = 1
    nextReviewAt: Optional[str] = None
    reviewAfterWeeks: int = 4
    source: str = "local"
    kind: str = "main"
    days: List[SyncDay] = Field(default_factory=list)


class SyncRequest(BaseModel):
    deviceId: str
    syncedAt: Optional[str] = None
    plans: List[SyncPlan] = Field(default_factory=list)
    sessions: List[SyncSession] = Field(default_factory=list)


class SyncResponse(BaseModel):
    ok: bool
    plans: int
    sessions: int
    setLogs: int
    syncedAt: str


# Routes
@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "model": MODEL_ID,
        "catalogSize": len(EXERCISE_CATALOG),
        "database": str(db.DB_PATH),
    }


# Rótulos em pt-BR para o prompt (H3-7). O valor de contrato nunca muda.
SEX_LABELS_PT = {
    "female": "feminino",
    "male": "masculino",
    "other": "outro",
    "undisclosed": "prefiro não informar",
}


def build_user_prompt(request: GenerateRequest) -> str:
    """Monta o prompt do usuário para a IA, incluindo perfil quando disponível."""
    equipment_str = ", ".join(request.availableEquipment) if request.availableEquipment else "nenhum (apenas peso corporal)"
    muscles_str = ", ".join(request.targetMuscles) if request.targetMuscles else "corpo todo"

    prompt = f"""Gere um treino de {request.goal} com as seguintes restrições:
- Dias por semana: {request.daysPerWeek}
- Equipamentos disponíveis: {equipment_str}
- Foco muscular: {muscles_str}
- Nível: {request.currentLevel}
- Duração por sessão: ~{request.sessionDurationMinutes} min
- Split preferido: {request.preferredSplit or 'livre'}
- Lesões/limitações: {request.injuriesOrLimitations or 'nenhuma'}
- Idioma dos nomes/instruções dos exercícios: {
        'português (use exatamente os nomes em português do catálogo)'
        if request_language(request) == 'pt'
        else 'inglês (use exatamente os nomes em inglês do catálogo)'
    }

O treino deve ser progressivo, balanceado e usar apenas exercícios do catálogo fornecido.
Retorne APENAS o JSON válido."""

    if request.sex is not None:
        prompt += f"""

- Sexo biológico: {SEX_LABELS_PT[request.sex]}"""

    if request.bodyWeightKg is not None and request.heightCm is not None:
        bmi = request.bodyWeightKg / ((request.heightCm / 100.0) ** 2)
        prompt += f"""

- Peso corporal: {request.bodyWeightKg} kg
- Altura: {request.heightCm} cm
- IMC: {bmi:.1f}
- Use o perfil para calibrar descanso, RPE, volume e cargas iniciais.
  Sugestões de carga são ponto de partida, não prescrição médica."""

    return prompt


def build_shortlist_user_prompt(request: GenerateRequest) -> str:
    """Prompt do usuário para modo T7 shortlist: reforça uso exclusivo da shortlist."""
    equipment_str = ", ".join(request.availableEquipment) if request.availableEquipment else "nenhum (apenas peso corporal)"
    muscles_str = ", ".join(request.targetMuscles) if request.targetMuscles else "corpo todo"

    prompt = f"""Gere um treino de {request.goal} usando APENAS os exercícios da SHORTLIST
fornecida no prompt de sistema. Regras estritas:
- Dias por semana: {request.daysPerWeek}
- Equipamentos disponíveis: {equipment_str}
- Foco muscular: {muscles_str}
- Nível: {request.currentLevel}
- Duração por sessão: ~{request.sessionDurationMinutes} min
- Split preferido: {request.preferredSplit or 'livre'}
- Lesões/limitações: {request.injuriesOrLimitations or 'nenhuma'}
- Idioma: {
        'português (copie o exerciseName exatamente da shortlist)'
        if request_language(request) == 'pt'
        else 'inglês (copie o exerciseName exatamente da shortlist)'
    }

O treino deve ser progressivo, balanceado e usar SOMENTE IDs da shortlist.
Retorne APENAS o JSON válido."""

    if request.sex is not None:
        prompt += f"""

- Sexo biológico: {SEX_LABELS_PT[request.sex]}"""

    if request.bodyWeightKg is not None and request.heightCm is not None:
        bmi = request.bodyWeightKg / ((request.heightCm / 100.0) ** 2)
        prompt += f"""

- Peso corporal: {request.bodyWeightKg} kg
- Altura: {request.heightCm} cm
- IMC: {bmi:.1f}
- Use o perfil para calibrar descanso, RPE, volume e cargas iniciais."""

    return prompt


def build_hybrid_user_prompt(request: GenerateRequest) -> str:
    """Prompt para geração de semana híbrida (musculação/corrida/mobilidade)."""
    environment = request.environment or "inferir pelos equipamentos"
    equipment_str = ", ".join(request.availableEquipment) if request.availableEquipment else "nenhum (apenas peso corporal)"
    muscles_str = ", ".join(request.targetMuscles) if request.targetMuscles else "corpo todo"

    lines = [
        "Gere uma semana híbrida com sessões independentes por modalidade.",
        f"- Ambiente: {environment}",
        f"- Equipamentos disponíveis: {equipment_str}",
        f"- Foco muscular global: {muscles_str}",
        f"- Nível: {request.currentLevel}",
        f"- Objetivo global: {request.goal}",
        f"- Revisar após: {request.reviewAfterWeeks} semanas",
        # H3-36: idioma do conteúdo segue o request (ausente ⇒ inglês).
        "- Idioma dos nomes/instruções dos exercícios: "
        + (
            "português (use exatamente os nomes em português do catálogo)"
            if request_language(request) == "pt"
            else "inglês (use exatamente os nomes em inglês do catálogo)"
        ),
        "",
        "Modalidades (cada uma gera sessões próprias, nunca misturadas num único dia):",
    ]
    if request.targetMuscles and not is_complementary_request(request):
        # H3-23: no plano principal, foco muscular é prioridade (não
        # exclusividade) — a regra explícita evita a semana genérica sem o
        # grupo pedido. No complementar a regra vale a exclusividade (H3-16).
        lines.insert(
            4,
            'Regra de prioridade: cada sessão de força deve incluir pelo menos '
            '1 exercício de cada grupo em "Foco muscular global" (quando existir '
            'no catálogo); o restante da sessão completa com o split do ciclo.',
        )
    if is_complementary_request(request):
        # Plano complementar (H3-16): foco exclusivo e equipamento estrito
        # entram no prompt para a IA não responder fora das restrições.
        allowed = complementary_equipment_filter(request)
        allowed_str = (
            ", ".join(sorted(allowed)) if allowed else "os equipamentos informados"
        )
        lines.insert(
            1,
            "PLANO COMPLEMENTAR — foque EXCLUSIVAMENTE em: "
            + ", ".join(request.focusMuscles or [])
            + ". Nenhum exercício fora desse foco; apenas força.",
        )
        lines.insert(2, f"- Equipamentos PERMITIDOS (estrito): {allowed_str}")
    for m in request.modalities or []:
        lines.append(
            f"- {m.type}: {m.sessionsPerWeek} sessão(ões)/semana"
            + (f", objetivo={m.goal}" if m.goal else "")
            + (f", nível={m.level}" if m.level else "")
            + (f", equipamentos={', '.join(m.availableEquipment)}" if m.availableEquipment else "")
            + (f", foco muscular={', '.join(m.targetMuscles)}" if m.targetMuscles else "")
            + (f", foco={m.focus}" if m.focus else "")
            + (f", duração/sessão={m.sessionDurationMinutes} min" if m.sessionDurationMinutes else "")
            + (
                f", benchmark5k={m.recent5kSeconds}s"
                if m.recent5kSeconds is not None
                else ""
            )
        )
    lines.append("")
    lines.append(
        "Distribua as sessões pelos dias da semana (seg=1 ... dom=7), permitindo "
        "mais de uma sessão no mesmo dia como `day`s separados. Cada `day` tem "
        "apenas uma modalidade. Corrige usa distância/duração/RPE (não reps); "
        "mobilidade usa duração; musculação usa reps/peso. Retorne APENAS o JSON válido."
    )
    return "\n".join(lines)


# ----- Diagnóstico do fallback da IA (H3-21) -----

# Códigos fechados de `fallbackReason` (contrato compartilhado com o app;
# ver docs/specs/H3-21-diagnostico-fallback-ia.md).
FALLBACK_REASONS = {
    "no_api_key",         # SENSENOVA_API_KEY ausente
    "http_error",         # API respondeu != 200 (detail traz o status)
    "timeout",            # httpx.TimeoutException / erro de rede
    "json_parse",         # conteúdo não é JSON (inclui JSON truncado)
    "unresolved_ids",     # IDs de musculação que não existem no catálogo
    "schema_validation",  # WorkoutPlanDTO rejeitou a resposta
    "unknown",            # qualquer outra exceção
}


class AiFailure(Exception):
    """Falha conhecida da geração por IA, com código fechado de diagnóstico.

    `reason` é um dos códigos em `FALLBACK_REASONS`; `detail` é texto curto
    para o log e nunca contém a chave da API nem o prompt completo.
    """

    def __init__(self, reason: str, detail: str = ""):
        if reason not in FALLBACK_REASONS:
            raise ValueError(f"reason de fallback inválido: {reason}")
        super().__init__(detail or reason)
        self.reason = reason
        self.detail = detail


def _strip_code_fences(content: str) -> str:
    text = (content or "").strip()
    if not text.startswith("```"):
        return text
    # Remove opening fence (```json / ```).
    text = re.sub(r"^```[a-zA-Z0-9_-]*\s*", "", text)
    text = re.sub(r"\s*```$", "", text)
    return text.strip()


def _extract_first_json_object(content: str) -> str:
    """Extrai o primeiro objeto JSON balanceado; tolera texto ao redor (H3-22)."""
    text = _strip_code_fences(content)
    start = text.find("{")
    if start == -1:
        return text
    depth = 0
    in_string = False
    escape = False
    for i in range(start, len(text)):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return text[start : i + 1]
    return text[start:]


def parse_ai_plan(
    content: str,
    catalog: List[Dict[str, Any]],
    request: Optional["GenerateRequest"] = None,
) -> Dict[str, Any]:
    """Converte a resposta bruta da IA em plano validado — função pura, sem rede.

    H3-25: resolve exerciseName → exerciseId (nome canônico listado) ANTES de
    resolve_plan_ids/validação DTO. exerciseId opcional/ausente é preenchido;
    ID válido+nome divergente mantém id_name_mismatch (H3-22); nome/ID não
    resolvível ⇒ drop + salvamento parcial/reconstrução, nunca derruba plano
    inteiro; todos inválidos ⇒ fallbackReason=unresolved_ids; run_*/mobility_*
    preservados.
    """
    try:
        raw_plan = json.loads(_extract_first_json_object(content))
    except (json.JSONDecodeError, TypeError) as exc:
        raise AiFailure("json_parse", f"JSON inválido: {exc}") from exc
    if not isinstance(raw_plan, dict):
        raise AiFailure(
            "json_parse", f"conteúdo não é um objeto JSON ({type(raw_plan).__name__})"
        )

    original_strength_count = sum(
        1
        for day in raw_plan.get("days", [])
        if str(day.get("modality", "strength")) == "strength" and not day.get("isRestDay")
        for ex in day.get("exercises", [])
        if str(ex.get("metricType", "reps_weight")) == "reps_weight" or not ex.get("metricType")
    )

    # H3-25: Resolve exerciseName → exerciseId ANTES de resolve_plan_ids.
    # Usa o pool elegível do request para restringir a resolução.
    resolution_count = 0
    name_drops: List[Dict[str, Any]] = []
    if request is not None:
        raw_plan, name_drops = resolve_exercise_names(raw_plan, catalog, request)
        resolution_count = sum(
            1
            for day in raw_plan.get("days", [])
            for ex in day.get("exercises", [])
            if str(ex.get("metricType", "reps_weight")) == "reps_weight" or not ex.get("metricType")
        )
        for drop in name_drops:
            print(fallback_log_line("dropped_ids", f"{drop.get('exerciseName', 'unknown')}:{drop.get('reason', 'unresolved_name')}"))

    # Resolve IDs (remove inválidos) e registra drops.
    raw_plan, dropped_ids = resolve_plan_ids(raw_plan, catalog)
    dropped_reasons = [f"{ex_id}:unresolved_id" for ex_id in dropped_ids]

    # Check ID↔nome divergente antes do enrich sobrescrever exerciseName.
    for mismatch in id_name_mismatches(raw_plan, catalog):
        ex_id = mismatch["exerciseId"]
        dropped_reasons.append(f"{ex_id}:id_name_mismatch")
        for day in raw_plan.get("days", []):
            day["exercises"] = [
                ex for ex in day.get("exercises", []) if ex.get("exerciseId") != ex_id
            ]

    if dropped_reasons:
        print(
            fallback_log_line(
                "dropped_ids",
                "; ".join(dropped_reasons[:12]),
            )
        )

    surviving_strength_count = sum(
        1
        for day in raw_plan.get("days", [])
        if str(day.get("modality", "strength")) == "strength" and not day.get("isRestDay")
        for ex in day.get("exercises", [])
        if str(ex.get("metricType", "reps_weight")) == "reps_weight" or not ex.get("metricType")
    )
    has_non_strength = any(
        str(day.get("modality", "strength")) != "strength"
        or any(
            str(ex.get("metricType", "")) in {"distance_duration", "duration"}
            for ex in day.get("exercises", [])
        )
        for day in raw_plan.get("days", [])
    )
    # Critério H3-22/H3-25: todos os exercícios de força inválidos => fallback total.
    if original_strength_count > 0 and surviving_strength_count == 0 and not has_non_strength:
        raise AiFailure(
            "unresolved_ids",
            "IDs retornados pela IA não existem no catálogo: "
            + ", ".join(dropped_ids[:5]) if dropped_ids else "nenhum exercício aproveitável",
        )

    # Enrich early? schema doesn't require metadata, but we need it for salvage
    # decisions based on modality/metric; keep structure.
    salvage_strength_days(raw_plan, catalog, request)

    # Falha total: nenhum exercício aproveitável em nenhuma sessão de força.
    strength_exercises = [
        ex
        for day in raw_plan.get("days", [])
        if str(day.get("modality", "strength")) == "strength" and not day.get("isRestDay")
        for ex in day.get("exercises", [])
        if str(ex.get("metricType", "reps_weight")) == "reps_weight"
        or not ex.get("metricType")
    ]
    if not strength_exercises and not has_non_strength:
        raise AiFailure(
            "unresolved_ids",
            "nenhum exercício aproveitável após remoção de IDs inválidos/divergentes",
        )

    # Remove dias vazios de força; mantém run/mobility se tiverem bloco.
    cleaned_days = []
    for day in raw_plan.get("days", []):
        if not day.get("exercises"):
            continue
        cleaned_days.append(day)
    if not cleaned_days:
        raise AiFailure(
            "unresolved_ids",
            "nenhum exercício aproveitável após salvamento parcial",
        )
    raw_plan["days"] = cleaned_days
    raw_plan["daysPerWeek"] = raw_plan.get("daysPerWeek") or len(cleaned_days)

    try:
        WorkoutPlanDTO(**raw_plan)
    except Exception as exc:
        raise AiFailure("schema_validation", f"plano rejeitado pelo schema: {exc}") from exc
    return raw_plan


def salvage_strength_days(
    raw_plan: Dict[str, Any],
    catalog: List[Dict[str, Any]],
    request: Optional["GenerateRequest"],
) -> None:
    """Reconstrói dias de força com < 3 exercícios usando o pool do request (H3-22).

    Mutação in-place. Corrida/mobilidade não são reconstruídas aqui. Sem request
    (testes puros antigos), usa defaults conservadores.
    """
    if request is None:
        return
    for idx, day in enumerate(raw_plan.get("days", [])):
        modality = str(day.get("modality", "strength"))
        if modality != "strength" or day.get("isRestDay"):
            continue
        strength_exs = [
            ex
            for ex in day.get("exercises", [])
            if str(ex.get("metricType", "reps_weight")) == "reps_weight"
        ]
        if len(strength_exs) >= 3:
            day["exercises"] = strength_exs + [
                ex
                for ex in day.get("exercises", [])
                if str(ex.get("metricType", "reps_weight")) != "reps_weight"
            ]
            continue

        # Reconstrói a sessão de força localmente com o pool do request.
        equipment = list(getattr(request, "availableEquipment", []) or [])
        level = str(getattr(request, "currentLevel", "intermediate") or "intermediate")
        goal = str(getattr(request, "goal", "general") or "general")
        duration = int(getattr(request, "sessionDurationMinutes", 60) or 60)
        muscles = list(getattr(request, "targetMuscles", []) or [])
        environment = str(getattr(request, "environment", "") or "").strip().lower() or "bodyweight"
        strict_equipment = complementary_equipment_filter(request)
        pool = _build_pool(catalog, equipment, level, strict_equipment=strict_equipment)
        used = {ex.get("exerciseId") for ex in strength_exs if ex.get("exerciseId")}
        focus = _expand_muscles(list(getattr(request, "focusMuscles", []) or [])) or None
        session = build_strength_session(
            pool,
            request,
            goal,
            level,
            environment,
            duration,
            muscles,
            random.Random(),
            used,
            idx,
            focus,
            complementary=is_complementary_request(request),
        )
        if session and session.get("exercises"):
            rebuilt = session["exercises"]
            # Mantém os válidos que sobraram no começo, completando sem duplicar.
            existing_ids = {ex.get("exerciseId") for ex in strength_exs}
            merged = list(strength_exs)
            for ex in rebuilt:
                if ex.get("exerciseId") in existing_ids:
                    continue
                merged.append(ex)
                if len(merged) >= len(rebuilt):
                    break
            if len(merged) < 3:
                merged = rebuilt
            for i, ex in enumerate(merged):
                ex["exerciseOrder"] = i
            day["exercises"] = merged
            day["modality"] = "strength"
        elif strength_exs:
            day["exercises"] = strength_exs
        # se nada dá para reconstruir e nada sobrou, o caller remove o dia.


def fallback_log_line(reason: str, detail: str) -> str:
    """Linha única de log do fallback, com `detail` truncado em 300 chars.

    Prefixo fixo `[fallback]` para facilitar o grep no servidor; quebras de
    linha viram espaço para a linha continuar única e grep-ável.
    """
    safe = " ".join(str(detail or "").split())[:300]
    return f"[fallback] reason={reason} detail={safe}"


def _ai_timeout(name: str, default: float) -> float:
    try:
        return max(3.0, float(os.getenv(name, str(default))))
    except (TypeError, ValueError):
        return default


# Orçamento TOTAL da IA dentro do job (deadline, não por attempt). Default
# 600s (H3-40): nenhum caminho de rede espera a IA — o app consulta o job sob
# demanda. Retry único (json_parse, 5xx, 429, content vazio) só com orçamento
# restante; timeout na 1ª esgota o orçamento e cai direto no fallback local.
AI_TIMEOUT_DEFAULT_SECONDS = 600.0


async def run_generation_job(job_id: str, request: GenerateRequest) -> None:
    """Executa a geração em background e grava o estado terminal no SQLite.

    As transições são guardadas no banco: quem commitar primeiro vence
    (cancel concorrente × conclusão); se o job já está terminal, o resultado
    é descartado. IA falha/esgota o orçamento ⇒ fallback local ⇒ done;
    failed só quando nem o fallback é possível (pool vazio) ou erro inesperado.
    """
    try:
        if not db.mark_generation_job_running(job_id):
            return  # cancelado antes de a task começar
        try:
            response = await _generate_plan_response(request)
        except ValueError as exc:
            # Pool vazio do complementar (H3-16): nem o fallback é possível.
            db.finish_generation_job(job_id, "failed", error=str(exc))
            return
        except Exception as exc:
            db.finish_generation_job(job_id, "failed", error=f"{type(exc).__name__}: {exc}")
            return
        db.finish_generation_job(job_id, "done", result=response.model_dump())
    except asyncio.CancelledError:
        db.finish_generation_job(job_id, "cancelled")
        raise
    finally:
        _job_tasks.pop(job_id, None)


def _spawn_generation_job(job_id: str, request: GenerateRequest) -> None:
    """Costura dos testes: o TestClient não mantém o event loop entre
    requests, então os testes desligam o spawn e dirigem o runner na mão."""
    _job_tasks[job_id] = asyncio.create_task(run_generation_job(job_id, request))


def _cancel_job(job_id: str) -> None:
    # O banco é a fonte de verdade: marca cancelled primeiro; a task em voo
    # recebe cancel best-effort (o runner descarta o resultado se já cancelado).
    db.finish_generation_job(job_id, "cancelled")
    task = _job_tasks.pop(job_id, None)
    if task is not None:
        task.cancel()


def _job_status_response(row: Dict[str, Any]) -> GenerateJobStatus:
    result = None
    if row.get("result_json"):
        result = GenerateResponse.model_validate_json(row["result_json"])
    return GenerateJobStatus(
        jobId=row["id"],
        status=row["status"],
        createdAt=row["created_at"],
        result=result,
        error=row.get("error"),
    )


@app.post("/v1/generate-workout", response_model=GenerateJobAck, status_code=202)
async def generate_workout(request: GenerateRequest):
    """Submete a geração como job assíncrono (H3-40): 202 imediato com jobId.

    A IA roda em background (`run_generation_job`); o app consulta
    `GET /v1/generate-workout/{jobId}` sob demanda. Validação de entrada
    continua 422; catálogo não carregado continua 500. Last-write-wins:
    novo submit do mesmo device cancela o job em andamento anterior.
    """
    if not EXERCISE_CATALOG:
        raise HTTPException(500, "Exercise catalog not loaded")

    active = db.get_active_generation_job(request.deviceId)
    if active:
        _cancel_job(active["id"])

    job_id = str(uuid.uuid4())
    db.create_generation_job(job_id, request.deviceId, request.model_dump())
    _spawn_generation_job(job_id, request)
    return GenerateJobAck(jobId=job_id)


@app.get("/v1/generate-workout/{job_id}", response_model=GenerateJobStatus)
async def generation_job_status(job_id: str):
    row = db.get_generation_job(job_id)
    if row is None:
        raise HTTPException(404, "Job não encontrado")
    return _job_status_response(row)


@app.post("/v1/generate-workout/{job_id}/cancel", response_model=GenerateJobStatus)
async def cancel_generation_job(job_id: str):
    """Idempotente: job done/failed/cancelled não muda; devolve o estado atual."""
    row = db.get_generation_job(job_id)
    if row is None:
        raise HTTPException(404, "Job não encontrado")
    _cancel_job(job_id)
    return _job_status_response(db.get_generation_job(job_id))


async def _generate_plan_response(request: GenerateRequest) -> GenerateResponse:
    """Gera o plano: IA primeiro, fallback local quando a IA falha.

    Lógica extraída do endpoint síncrono na H3-40 — o resultado sempre informa
    a origem (`ai` ou `local`), o motivo do fallback quando local
    (`fallbackReason`, H3-21) e a data de revisão. O caller (runner) decide
    como reportar: ValueError (pool vazio) sobe e vira job failed.

    T7 experiment: quando EXPERIMENT_SHORTLIST=true (apenas plano principal),
    usa shortlist determinística com IDs exatos e schema rígido.
    """
    start_time = datetime.utcnow()
    workout_data = None
    source = "local"
    model_used = "local-fallback"

    modalities = getattr(request, "modalities", None) or []
    is_hybrid = bool(modalities)
    is_complementary = is_complementary_request(request)

    # T7: shortlist mode apenas para plano principal (sem modalidades, sem foco)
    use_shortlist = EXPERIMENT_SHORTLIST and not is_hybrid and not is_complementary

    if is_hybrid:
        user_prompt = build_hybrid_user_prompt(request)
        system_prompt = system_prompt_for_request(request)
    elif use_shortlist:
        user_prompt = build_shortlist_user_prompt(request)
        system_prompt = system_prompt_for_request(request)
    else:
        user_prompt = build_user_prompt(request)
        system_prompt = system_prompt_for_request(request)

    fallback_reason = ""
    fallback_detail = ""
    if not SENSENOVA_API_KEY:
        fallback_reason = "no_api_key"
        fallback_detail = "SENSENOVA_API_KEY ausente"
    else:
        # Log métricas do catálogo/shortlist no prompt.
        if use_shortlist:
            shortlist_metrics = shortlist_summary_metrics(request, EXERCISE_CATALOG, SHORTLIST_PER_MUSCLE)
            print(
                f"[shortlist] chars={shortlist_metrics['chars']} linhas={shortlist_metrics['linhas']} "
                f"tokens_aprox={shortlist_metrics['tokens_aprox']} exercises={shortlist_metrics['exercises']}"
            )
        else:
            catalog_metrics = catalog_summary_metrics(request, EXERCISE_CATALOG)
            print(f"[catalog] chars={catalog_metrics['chars']} linhas={catalog_metrics['linhas']} tokens_aprox={catalog_metrics['tokens_aprox']}")

        ai_budget = _ai_timeout("AI_TIMEOUT_TOTAL_SECONDS", AI_TIMEOUT_DEFAULT_SECONDS)
        ai_started = monotonic()
        attempts = 2
        for attempt in range(attempts):
            remaining = ai_budget - (monotonic() - ai_started)
            if remaining < 3.0:
                break
            attempt_timeout = remaining
            try:
                timeout = httpx.Timeout(
                    connect=5.0, read=attempt_timeout, write=10.0, pool=5.0
                )
                async with httpx.AsyncClient(timeout=timeout) as client:
                    response = await client.post(
                        f"{SENSENOVA_BASE_URL}/chat/completions",
                        headers={
                            "Authorization": f"Bearer {SENSENOVA_API_KEY}",
                            "Content-Type": "application/json",
                        },
                        json={
                            "model": MODEL_ID,
                            "messages": [
                                {"role": "system", "content": system_prompt},
                                {"role": "user", "content": user_prompt},
                            ],
                            "response_format": {"type": "json_object"},
                            "max_tokens": 8192,
                            "temperature": 0.3,
                            "reasoning": {"effort": "none"},  # JSON mode
                        },
                    )

                if response.status_code != 200:
                    if attempt == 0 and response.status_code in (429, 500, 502, 503, 504):
                        fallback_reason = "http_error"
                        fallback_detail = f"SenseNova API error: {response.status_code} - {response.text[:200]}"
                        continue
                    raise AiFailure(
                        "http_error",
                        f"SenseNova API error: {response.status_code} - {response.text[:200]}",
                    )

                content = response.json()["choices"][0]["message"]["content"]

                # H3-25: detecta resposta inútil (HTTP 200 mas content vazio/ausente ou
                # não parseável após extração) ⇒ 1 retry com parâmetros ajustados.
                if not content or not content.strip():
                    if attempt == 0:
                        fallback_reason = "json_parse"
                        fallback_detail = "resposta vazia (content ausente/vazio)"
                        continue
                    raise AiFailure("json_parse", "resposta vazia persistente após retry")

                raw_plan = parse_ai_plan(content, EXERCISE_CATALOG, request)

                workout = WorkoutPlanDTO(**raw_plan)
                # H3-36: conteúdo do plano no idioma do request (PT quando
                # pedido; EN canônico quando ausente).
                workout_data = enrich_plan(
                    workout.model_dump(), EXERCISE_CATALOG, language=request_language(request)
                )
                if is_hybrid:
                    workout_data = normalize_hybrid_plan(workout_data)
                if is_complementary:
                    # Garantias do complementar valem para a resposta da IA:
                    # remove o que saiu fora do foco/equipamento e reconstrói
                    # sessões insuficientes (H3-16).
                    workout_data = enforce_complementary_plan(workout_data, request, EXERCISE_CATALOG)
                source = "ai"
                model_used = MODEL_ID
                fallback_reason = ""
                fallback_detail = ""
                break
            except httpx.TimeoutException as exc:
                fallback_reason, fallback_detail = "timeout", f"timeout da IA: {exc}"
            except httpx.HTTPError as exc:
                fallback_reason, fallback_detail = "timeout", f"erro de rede na chamada da IA: {exc}"
            except AiFailure as exc:
                fallback_reason, fallback_detail = exc.reason, exc.detail
                # Retry em json_parse (inclui content vazio), 429, 5xx, timeout/rede.
                # Sem retry em 4xx de auth/parâmetro (http_error sem retry) ou schema_validation.
                retryable = exc.reason in {"json_parse", "timeout", "http_error"}
                if attempt == 0 and retryable and exc.reason != "http_error":
                    # Para http_error, só retry se status 429/5xx (já tratado acima no status_code)
                    continue
            except Exception as exc:
                fallback_reason, fallback_detail = "unknown", f"{type(exc).__name__}: {exc}"

    if fallback_reason:
        print(fallback_log_line(fallback_reason, fallback_detail))

    if workout_data is None:
        # ValueError (pool vazio do complementar, H3-16) sobe para o runner
        # marcar o job como failed com a mesma mensagem (H3-40).
        if is_hybrid:
            workout_data = build_hybrid_plan(request, EXERCISE_CATALOG)
            workout_data = normalize_hybrid_plan(workout_data)
            if is_complementary:
                workout_data = enforce_complementary_plan(
                    workout_data, request, EXERCISE_CATALOG
                )
        else:
            workout_data = build_local_plan(request, EXERCISE_CATALOG)

    next_review = review_date(request.reviewAfterWeeks)
    plan_id = 0
    try:
        plan_id = db.save_generation(
            device_id=request.deviceId,
            request_payload=request.model_dump(),
            plan_payload=workout_data,
            model_used=model_used,
            source=source,
            next_review_at=next_review,
            review_after_weeks=request.reviewAfterWeeks,
        )
    except Exception as exc:
        print(f"Failed to persist generated plan: {exc}")

    generation_time = int((datetime.utcnow() - start_time).total_seconds() * 1000)
    print(f"[generation] source={source} generationTimeMs={generation_time} model={model_used}")

    # Invariante do contrato (H3-21): source == "local" ⇒ fallbackReason
    # nunca vazio (no mínimo "unknown"); source == "ai" ⇒ sempre vazio.
    if source == "local" and not fallback_reason:
        fallback_reason = "unknown"

    return GenerateResponse(
        workout=WorkoutPlanDTO(**workout_data),
        generationTimeMs=generation_time,
        modelUsed=model_used,
        planId=plan_id,
        nextReviewAt=next_review,
        reviewAfterWeeks=request.reviewAfterWeeks,
        source=source,
        fallbackReason=fallback_reason,
    )


@app.post("/v1/review-plan", response_model=GenerateReviewResponse)
async def review_plan_endpoint(request: GenerateReviewRequest):
    """Propõe ajustes ao plano com base no histórico real.

    O app mostra as `changes` e só salva se o usuário confirmar. Todas as
    regras são determinísticas (fallback local); a IA não altera o plano sem
    confirmação.
    """
    result = review_plan(request.plan, request.history or {})
    return GenerateReviewResponse(
        workout=WorkoutPlanDTO(**result["workout"]),
        changes=result.get("changes", []),
        source=result.get("source", "local"),
    )


@app.get("/v1/catalog")
async def get_catalog():
    """Return full exercise catalog (for app sync)"""
    return {"exercises": EXERCISE_CATALOG}


@app.get("/v1/catalog/summary")
async def get_catalog_summary():
    """Return catalog summary for prompt building"""
    by_equipment: dict = {}
    by_muscle: dict = {}
    by_level: dict = {}
    for exercise in EXERCISE_CATALOG:
        equipment = exercise.get("equipment") or "body only"
        by_equipment[equipment] = by_equipment.get(equipment, 0) + 1
        level = exercise.get("level") or "unknown"
        by_level[level] = by_level.get(level, 0) + 1
        for muscle in exercise.get("primaryMuscles", []):
            by_muscle[muscle] = by_muscle.get(muscle, 0) + 1
    return {
        "total": len(EXERCISE_CATALOG),
        "byEquipment": by_equipment,
        "byMuscle": by_muscle,
        "byLevel": by_level,
    }


@app.post("/v1/sync", response_model=SyncResponse)
async def sync_data(request: SyncRequest):
    """Recebe o snapshot local do app e popula o banco do backend.

    Chamado apenas quando o usuário toca em "Sincronizar" no app.
    """
    payload = request.model_dump()
    result = db.sync_snapshot(payload)
    return SyncResponse(**result)


@app.get("/v1/sync/status")
async def sync_status(deviceId: str = "default"):
    return db.sync_status(deviceId)


@app.get("/v1/sync/plans")
async def synced_plans(deviceId: str = "default"):
    return {"deviceId": deviceId, "plans": db.list_synced_plans(deviceId)}


@app.get("/v1/sync/download")
async def sync_download(deviceId: str = "default"):
    """Baixa todos os dados sincronizados de um dispositivo (planos, dias, exercícios, sessões, séries).
    Útil para restaurar dados após reinstalar o app."""
    return db.get_sync_download_data(deviceId)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "3000")))