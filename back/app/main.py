from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional, Literal
import httpx
import os
import json
import socket
import threading
from datetime import datetime
from dotenv import load_dotenv
from zeroconf import ServiceInfo, Zeroconf

# Carrega back/.env antes de importar módulos que leem variáveis de ambiente
# (no Docker as variáveis já vêm injetadas).
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from . import database as db
from .plan_builder import (
    build_local_plan,
    enrich_plan,
    resolve_plan_ids,
    review_date,
)

# Config
SENSENOVA_API_KEY = os.getenv("SENSENOVA_API_KEY")
SENSENOVA_BASE_URL = os.getenv("SENSENOVA_BASE_URL", "https://token.sensenova.ai/v1")
MODEL_ID = os.getenv("MODEL_ID", "sensenova-6.8-flash-lite")
MDNS_ENABLED = os.getenv("MDNS_ENABLED", "true").strip().lower() not in {"0", "false", "no"}

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

# Build catalog summary for prompt
CATALOG_SUMMARY = ""
if EXERCISE_CATALOG:
    # Group by muscle group for concise prompt
    by_muscle = {}
    for ex in EXERCISE_CATALOG:
        for muscle in ex.get("primaryMuscles", []):
            if muscle not in by_muscle:
                by_muscle[muscle] = []
            by_muscle[muscle].append(f"{ex['id']} ({ex['name']}, {ex.get('equipment', 'body only')})")
    
    CATALOG_SUMMARY = "CATÁLOGO DE EXERCÍCIOS VÁLIDOS (use APENAS estes IDs):\n"
    for muscle, exercises in sorted(by_muscle.items()):
        CATALOG_SUMMARY += f"\n{muscle.upper()}:\n"
        for ex in exercises[:8]:  # limit per muscle
            CATALOG_SUMMARY += f"  - {ex}\n"
        if len(exercises) > 8:
            CATALOG_SUMMARY += f"  ... e mais {len(exercises) - 8} exercícios\n"

SYSTEM_PROMPT = f"""Você é um gerador de treinos de musculação baseado em evidências.

{CATALOG_SUMMARY}

REGRAS OBRIGATÓRIAS:
1. Use APENAS exercícios do catálogo acima (IDs exatos)
2. Retorne JSON válido conforme schema WorkoutPlanDTO
3. Não invente exercícios, não use IDs inexistentes
4. Respeite equipamentos disponíveis do usuário
5. Balanceie volume entre grupos musculares
6. Inclua aquecimento e alongamento sugeridos

FORMATO DE SAÍDA (JSON):
{{
  "name": "string",
  "description": "string",
  "goal": "hypertrophy|strength|endurance|power|fat_loss|general",
  "daysPerWeek": 3-6,
  "days": [
    {{
      "name": "string",
      "dayOrder": 0,
      "exercises": [
        {{
          "exerciseId": "string (ID EXATO do catálogo)",
          "exerciseOrder": 0,
          "sets": 3-5,
          "targetReps": "string (ex: '8-12', '10', 'AMRAP')",
          "targetRpe": 7.0-9.5,
          "restSeconds": 60-180,
          "notes": "string",
          "isSupersetWithPrevious": false
        }}
      ]
    }}
  ]
}}"""


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

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    db.init_db()
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
    # Shutdown
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
class GenerateRequest(BaseModel):
    deviceId: str = "default"
    goal: Literal["hypertrophy", "strength", "endurance", "power", "fat_loss", "general"] = "hypertrophy"
    daysPerWeek: int = Field(ge=2, le=6, default=4)
    availableEquipment: List[str] = Field(default_factory=list)  # e.g., ["dumbbell", "barbell", "body only"]
    targetMuscles: List[str] = Field(default_factory=list)       # e.g., ["chest", "back", "legs"]
    injuriesOrLimitations: str = ""
    currentLevel: Literal["beginner", "intermediate", "advanced"] = "intermediate"
    sessionDurationMinutes: int = Field(ge=30, le=120, default=60)
    preferredSplit: Optional[Literal["full_body", "upper_lower", "push_pull_legs", "bro_split"]] = None
    reviewAfterWeeks: int = Field(ge=1, le=12, default=4)


class ExerciseDTO(BaseModel):
    exerciseId: str
    exerciseOrder: int
    sets: int = Field(ge=1, le=6)
    targetReps: str
    targetRpe: Optional[float] = Field(default=None, ge=5.0, le=10.0)
    restSeconds: int = Field(ge=30, le=300, default=90)
    notes: str = ""
    isSupersetWithPrevious: bool = False
    exerciseName: str = ""
    primaryMuscles: List[str] = Field(default_factory=list)
    equipment: str = ""


class DayDTO(BaseModel):
    name: str
    dayOrder: int
    exercises: List[ExerciseDTO]
    isRestDay: bool = False


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


class SyncDay(BaseModel):
    localId: int
    name: str = ""
    dayOrder: int = 0
    notes: str = ""
    isRestDay: bool = False
    exercises: List[SyncPlanExercise] = Field(default_factory=list)


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


@app.post("/v1/generate-workout", response_model=GenerateResponse)
async def generate_workout(request: GenerateRequest):
    """Gera um plano sob demanda.

    Tenta a IA primeiro. Se a IA falhar, demorar, não tiver chave ou devolver
    IDs inválidos, monta um plano local válido com o catálogo. A resposta
    sempre informa a origem (`ai` ou `local`) e a data de revisão.
    """
    if not EXERCISE_CATALOG:
        raise HTTPException(500, "Exercise catalog not loaded")

    start_time = datetime.utcnow()
    workout_data = None
    source = "local"
    model_used = "local-fallback"

    equipment_str = ", ".join(request.availableEquipment) if request.availableEquipment else "nenhum (apenas peso corporal)"
    muscles_str = ", ".join(request.targetMuscles) if request.targetMuscles else "corpo todo"

    user_prompt = f"""Gere um treino de {request.goal} com as seguintes restrições:
- Dias por semana: {request.daysPerWeek}
- Equipamentos disponíveis: {equipment_str}
- Foco muscular: {muscles_str}
- Nível: {request.currentLevel}
- Duração por sessão: ~{request.sessionDurationMinutes} min
- Split preferido: {request.preferredSplit or 'livre'}
- Lesões/limitações: {request.injuriesOrLimitations or 'nenhuma'}

O treino deve ser progressivo, balanceado e usar apenas exercícios do catálogo fornecido.
Retorne APENAS o JSON válido."""

    if SENSENOVA_API_KEY:
        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                response = await client.post(
                    f"{SENSENOVA_BASE_URL}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {SENSENOVA_API_KEY}",
                        "Content-Type": "application/json",
                    },
                    json={
                        "model": MODEL_ID,
                        "messages": [
                            {"role": "system", "content": SYSTEM_PROMPT},
                            {"role": "user", "content": user_prompt},
                        ],
                        "response_format": {"type": "json_object"},
                        "max_tokens": 8192,
                        "temperature": 0.3,
                        "reasoning": {"effort": "none"},  # JSON mode
                    },
                )

            if response.status_code != 200:
                raise RuntimeError(
                    f"SenseNova API error: {response.status_code} - {response.text}"
                )

            content = response.json()["choices"][0]["message"]["content"]
            raw_plan = json.loads(content)
            raw_plan, unresolved = resolve_plan_ids(raw_plan, EXERCISE_CATALOG)
            if unresolved:
                raise ValueError(
                    "IDs retornados pela IA não existem no catálogo: "
                    + ", ".join(unresolved[:5])
                )

            workout = WorkoutPlanDTO(**raw_plan)
            workout_data = enrich_plan(workout.model_dump(), EXERCISE_CATALOG)
            source = "ai"
            model_used = MODEL_ID
        except Exception as exc:
            print(f"AI generation failed; using local fallback: {exc}")
    else:
        print("SENSENOVA_API_KEY not set; using local fallback")

    if workout_data is None:
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

    return GenerateResponse(
        workout=WorkoutPlanDTO(**workout_data),
        generationTimeMs=generation_time,
        modelUsed=model_used,
        planId=plan_id,
        nextReviewAt=next_review,
        reviewAfterWeeks=request.reviewAfterWeeks,
        source=source,
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


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "3000")))