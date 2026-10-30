from contextlib import asynccontextmanager
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
from zeroconf import ServiceInfo, Zeroconf

# Config
SENSENOVA_API_KEY = os.getenv("SENSENOVA_API_KEY")
SENSENOVA_BASE_URL = os.getenv("SENSENOVA_BASE_URL", "https://token.sensenova.ai/v1")
MODEL_ID = os.getenv("MODEL_ID", "sensenova-6.8-flash-lite")

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
    print("Treinos Backend started")
    print(f"Model: {MODEL_ID}")
    print(f"Catalog loaded: {len(EXERCISE_CATALOG)} exercises")
    mdns_service.start()
    yield
    # Shutdown
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
    goal: Literal["hypertrophy", "strength", "endurance", "power", "fat_loss", "general"] = "hypertrophy"
    daysPerWeek: int = Field(ge=2, le=6, default=4)
    availableEquipment: List[str] = Field(default_factory=list)  # e.g., ["dumbbell", "barbell", "body only"]
    targetMuscles: List[str] = Field(default_factory=list)       # e.g., ["chest", "back", "legs"]
    injuriesOrLimitations: str = ""
    currentLevel: Literal["beginner", "intermediate", "advanced"] = "intermediate"
    sessionDurationMinutes: int = Field(ge=30, le=120, default=60)
    preferredSplit: Optional[Literal["full_body", "upper_lower", "push_pull_legs", "bro_split"]] = None


class ExerciseDTO(BaseModel):
    exerciseId: str
    exerciseOrder: int
    sets: int = Field(ge=1, le=6)
    targetReps: str
    targetRpe: Optional[float] = Field(default=None, ge=5.0, le=10.0)
    restSeconds: int = Field(ge=30, le=300, default=90)
    notes: str = ""
    isSupersetWithPrevious: bool = False


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


# Routes
@app.get("/health")
async def health():
    return {
        "status": "healthy",
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "model": MODEL_ID,
        "catalogSize": len(EXERCISE_CATALOG)
    }


@app.post("/v1/generate-workout", response_model=GenerateResponse)
async def generate_workout(request: GenerateRequest):
    if not SENSENOVA_API_KEY:
        raise HTTPException(500, "API key not configured on server")
    
    if not EXERCISE_CATALOG:
        raise HTTPException(500, "Exercise catalog not loaded")
    
    # Build user context for prompt
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
    
    start_time = datetime.utcnow()
    
    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            response = await client.post(
                f"{SENSENOVA_BASE_URL}/chat/completions",
                headers={
                    "Authorization": f"Bearer {SENSENOVA_API_KEY}",
                    "Content-Type": "application/json"
                },
                json={
                    "model": MODEL_ID,
                    "messages": [
                        {"role": "system", "content": SYSTEM_PROMPT},
                        {"role": "user", "content": user_prompt}
                    ],
                    "response_format": {"type": "json_object"},
                    "max_tokens": 8192,
                    "temperature": 0.3,
                    "reasoning": {"effort": "none"}  # Disable reasoning for JSON mode
                }
            )
            
            if response.status_code != 200:
                error_detail = response.text
                raise HTTPException(502, f"SenseNova API error: {response.status_code} - {error_detail}")
            
            data = response.json()
            content = data["choices"][0]["message"]["content"]
            
            # Parse and validate
            workout_data = json.loads(content)
            workout = WorkoutPlanDTO(**workout_data)
            
            # Validate all exercise IDs exist in catalog
            catalog_ids = {ex["id"] for ex in EXERCISE_CATALOG}
            for day in workout.days:
                for ex in day.exercises:
                    if ex.exerciseId not in catalog_ids:
                        raise HTTPException(500, f"Invalid exercise ID returned by AI: {ex.exerciseId}")
            
            generation_time = int((datetime.utcnow() - start_time).total_seconds() * 1000)
            
            return GenerateResponse(
                workout=workout,
                generationTimeMs=generation_time,
                modelUsed=MODEL_ID
            )
            
    except httpx.TimeoutException:
        raise HTTPException(504, "Timeout calling SenseNova API")
    except json.JSONDecodeError as e:
        raise HTTPException(500, f"AI returned invalid JSON: {str(e)}")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(500, f"Generation failed: {str(e)}")


@app.get("/v1/catalog")
async def get_catalog():
    """Return full exercise catalog (for app sync)"""
    return {"exercises": EXERCISE_CATALOG}


@app.get("/v1/catalog/summary")
async def get_catalog_summary():
    """Return catalog summary for prompt building"""
    return {
        "total": len(EXERCISE_CATALOG),
        "byEquipment": {},
        "byMuscle": {},
        "byLevel": {}
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=3000)