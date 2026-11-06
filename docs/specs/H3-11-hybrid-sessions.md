# H3-11 — Geração híbrida por sessão e abas por modalidade

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-11` |
| MoSCoW | `M` |
| Spec aprovada em | `aprovada` (2026-11-06) |
| Implementação | `concluída` (2026-11-06) |
| Depende de | H2-6 (plano híbrido), H2-7 (mobilidade), H1-6 (modalidade no dia), H1-2 (execução por séries) |

## Objetivo

Gerar cada modalidade como uma **sessão completa e independente** — musculação
de academia com 4 a 8 exercícios, corrida com bloco próprio e mobilidade com
rotina própria — e mostrar cada uma em sua aba, para o usuário fazer uma coisa
por vez sem que uma única sessão misture musculação, corrida e mobilidade.
**Duas ou três modalidades podem cair no mesmo dia**; nesse caso, cada uma
continua sendo uma sessão separada, com seu próprio início e sua própria aba —
nunca uma sequência única de “treino híbrido”.

## Contexto verificado (por que hoje sai errado)

O problema não é só de prompt: o fallback local e o caminho da IA têm defeitos
estruturais que se somam.

- `back/app/plan_builder.py` `build_hybrid_plan` cria **um slot por sessão** e,
  para musculação, chama `_pick_strength_exercise` **uma única vez**. Resultado:
  um dia de musculação com 1 exercício.
- O mesmo `build_hybrid_plan` agrupa os slots por `weekday` e junta todos os
  exercícios em um único `day` (`day_exercises`). **Coexistir no mesmo dia é
  válido**, mas o app transforma isso em uma **sequência única**, com um só botão
  de iniciar; corrida e musculação deveriam ser sessões independentes, cada uma
  na sua aba.
- `_pick_strength_exercise` usa `_pick_exercise`, cujo fallback escolhe
  **qualquer** exercício do pool, ignorando músculo, equipamento e categoria. O
  catálogo tem 123 itens de `stretching` e 14 de `cardio`; um treino de
  musculação pode receber alongamento.
- `_build_pool` filtra equipamento, mas não filtra `category`; e o `goal`,
  `level`, `targetMuscles` e `equipment` da modalidade `strength` são ignorados
  em favor dos campos globais do request.
- `back/app/main.py` `build_hybrid_user_prompt` não envia equipamentos, split,
  foco muscular, perfil nem duração por modalidade. O `SYSTEM_PROMPT` nem sequer
  mostra `weekday`, `modality`, `metricType` ou alvos no schema de saída.
- `resolve_plan_ids` tenta resolver **todos** os IDs no catálogo. Os IDs
  sintéticos `run_*` e `mobility_*` não existem no catálogo, então qualquer
  resposta híbrida da IA é tratada como inválida e cai no fallback local ruim.
- `ExerciseDTO.sets` tem `le=6`; o template avançado de corrida usa `sets=10`
  (`10 x 1 km`) e estoura a validação do Pydantic.
- `app/.../data/WorkoutRepository.kt` `startSession` não copia
  `WorkoutDayEntity.modality` para a sessão. Um dia de corrida ou mobilidade é
  gravado como `strength`.
- `app/.../ui/screens/HomeScreen.kt` lista todos os dias em uma pilha única e
  `WorkoutScreen` sempre pede reps e carga, inclusive para corrida.
- `profile_adjustments` mexe em `restSeconds`/`sets` de **todos** os exercícios,
  inclusive blocos de corrida e mobilidade.
- O nome do dia é inferido por `exerciseId.split("_")[0]`; como IDs de
  musculação não têm `_`, um dia puro de musculação vira "Treino híbrido".

## Fora de escopo

- Gerar natação. Natação continua registro avulso (H2-5).
- GPS, pace ao vivo, frequência cardíaca em tempo real ou integração com Strava.
- Plano de corrida com periodização de prova (ex.: plano de maratona em 16
  semanas). O v1 usa templates e progressão simples.
- Edição manual do plano gerado dentro do app.
- Migrar/reescrever planos híbridos antigos já salvos. Eles continuam abrindo;
  para obter o comportamento novo, o usuário gera novamente.
- Múltiplos planos ativos simultâneos.
- Wear OS: o relógio continua mostrando a sessão em andamento; não ganha UI
  específica de corrida/mobilidade nesta spec.
- Vídeos/animações de exercício e novos itens de catálogo.
- Prescrição médica, reabilitação ou diagnóstico.

## Arquivos e símbolos que devem mudar

### Backend

| Arquivo | Mudança |
|---|---|
| `back/app/main.py` | `ModalityRequest` tipado; `GenerateRequest.environment`; `SYSTEM_PROMPT_HYBRID`; `build_hybrid_user_prompt`; `ExerciseDTO.sets` até 20; chamar `normalize_hybrid_plan`; validar IDs só em musculação |
| `back/app/plan_builder.py` | `strength_exercise_count`, `strength_split_for`, `build_strength_session`, `assign_weekdays`, `normalize_hybrid_plan`; reescrever `build_hybrid_plan`; `_build_pool` por categoria; `profile_adjustments` só em `reps_weight`; `resolve_plan_ids` ignora corrida/mobilidade; `build_local_plan` reutiliza o builder de musculação |
| `back/app/run_templates.py` | `template_for(level, recent5k_seconds, session_index, duration_minutes)` com rotação e escala por duração |
| `back/tests/test_hybrid_plan.py` | Atualizar e ampliar os testes de geração |
| `back/tests/test_run_templates.py` | **Novo.** Rotação, escala e benchmark |
| `back/tests/test_profile_adjustments.py` | Provar que corrida/mobilidade não recebem ajuste de IMC |

### App Android

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `ModalityDto.sessionDurationMinutes`; `GeneratePlanRequest.environment` |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Seletor de ambiente; controles por modalidade (sessões, nível, objetivo, foco, duração); remover dias/duração globais do modo híbrido |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Enviar ambiente/duração por modalidade; rotear `startWorkout` por modalidade; `finishRunWorkout`; `finishPlannedMobility` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `startSession` copia `day.modality` e `day.name`; `finishRunSession`; `logPlannedMobility` |
| `app/src/main/java/com/treinos/app/ui/WeeklySchedule.kt` | `ModalityTab`, `groupSessionsByModality`, `defaultModalityTab`, `planVolumeLabel`, `sessionsOnWeekday` |
| `app/src/main/java/com/treinos/app/ui/SessionMode.kt` | **Novo.** `SessionKind` e `fromModality` |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Abas por modalidade e card de sessão específico |
| `app/src/main/java/com/treinos/app/ui/screens/RunWorkoutScreen.kt` | **Novo.** Execução de corrida planejada |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | Modo “rotina planejada”, com rotina pré-selecionada e vínculo com `planId`/`dayId` |
| `app/src/main/java/com/treinos/app/ui/MobilityRoutine.kt` | `routineForFocus(focus, minutes)` |
| `app/src/main/java/com/treinos/app/ui/history/HistoryMetrics.kt` | Séries/reps/volume contam só musculação; corrida/mobilidade contam duração/distância |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Rotear corrida para `RunWorkoutScreen` e mobilidade planejada para `MobilityScreen` |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Ficar restrito a musculação; nunca abrir diálogo de reps para corrida |

Não edite `shared/` nem `com.treinos.shared/` (legado). **Não há mudança de
schema Room nesta spec**: `WorkoutDayEntity.modality`, `WorkoutSessionEntity.modality`
e os campos `durationSeconds`/`distanceMeters` de `SetLogEntity` já existem.

## Modelo de dados / contrato

### Request híbrido v2 (aditivo, compatível com v1)

```json
{
  "deviceId": "uuid",
  "environment": "gym",
  "goal": "hypertrophy",
  "daysPerWeek": 3,
  "currentLevel": "intermediate",
  "sessionDurationMinutes": 60,
  "modalities": [
    {
      "type": "strength",
      "sessionsPerWeek": 3,
      "goal": "hypertrophy",
      "level": "intermediate",
      "availableEquipment": ["barbell", "dumbbell", "machine", "cable", "body only"],
      "targetMuscles": ["chest", "back", "legs"],
      "sessionDurationMinutes": 60
    },
    {
      "type": "run",
      "sessionsPerWeek": 2,
      "goal": "endurance",
      "level": "beginner",
      "recent5kSeconds": null,
      "sessionDurationMinutes": 40
    },
    {
      "type": "mobility",
      "sessionsPerWeek": 2,
      "focus": "back",
      "sessionDurationMinutes": 10
    }
  ],
  "reviewAfterWeeks": 4
}
```

Regras do contrato:

- `environment`: `gym` | `home` | `bodyweight` | ausente. Ausente = inferir pelo
  `availableEquipment`; se também vazio, `bodyweight`.
- `ModalityRequest` é tipado no Pydantic, com
  `type: Literal["strength", "run", "mobility"]`, `sessionsPerWeek` 1..7,
  `sessionDurationMinutes` 5..120 e `recent5kSeconds` opcional. `swim` continua
  proibido.
- `sessionDurationMinutes` da modalidade vence o global. Mobilidade é limitada
  a 5..15 min no plano.
- `daysPerWeek` global é ignorado quando `modalities` não está vazio. Continua
  aceito para compatibilidade e para geração só de musculação sem `modalities`.
- Request v1/v2 continua válido. Campos novos são opcionais e têm default.

### Resposta

```json
{
  "workout": {
    "name": "Semana híbrida — 3x academia, 2x corrida, 2x mobilidade",
    "description": "Sessões independentes por modalidade.",
    "goal": "general",
    "daysPerWeek": 7,
    "days": [
      {
        "name": "Academia A — Superiores",
        "dayOrder": 0,
        "weekday": 1,
        "modality": "strength",
        "isRestDay": false,
        "exercises": [
          {
            "exerciseId": "Barbell_Bench_Press",
            "exerciseOrder": 0,
            "sets": 3,
            "targetReps": "8-12",
            "targetRpe": 8.0,
            "restSeconds": 90,
            "notes": "",
            "isSupersetWithPrevious": false,
            "metricType": "reps_weight"
          }
        ]
      },
      {
        "name": "Mobilidade — costas",
        "dayOrder": 1,
        "weekday": 1,
        "modality": "mobility",
        "isRestDay": false,
        "exercises": [
          {
            "exerciseId": "mobility_back",
            "exerciseOrder": 0,
            "sets": 1,
            "targetReps": "",
            "targetRpe": 5.0,
            "restSeconds": 0,
            "notes": "Rotina de mobilidade para costas (10 min).",
            "metricType": "duration",
            "targetDurationSeconds": 600
          }
        ]
      },
      {
        "name": "Corrida — intervalado",
        "dayOrder": 2,
        "weekday": 4,
        "modality": "run",
        "isRestDay": false,
        "exercises": [
          {
            "exerciseId": "run_interval",
            "exerciseOrder": 0,
            "sets": 5,
            "targetReps": "",
            "targetRpe": 8.0,
            "restSeconds": 120,
            "notes": "Aquecimento 10 min + 5 x 1 km forte + 2 min trote.",
            "metricType": "distance_duration",
            "targetDistanceMeters": 1000,
            "targetPaceSecondsPerKm": 342
          }
        ]
      }
    ]
  },
  "source": "local",
  "reviewAfterWeeks": 4
}
```

> O exemplo acima abrevia a lista de musculação e omite sessões repetidas para
> mostrar o formato; o invariante 2 exige 4–8 exercícios por sessão de academia.
> Ele mostra também **duas sessões no mesmo `weekday`** (academia e mobilidade
> na segunda): são entradas separadas, não uma sequência única.

Invariantes que o backend deve garantir **depois da IA e no fallback**:

1. **Um `day` = uma sessão = uma modalidade.** Todos os exercícios do dia têm o
   mesmo `metricType` e pertencem à mesma família de `modality`.
2. **Musculação:** 4 a 8 exercícios, todos `metricType = "reps_weight"`,
   `category` de força (`strength`, `powerlifting`, `olympic weightlifting`), sem
   `stretching`/`cardio`. O primeiro exercício é composto.
3. **Corrida:** exatamente 1 bloco, `metricType = "distance_duration"`, com
   `targetDistanceMeters` e/ou `targetRpe`; `sets` é o número de repetições do
   bloco (até 20).
4. **Mobilidade:** exatamente 1 bloco, `metricType = "duration"`,
   `targetDurationSeconds` entre 300 e 900.
5. `daysPerWeek = len(days)` = total de **sessões** da semana, não de dias
   distintos.
6. `weekday` 1..7. **Várias sessões no mesmo `weekday` são permitidas**; cada uma
   é um objeto `day` separado, com `dayOrder` diferente. Nunca um `day` com
   exercícios de modalidades diferentes.
7. `dayOrder` é único e crescente; a ordenação final é `weekday` e depois
   `dayOrder`.
8. No máximo 3 sessões por `weekday`, no máximo uma de cada modalidade. Como cada
   modalidade tem no máximo 7 sessões, a capacidade de 21 nunca é excedida e não
   é preciso cortar sessões.

### Tipos puros no app

```kotlin
enum class SessionKind {
    STRENGTH, RUN, MOBILITY;

    companion object {
        fun fromModality(modality: String?): SessionKind
    }
}

enum class ModalityTab { STRENGTH, RUN, MOBILITY }

fun groupSessionsByModality(
    days: List<WorkoutDayWithExercises>,
): Map<ModalityTab, List<WorkoutDayWithExercises>>

fun defaultModalityTab(
    days: List<WorkoutDayWithExercises>,
    todayWeekday: Int,
): ModalityTab?

fun planVolumeLabel(plan: WorkoutPlanWithDetails): String

fun sessionsOnWeekday(
    days: List<WorkoutDayWithExercises>,
    weekday: Int,
): List<WorkoutDayWithExercises>

data class RunLogInput(
    val durationSeconds: Long,
    val distanceMeters: Double?,
    val rpe: Double?,
)

data class RunLogValidation(
    val valid: Boolean,
    val error: String?,
    val paceSecondsPerKm: Int?,
)

fun validateRunLog(input: RunLogInput): RunLogValidation
```

`MobilityRoutineTemplates.routineForFocus(focus: String?, minutes: Int)`
mapeia `back` → `back_10`, `hips` → `hips_10`, `post_run` → `post_run_8`,
qualquer outro → `full_15`, ajustando pela duração mais próxima.

### Execução sem migration

- `WorkoutRepository.startSession` copia `modality = day.modality` e
  `title = day.name` para `WorkoutSessionEntity`.
- Corrida grava um `SetLogEntity` com `durationSeconds`, `distanceMeters` e
  `rpe` (campos já existentes); a sessão recebe `totalDurationSeconds`,
  `totalDistanceMeters` e `sessionRpe`.
- Mobilidade planejada não cria sessão ao abrir o timer; ao finalizar, insere uma
  sessão `completed` com `modality = "mobility"`, `source = "plan"`, `planId`,
  `dayId`, `title` e `totalDurationSeconds`, sem séries.
- Nenhuma coluna nova em `session_exercises`; o modo de execução vem de
  `session.modality`.
- `resumeWorkout` usa o mesmo roteamento por modalidade para reabrir a tela certa
  depois de “Retomar treino”.

### Apresentação na Home

- A Home organiza o plano **por modalidade**, não por dia: abas “Academia”,
  “Corrida” e “Mobilidade”, cada uma listando apenas as sessões daquela
  modalidade.
- Um `weekday` com academia e corrida aparece **uma vez em cada aba**: um card
  em “Academia” e outro em “Corrida”, com inícios independentes. Nunca existe um
  card “Treino híbrido” que liste as duas coisas em sequência.
- Cada card mostra apenas o resumo da própria modalidade (exercícios e volume na
  academia; distância/duração/RPE na corrida; foco/duração na mobilidade).
- O rótulo do plano conta **sessões**, não dias distintos: 3 academia + 2 corrida
  + 2 mobilidade = “7 sessões/semana”.
- Sessões do mesmo dia são ordenadas por `dayOrder` dentro de cada aba e
  identificadas pelo nome da sessão (ex.: “Academia A — Superiores”,
  “Mobilidade — costas”), para o usuário saber qual está iniciando.
- O botão “Iniciar” de um card abre somente aquela sessão; ao voltar, a Home
  continua na mesma aba.

## Regras e casos de borda

1. 3 musculação + 2 corrida + 2 mobilidade → 7 sessões separadas; cada **sessão**
   com uma única modalidade; cada sessão de musculação com 4 a 8 exercícios.
2. Musculação 3x/semana usa dias não consecutivos (ex.: seg/qua/sex). Nunca duas
   sessões de musculação no mesmo dia.
3. Modalidades diferentes **podem** cair no mesmo `weekday` (ex.: academia de
   manhã e corrida à noite). Nesse caso são sempre dois objetos `day` separados,
   com `dayOrder` diferentes, cada um com seu próprio início, sua própria tela e
   sua própria aba. O que não pode existir é um único `day` com exercícios de
   modalidades diferentes.
4. Qualquer combinação de modalidades pode compartilhar o `weekday`: no máximo 3
   sessões por dia, no máximo uma de cada modalidade (ex.: musculação +
   corrida + mobilidade no mesmo dia).
5. Duração da musculação define o volume: 30 min → 4 exercícios; 45 → 5;
   60 → 6; 90 → 8. Valores intermediários são arredondados para a faixa mais
   próxima, sempre entre 4 e 8. Se a estimativa de tempo passar de duração + 10
   min, remove um acessório.
6. `goal` da modalidade `strength` define séries, reps, RPE e descanso; não usa
   mais o `goal` global quando a modalidade traz o seu.
7. `environment = "gym"`: o pool de musculação exclui `stretching` e `cardio`;
   pelo menos 3 dos 4–8 exercícios usam `barbell`, `dumbbell`, `machine` ou
   `cable`; o primeiro exercício é composto e usa equipamento selecionado. Se o
   usuário escolheu só `body only`, a regra de equipamento não se aplica.
8. `environment = "home"`: usa apenas os equipamentos escolhidos + `body only`;
   não inventa máquina/polia.
9. Nível `beginner` exclui exercícios `advanced`; ainda garante o composto
   principal.
10. Não repete o mesmo `exerciseId` dentro da sessão. Entre dias da mesma
    semana, evita repetir o mesmo composto quando há alternativa.
11. Corrida rotaciona templates por sessão (leve, intervalado, ritmo, longão) em
    vez de repetir o primeiro template.
12. Duração da corrida escala o volume: intervalos ajustam `sets`
    (`clamp(round(base * minutos / 60), 3, 20)`); corrida contínua ajusta a
    distância proporcionalmente, entre 1 km e 21 km. Sem benchmark, usa RPE.
13. `recent5kSeconds` inválido (0, negativo, > 3 h) é ignorado; a prescrição usa
    RPE, não pace inventado.
14. Mobilidade usa o `focus` e a duração da modalidade; foco desconhecido cai em
    `full_15`; a rotina real vem de `MobilityRoutineTemplates`.
15. Ajustes de IMC (`profile_adjustments`) só alteram exercícios
    `reps_weight`. Corrida e mobilidade mantêm sets, descanso e distância.
16. Falha da IA, JSON inválido, ID de musculação inexistente ou plano que não
    satisfaça as invariantes → fallback local; `source` informa a origem.
17. IA que devolve um dia misturando musculação e corrida é **normalizada**:
    o dia é dividido em dois, preservando `weekday`, e a musculação é completada
    pelo builder local. Se ainda ficar inválido, cai no fallback.
18. `resolve_plan_ids` valida apenas exercícios de musculação. IDs sintéticos
    `run_*` e `mobility_*` são aceitos como estão.
19. Plano híbrido antigo (dias misturados) continua abrindo; o app não reescreve
    nem divide automaticamente. A UI mostra a sessão como está e o usuário pode
    gerar um plano novo.
20. Duas ou três sessões no mesmo `weekday` aparecem em cards separados, na aba de
    cada modalidade. Iniciar uma nunca inclui os exercícios da outra nem a
    apresenta como continuação da anterior.
21. Iniciar corrida grava `modality = "run"`; a execução pede duração,
    distância e RPE — nunca reps/carga. O histórico mostra distância e pace, e
    **não** soma a corrida nas séries/reps/volume de musculação.
22. Iniciar mobilidade planejada abre a rotina com timer; finalizar grava sessão
    `mobility` sem séries. Sair antes de finalizar não grava nada. O histórico
    mostra a rotina como atividade, sem “0 séries”.
23. Sessão de musculação continua exatamente como hoje (séries, carga, RPE,
    descanso e progressão).
24. Revisão de plano não mistura nem divide sessões; continua ajustando apenas
    séries de musculação.
25. Plano com 7 sessões mostra “7 sessões/semana” na Home, não “7x por semana”
    como se fossem 7 dias distintos.
26. Abas só aparecem para modalidades presentes no plano ativo. A aba padrão é a
    da próxima sessão (hoje ou o próximo `weekday`); se hoje tem mais de uma
    sessão, a ordem de preferência é musculação → corrida → mobilidade. Sem
    sessões, mostra o estado vazio atual.
27. App/backend antigos: request v1/v2 e plano v1/v2 continuam válidos; campos
    novos são opcionais.

## Critérios de aceite

1. **Dado** que o usuário escolhe 3 musculação + 2 corrida + 2 mobilidade com
   ambiente academia, **quando** gera, **então** recebe 7 sessões, cada uma com
   uma única modalidade, e cada sessão de musculação tem 4 a 8 exercícios.
2. **Dado** o mesmo plano, **quando** abre a Home, **então** vê abas
   “Academia”, “Corrida” e “Mobilidade”, e nenhuma sessão de corrida aparece
   dentro do card de musculação — mesmo quando as duas caem no mesmo `weekday`.
3. **Dado** que o ambiente é academia, **quando** gera musculação, **então** pelo
   menos 3 exercícios usam barra, halter, máquina ou polia, e nenhum é
   alongamento/cardio.
4. **Dado** que há 3 sessões de musculação, **quando** gera, **então** elas não
   ficam em dias consecutivos.
5. **Dado** que o usuário inicia uma sessão de corrida, **então** a tela pede
   duração, distância e RPE, não reps/carga; ao finalizar, o histórico mostra
   distância e pace e não soma a corrida nas séries de musculação.
6. **Dado** que o usuário inicia mobilidade planejada, **então** vê a rotina com
   timer e, ao finalizar, o histórico mostra “Mobilidade • 10 min” sem séries.
7. **Dado** que duas ou três sessões caem no mesmo `weekday`, **então** cada uma
   aparece no card da sua aba, pode ser iniciada separadamente e não é
   apresentada como continuação de outra.
8. **Dado** que a IA devolve um dia com musculação e corrida juntas, **quando** o
   backend normaliza, **então** o resultado tem dois dias separados no mesmo
   `weekday` e a musculação é completada.
9. **Dado** que a IA devolve um bloco `run_interval`, **então** o backend não o
   trata como ID inválido e não cai no fallback por causa disso.
10. **Dado** que o usuário escolhe só musculação, **então** recebe uma sessão
    completa de academia, não um único exercício.
11. **Dado** que o usuário escolhe só corrida ou só mobilidade, **então** o plano
    é válido e a Home mostra apenas a aba correspondente.
12. **Dado** um plano híbrido antigo com dias misturados, **quando** abre o app,
    **então** o plano continua acessível e o usuário é orientado a gerar de novo
    para obter sessões separadas.
13. **Dado** que o backend está sem chave de IA, **quando** gera, **então** o
    fallback local produz o mesmo recorte por sessão e modalidade.
14. **Dado** que o usuário revisa o plano, **então** a revisão não junta nem
    separa sessões; só ajusta séries de musculação quando aplicável.

## Testes a criar

### Backend

| Arquivo | Testes |
|---|---|
| `back/tests/test_hybrid_plan.py` | `test_cada_dia_tem_uma_unica_modalidade`, `test_musculacao_tem_4_a_8_exercicios`, `test_musculacao_academia_usa_equipamentos_de_academia`, `test_musculacao_nao_usa_alongamento_ou_cardio`, `test_musculacao_3x_nao_usa_dias_consecutivos`, `test_modalidades_diferentes_podem_compartilhar_weekday`, `test_sessoes_no_mesmo_dia_nao_sao_mescladas`, `test_tres_modalidades_no_mesmo_dia_sao_tres_dias`, `test_duracao_por_modalidade_limita_volume`, `test_corrida_varia_templates_por_sessao`, `test_corrida_escala_distancia_pela_duracao`, `test_mobilidade_respeita_foco_e_duracao`, `test_normaliza_ia_com_modalidades_misturadas`, `test_resolve_ids_ignora_corrida_e_mobilidade`, `test_sets_de_corrida_aceitam_intervalos_ate_20`, `test_fallback_sem_ia` |
| `back/tests/test_run_templates.py` | `test_rotaciona_templates_por_sessao`, `test_escala_intervalos_pela_duracao`, `test_benchmark_invalido_usa_rpe`, `test_corrida_iniciante_usa_caminhada` |
| `back/tests/test_profile_adjustments.py` | `test_imc_nao_altera_corrida_nem_mobilidade`, `test_imc_continua_ajustando_musculacao` |
| `back/tests/test_contract_v2.py` | `test_dia_hibrido_tem_uma_modalidade`, `test_request_com_environment_e_duracao_por_modalidade` |

### App Android

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/WeeklyScheduleTest.kt` | `agrupa_dias_por_modalidade`, `mesmo_weekday_com_duas_modalidades_nao_vira_uma_sessao`, `sessoes_de_modalidades_diferentes_ficam_em_grupos_diferentes`, `aba_padrao_e_a_proxima_sessao`, `rotulo_do_plano_hibrido_conta_sessoes`, `sessoes_do_dia_ordenadas_por_day_order` |
| `app/src/test/java/com/treinos/app/ui/SessionModeTest.kt` | `modality_run_usa_modo_corrida`, `modality_mobility_usa_modo_mobilidade`, `modality_strength_usa_modo_series`, `modality_desconhecida_assume_strength` |
| `app/src/test/java/com/treinos/app/ui/RunMetricsTest.kt` | `pace_calculado_na_finalizacao`, `distancia_zero_nao_gera_pace`, `duracao_invalida_bloqueia_registro` |
| `app/src/test/java/com/treinos/app/ui/MobilityRoutineTest.kt` | `foco_back_escolhe_back_10`, `duracao_menor_escolhe_rotina_mais_proxima`, `foco_desconhecido_usa_full_body` |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `start_session_copia_modalidade_do_dia`, `corrida_grava_setlog_com_duracao_e_distancia`, `mobilidade_planejada_nao_cria_series` |
| `app/src/test/java/com/treinos/app/ui/history/HistoryMetricsTest.kt` | `corrida_nao_soma_series_de_musculacao`, `mobilidade_nao_soma_series_de_musculacao` |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | `mostra_abas_apenas_das_modalidades_do_plano`, `card_de_corrida_mostra_distancia_e_duracao`, `mesmo_dia_com_academia_e_corrida_aparece_nas_duas_abas` |

## Verificação manual

1. Gere uma semana com 3 musculação + 2 corrida + 2 mobilidade em ambiente
   “Academia”.
2. Confira que existem 7 sessões, que cada card de musculação tem 4–8 exercícios
   de academia e que nenhum card mistura corrida com musculação.
3. Abra a aba “Academia” e inicie uma sessão; confirme séries, carga e descanso.
4. Abra a aba “Corrida”, inicie uma sessão, registre duração/distância/RPE e
   confirme o pace no histórico.
5. Abra a aba “Mobilidade”, inicie a rotina planejada, finalize antes do fim e
   confirme a sessão sem séries no histórico.
6. Force duas ou três sessões no mesmo dia e confirme que cada uma aparece na aba
   da sua modalidade, com início e execução independentes.
7. Gere só musculação; repita só corrida e só mobilidade.
8. Desligue a chave da IA (ou use backend sem `SENSENOVA_API_KEY`) e repita a
   geração; o recorte por sessão deve ser o mesmo do fallback.
9. Abra um plano híbrido antigo e confirme que ele ainda carrega.
10. Faça uma revisão com histórico simulado e confirme que nenhuma sessão foi
    fundida ou dividida.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **IA continuar misturando modalidades:** a resposta é normalizada no backend e,
  se não passar nas invariantes, cai no fallback local. O teste
  `test_normaliza_ia_com_modalidades_misturadas` protege o caminho.
- **`resolve_plan_ids` voltar a rejeitar corrida/mobilidade:** sem esse ajuste, o
  caminho híbrido com IA nunca funciona. O teste
  `test_resolve_ids_ignora_corrida_e_mobilidade` quebra se alguém voltar ao
  comportamento antigo.
- **Volume de musculação estourar a duração:** a tabela de 4–8 exercícios e o
  corte de acessório mantêm a sessão dentro do orçamento; o teste de duração
  falha se o volume voltar a ser fixo.
- **Corrida/mobilidade voltarem a receber ajuste de IMC:** o teste
  `test_imc_nao_altera_corrida_nem_mobilidade` protege a separação.
- **Plano antigo quebrar:** nenhuma coluna nova é obrigatória; `modality` e os
  campos de duração/distância já existem. Planos antigos continuam legíveis.
- **Abas esconderem sessões:** `groupSessionsByModality` deve preservar todos os
  dias; o teste de agrupamento conta sessões, não dias distintos.
- **Rollback:** reverter `build_hybrid_plan`, o prompt e as abas. Os planos e
  sessões já gravados continuam válidos; a execução de corrida/mobilidade usa
  campos que já existiam. Nada é apagado.
