# Treinos App — App Pessoal de Treinos (Local-first + IA)

App Android + Wear OS para musculação, 100% offline, com geração de treinos via IA sob demanda (backend no PC/Codespaces/Oracle Free Tier).

**Stack:** Kotlin + Jetpack Compose + Room + Hilt + Retrofit + Compose for Wear OS + FastAPI (Python) + SenseNova API

---

## 🎯 Características

| Feature | Status |
|---|---|
| 📱 App Android (celular) | ✅ Estrutura completa |
| ⌚ App Wear OS (Galaxy Watch FE) | ✅ Estrutura base |
| 💾 Banco local (Room) — 876 exercícios | ✅ Pronto |
| 🏋️ CRUD de treinos offline | ✅ ViewModels + Screens |
| 🤖 Geração de treino via IA | ✅ Backend + integração |
| 🔗 Descoberta LAN (mDNS) | 📋 Sprint 3 |
| ⏱ Timer de descanso no relógio | 📋 Sprint 4 |
| ☁️ Backend grátis (Codespaces/Oracle) | ✅ Docker Compose |

---

## 📁 Estrutura do Projeto

```
TreinosApp/
├── settings.gradle.kts          # Multi-module: app, wear, shared
├── build.gradle.kts             # Versões centralizadas
├── gradle.properties
├── docker-compose.yml           # Backend + rede
├── scripts/import-exercises.js  # free-exercise-db → ExerciseCatalog.kt
├── shared/                      # Módulo comum (Room, models, DAOs, repos)
├── app/                         # App Android (celular)
│   ├── ui/workoutlist/          # Lista de treinos
│   ├── ui/workouteditor/        # Editor de treinos
│   ├── ui/exercisepicker/       # Seletor de exercícios
│   ├── ui/workoutexecution/     # Execução do treino (timer, sets)
│   └── ui/navigation/           # NavHost + rotas
├── wear/                        # App Wear OS
└── back/                        # Backend FastAPI (Python)
    ├── main.py                  # /health, /v1/generate-workout, /v1/catalog
    └── exercise_catalog.json    # 876 exercícios
```

---

## 🚀 Como Rodar

### Pré-requisitos
- Android Studio Koala+ (JDK 17, Android SDK 34)
- Docker + Docker Compose
- Chave da API SenseNova (gratuita em https://platform.sensenova.ai)

### 1. Backend (no seu PC)
```bash
cd back
cp .env.example .env
# Edite .env e coloque sua SENSENOVA_API_KEY

docker compose up -d --build
# Teste:
curl http://localhost:3000/health
# {"status":"healthy","catalogSize":876,"model":"sensenova-6.8-flash-lite"}
```

### 2. App Android
```bash
# No Android Studio:
# 1. Open project → selecione a pasta raiz
# 2. Wait for Gradle sync
# 3. Run 'app' no emulador (API 34) ou device físico
# 4. Run 'wear' no emulador Wear OS ou Galaxy Watch FE real
```

### 3. Conexão Celular ↔ PC (LAN)
- Celular e PC na **mesma Wi-Fi**
- No emulador: usa `10.0.2.2:3000` automaticamente
- No device físico: app detecta via mDNS ou você digita `192.168.x.x:3000`
- Android 14+: concede permissão "Acesso à rede local" quando solicitado

### 4. Wear OS (Galaxy Watch FE)
```bash
# No relógio: Settings → Developer options → ADB debugging ON
# No PC:
adb pair <watch-ip>:<port>
adb connect <watch-ip>:5555
# No Android Studio: Run 'wear' module
```

---

## 🏗️ Arquitetura

### Módulo `shared` (Kotlin Multiplatform-ready)
- **Room Database:** `Exercise`, `WorkoutPlan`, `WorkoutDay`, `WorkoutSession`, `SetLog`
- **DAOs:** Flows reativos + suspend functions
- **Repositories:** `ExerciseRepository`, `WorkoutRepository`, `BackendRepository`
- **Models:** Serializáveis com kotlinx-serialization

### App Android (`app`)
- **Hilt DI:** DatabaseModule, NetworkModule
- **Navigation:** Compose Navigation + Bottom Bar (4 tabs)
- **Telas:**
  - `WorkoutListScreen` — Lista, busca, duplicar, excluir
  - `WorkoutEditorScreen` — Editor arrastável (dias + exercícios)
  - `ExercisePickerScreen` — Filtros por músculo/equipamento/nível
  - `WorkoutExecutionScreen` — Timer sessão, timer descanso, sets, RPE, navegação
  - `HistoryScreen`, `SettingsScreen` — Placeholders

### Wear OS (`wear`)
- **Compose for Wear OS** + Material3
- **Data Layer API** para sync celular↔relógio
- Tela de execução: exercício atual, timer descanso, próximas séries

### Backend (`back`)
- **FastAPI** + Uvicorn em Docker
- **Endpoints:**
  - `GET /health` — Health check + catálogo size
  - `GET /v1/catalog` — Lista completa (para sync inicial)
  - `POST /v1/generate-workout` — Gera treino JSON validado
- **Prompt Engineering:** Catálogo resumido injetado no system prompt
- **Validação:** IDs de exercício conferidos contra catálogo local

---

## 📊 Banco de Exercícios (free-exercise-db)

- **Fonte:** https://github.com/yuhonas/free-exercise-domínio público (Unlicense)
- **876 exercícios** com: id, nome, força, nível, mecânica, equipamento, músculos primários/secundários, instruções, categoria, imagens
- **Embutido no APK** via `ExerciseCatalog.kt` (auto-gerado por `scripts/import-exercises.js`)
- **Imagens:** Servidas via `https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/<id>/0.jpg`

---

## 🤖 Geração de Treino via IA

**Fluxo:**
1. Usuário clica "Gerar com IA" no editor
2. App envia contexto: objetivo, dias/semana, equipamentos, músculos-alvo, nível, limitações
3. Backend monta prompt com catálogo resumido → chama SenseNova (`sensenova-6.8-flash-lite`)
4. IA retorna `WorkoutPlanDTO` (JSON schema estrito)
5. Backend valida IDs contra catálogo → retorna pro app
6. App abre editor pré-preenchido para revisão → salva no Room

**Configuração SenseNova:**
```env
SENSENOVA_API_KEY=sk-xxx
SENSENOVA_BASE_URL=https://token.sensenova.ai/v1
MODEL_ID=sensenova-6.8-flash-lite
```
> Dica: Use `"reasoning": {"effort": "none"}` no request para JSON mode (doc oficial recomenda desligar reasoning para JSON estruturado).

---

## 🆓 Hospedagem Grátis do Backend

| Opção | Custo | Ideal para |
|---|---|---|
| **PC local (Docker)** | R$ 0 | Desenvolvimento, uso na LAN |
| **GitHub Codespaces** | 120h/mês grátis | "Sobe quando quer, fecha depois" |
| **Oracle Always Free** | 2 OCPU + 12GB RAM pra sempre | Backend 24/7 na nuvem |

---

## 📝 Sprints

| Sprint | Entregável |
|---|---|
| **S0** (feito) | Fundação: Room, catálogo 876 exercícios, Docker backend, navegação base |
| **S1** (em andamento) | CRUD treinos offline completo (editor, execução, histórico) |
| **S2** | Integração "Gerar com IA" + validação |
| **S3** | Descoberta LAN automática (mDNS) + fallback manual |
| **S4** | Wear OS MVP: timer descanso, próximo exercício, sync Data Layer |
| **S5** | Polimento: animações, A11y, testes, CI |
| **S6** (opcional) | Backend na nuvem grátis (Codespaces/Oracle) |

---

## 🛠️ Desenvolvimento

```bash
# Gerar catálogo atualizado (se free-exercise-db mudar)
node scripts/import-exercises.js back/app/exercise_catalog.json shared/src/main/java/com/treinos/shared/data/

# Build backend
cd back && docker build -t treinos-backend .

# Logs backend
docker logs -f treinos-backend

# ADB reverse (dev no emulador)
adb reverse tcp:3000 tcp:3000
```

---

## 📄 Licença

- **Código do app:** MIT (seu uso pessoal)
- **Catálogo de exercícios:** Unlicense (domínio público) — https://github.com/yuhonas/free-exercise-db
- **SenseNova API:** Termos da plataforma (https://platform.sensenova.ai)

---

## 🤝 Contribuição

Projeto pessoal — PRs bem-vindos para melhorias de UX, correções de bugs, ou novos exercícios PT-BR no catálogo.

---

**Desenvolvido para uso pessoal — custo R$ 0/mês 💪**# CI trigger
