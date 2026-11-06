# H2-6 — Plano híbrido e revisão por IA

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H2-6` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-05` |
| Implementação | `concluída` |
| Depende de | H1-6 (campos de modalidade e sync v2), H2-5 (registro avulso); H2-7 é opcional |
| Correção de escopo | **H3-11**: cada `day` passa a ser uma única sessão/modalidade; várias modalidades podem dividir o mesmo `weekday`, mas como sessões separadas; a musculação ganha 4–8 exercícios e a IA é normalizada antes de salvar |

> **Correção de escopo (H3-11).** Esta spec permitia duas sessões no mesmo
> `weekday`, mas a implementação juntou os exercícios das duas no mesmo `day`,
> transformando-as em uma sequência única. Coexistir no mesmo dia continua
> **permitido**; o que a H3-11 exige é que cada modalidade seja um **`day`
> separado**, com início e aba próprios. Nenhum `day` mistura musculação,
> corrida e mobilidade. O restante do contrato (campos, revisão e
> compatibilidade v1/v2) continua valendo.

## Objetivo

Montar uma semana com modalidades diferentes — musculação, corrida e mobilidade
planejadas, natação apenas registrada — e permitir que a IA revise a semana com
base no que foi realmente feito, sem tirar o app do modo local-first.

## Fora de escopo

- Gerar plano de natação. Natação é registro avulso (H2-5).
- GPS, pace ao vivo, frequência cardíaca em tempo real ou integração com Strava.
- Blocos estruturados de natação (`8 x 100 m`) ou splits por raia.
- Replanejamento automático sem o usuário confirmar.
- Prescrição médica, reabilitação ou diagnóstico.
- Wear OS e protocolo do relógio.
- Múltiplos planos ativos simultâneos. Continua existindo **um** plano ativo.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | Campos de H1-6 (`modality`, `weekday`, `metricType`, alvos) |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | Salvar plano híbrido; `observeWeek()`; iniciar sessão por `dayId` (já existe) |
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `GeneratePlanRequest` v2 com `modalities`; `ReviewPlanRequest/Response` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Geração híbrida, revisão, aceitar/descartar mudanças |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Seleção de modalidades e sessões por modalidade |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Visão “Hoje” com planejado + registros do dia |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | Filtro por modalidade e métricas (km, pace, metros) |
| `back/app/main.py` | `GenerateRequest` v2; `POST /v1/review-plan` |
| `back/app/plan_builder.py` | Fallback híbrido; regras de revisão determinísticas |
| `back/app/run_templates.py` | **Novo.** Templates de corrida por nível/objetivo |
| `contracts/generate_response.json` | Fixture v2 (campos novos, compatível com v1) |
| `back/tests/test_hybrid_plan.py` | **Novo.** Geração e revisão |
| `app/src/test/java/com/treinos/app/ui/WeeklyScheduleTest.kt` | **Novo.** Ordenação e duplicidade de dia |
| `app/src/test/java/com/treinos/app/ui/RunMetricsTest.kt` | **Novo.** Métricas de corrida: pace, duração do bloco, sem benchmark usa RPE |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

### Request de geração v2

```json
{
  "deviceId": "uuid",
  "modalities": [
    {
      "type": "strength",
      "sessionsPerWeek": 3,
      "goal": "hypertrophy",
      "level": "intermediate",
      "availableEquipment": ["barbell", "dumbbell", "machine"],
      "targetMuscles": []
    },
    {
      "type": "run",
      "sessionsPerWeek": 2,
      "goal": "endurance",
      "level": "beginner",
      "recent5kSeconds": 1800
    },
    {
      "type": "mobility",
      "sessionsPerWeek": 2,
      "focus": "back"
    }
  ],
  "sessionDurationMinutes": 60,
  "reviewAfterWeeks": 4
}
```

Regras:

- `modalities` tem pelo menos uma entrada; `strength` é opcional.
- `sessionsPerWeek` por modalidade; a soma pode passar de 7 porque o mesmo
  `weekday` pode ter duas sessões (ex.: academia de manhã, natação à noite).
- `run.recent5kSeconds` é opcional; sem ele, a prescrição usa RPE, não pace.
- Natação **não** entra em `modalities`; entra como registro H2-5.
- O request v1 continua aceito; sem `modalities`, o backend gera só musculação.

### Resposta

```json
{
  "workout": {
    "name": "Semana híbrida",
    "goal": "general",
    "daysPerWeek": 7,
    "days": [
      {
        "name": "Corrida intervalada",
        "dayOrder": 0,
        "weekday": 2,
        "modality": "run",
        "isRestDay": false,
        "exercises": [
          {
            "exerciseId": "run_interval",
            "exerciseName": "5 x 1 km",
            "metricType": "distance_duration",
            "sets": 5,
            "targetReps": "",
            "targetDistanceMeters": 1000,
            "targetPaceSecondsPerKm": 330,
            "targetRpe": 8.0,
            "restSeconds": 120,
            "notes": "Aquecimento 10 min + 5 x 1 km forte + 2 min trote"
          }
        ]
      }
    ]
  },
  "source": "ai",
  "reviewAfterWeeks": 4
}
```

- `weekday`: 1 = segunda … 7 = domingo; `null` = rotativo.
- `daysPerWeek` passa a significar “sessões planejadas na semana”, não “dias
  distintos”.
- Para corrida, `sets` representa repetições do bloco; `targetReps` fica vazio.
- Para mobilidade, `metricType = "duration"` e `targetDurationSeconds`.
- Para musculação, o formato atual não muda (`reps_weight`).

### Revisão

```http
POST /v1/review-plan
```

```json
{
  "deviceId": "uuid",
  "plan": { "days": [] },
  "history": {
    "weeks": 4,
    "adherence": 0.75,
    "sessions": [
      {
        "modality": "run",
        "completed": true,
        "durationSeconds": 1800,
        "distanceMeters": 5000,
        "rpe": 7.0,
        "startedAt": 1760000000000
      }
    ]
  }
}
```

Resposta: `workout` novo + `changes` (lista de strings legíveis) + `source`.
O app mostra as mudanças e só salva se o usuário confirmar.

Regras determinísticas do fallback:

- Adesão `< 60%` → reduzir uma sessão da modalidade com mais faltas ou cortar
  20% do volume.
- RPE médio `> 8.5` por duas semanas → deload (menos séries ou menos intensidade).
- Pace de corrida melhorou por duas semanas → progredir o bloco principal.
- Registro de dor/limitação nas notas → não aumentar volume; sugerir mobilidade.
- Natação registrada entra como contexto de volume/fadiga, mas não gera bloco.

## Regras e casos de borda

1. Soma de `sessionsPerWeek` maior que 7 → permitido; o mesmo `weekday` pode ter
   mais de uma sessão.
2. Duas sessões no mesmo `weekday` → ordenar por modalidade e por `dayOrder`.
3. Plano sem `strength` → válido (só corrida/mobilidade).
4. Plano só com natação registrada → não é plano; é histórico de atividades.
5. `recent5kSeconds` ausente → prescrição por RPE, sem pace fixo.
6. `recent5kSeconds` inválido (0, negativo, > 3 h) → ignora e usa RPE.
7. IA falha/JSON inválido → fallback local híbrido; a resposta informa `source`.
8. Revisão sem histórico → devolve o plano atual com `changes = []`.
9. Revisão com adesão baixa → não aumentar volume; reduzir.
10. Usuário descarta a revisão → plano atual permanece intacto.
11. Plano híbrido salvo → continua existindo exatamente um plano ativo.
12. Sessão de corrida executada → `SetLog` usa `durationSeconds`/`distanceMeters`;
    não exigir reps/carga.
13. Natação registrada não entra no plano e não aparece como “dia de treino”.
14. O app antigo continua lendo o plano v1; os campos novos têm defaults.

## Critérios de aceite

1. **Dado** que o usuário escolhe 3 musculação + 2 corrida + 2 mobilidade,
   **quando** gera, **então** recebe 7 sessões distribuídas em `weekday`, com
   modalidade em cada dia.
2. **Dado** que duas sessões caem no mesmo dia, **então** ambas aparecem na visão
   da semana e podem ser iniciadas separadamente.
3. **Dado** um dia de corrida, **então** a execução mostra distância/pace/duração,
   não séries com reps.
4. **Dado** que o backend está offline, **quando** gera, **então** o fallback
   local monta a semana híbrida com templates.
5. **Dado** um histórico de 4 semanas com adesão 40%, **quando** revisa,
   **então** a proposta reduz volume e o app mostra as mudanças antes de salvar.
6. **Dado** que o usuário descarta, **então** o plano atual não muda.
7. **Dado** que ele registrou natação, **então** o histórico mostra a atividade e
   a revisão a considera como contexto, sem criar plano de natação.
8. **Dado** um plano v1 antigo, **quando** o app novo abre, **então** continua
   funcionando com os defaults.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_hybrid_plan.py` | `test_plano_hibrido_distribui_modalidades`, `test_permite_duas_sessoes_no_mesmo_dia`, `test_corrida_iniciante_usa_caminhada`, `test_corrida_com_benchmark_calcula_paces`, `test_fallback_sem_ia`, `test_revisao_reduz_volume_com_adesao_baixa`, `test_revisao_deload_com_rpe_alto`, `test_revisao_sem_historico_nao_muda`, `test_natacao_nao_vira_plano` |
| `back/tests/test_contract_v2.py` | `test_fixture_v2_valido`, `test_v1_continua_valido` |
| `app/src/test/java/com/treinos/app/ui/WeeklyScheduleTest.kt` | `ordena_por_weekday_e_day_order`, `permite_duas_sessoes_no_mesmo_dia`, `conta_sessoes_e_nao_dias` |
| `app/src/test/java/com/treinos/app/ui/RunMetricsTest.kt` | `formata_pace_por_km`, `calcula_duracao_do_bloco`, `sem_benchmark_usa_rpe` |

## Verificação manual

1. Gere uma semana com 3 musculação + 2 corrida + 2 mobilidade.
2. Confira a visão “Hoje” e a visão “Semana”.
3. Inicie um dia de corrida e registre duração/distância.
4. Registre uma natação avulsa e confirme que ela não virou dia de plano.
5. Sincronize, faça 2 semanas de sessões (ou simule histórico) e peça revisão.
6. Confira as mudanças propostas e descarte; depois aceite e confirme o novo
   plano.
7. Desligue o backend e repita a geração para validar o fallback.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Contrato v1/v2:** manter o endpoint v1 e adicionar campos opcionais; o teste
  de fixture v1 protege a compatibilidade.
- **Plano grande demais:** 10+ sessões/semana podem desmotivar; a UI deve avisar
  e a revisão deve reduzir adesão baixa.
- **Corrida sem benchmark:** pace inventado é pior que RPE; usar RPE quando não
  houver `recent5kSeconds`.
- **IA “adaptando” demais:** toda mudança é proposta, nunca aplicada sem
  confirmação.
- **Natação virar plano:** o backend não aceita `swim` em `modalities`; o app
  trata natação só como registro.
- **Rollback:** o plano v1 continua válido; desativar a revisão e a geração
  híbrida não apaga planos existentes.
