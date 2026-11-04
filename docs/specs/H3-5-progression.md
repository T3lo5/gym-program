# H3-5 — Progressão de carga e alerta de revisão

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-5` |
| MoSCoW | `W` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `pendente` |
| Depende de | H1-2 (séries), H2-4 (histórico), H1-5 (opcional, perfil) |

## Objetivo

Sugerir a próxima carga/série com base no que o usuário realmente levantou e
avisar quando o plano precisa ser revisto — sem mudar o plano sozinho e sem
depender de IA.

## Fora de escopo

- Alterar automaticamente peso, séries ou descanso do plano.
- Periodização automática completa (blocos, acumulação, choque).
- Prescrição médica, cálculo de 1RM ou garantia de ganho.
- Novo schema Room ou tabela de progressão.
- Treinar um modelo de IA com o histórico.
- Sincronizar sugestões com o backend.
- Gráficos de progressão (isso é H2-4).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/progression/ProgressionEngine.kt` | **Novo.** Cálculo puro de sugestão/ciclo |
| `app/src/main/java/com/treinos/app/ui/progression/ReviewSchedule.kt` | **Novo.** Adiar revisão (puro) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Sugestões por exercício + `postponeReview` |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `updatePlanReviewDate(planId, nextReviewAt)` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `postponeReview(planId, weeks)` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Passar sugestão e callback de revisão |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Pré-preencher carga sugerida + texto de apoio |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Card “Revisar plano” + “Adiar 2 semanas” |
| `app/src/test/java/com/treinos/app/ui/progression/ProgressionEngineTest.kt` | **Novo.** Regras de carga |
| `app/src/test/java/com/treinos/app/ui/progression/ReviewScheduleTest.kt` | **Novo.** Adiamento e ciclo |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

Tudo derivado de `List<SessionWithExercises>` (existente) e do plano ativo.
Nenhuma coluna/tabela nova.

```kotlin
enum class ProgressionAction { INCREASE, MAINTAIN, DECREASE, NONE }

data class ProgressionSet(
    val sessionId: Long,
    val sessionStartedAt: Long,
    val exerciseId: String,
    val isCompleted: Boolean,
    val isWarmup: Boolean,
    val reps: Int?,
    val weightKg: Double?,
    val rpe: Double?,
)

data class ProgressionContext(
    val exerciseId: String,
    val targetReps: String,      // "8-12", "10", "AMRAP"
    val equipment: String,       // "barbell", "dumbbell", "body only"...
    val primaryMuscles: String,  // "chest" ou "chest, triceps"
)

data class ProgressionSuggestion(
    val action: ProgressionAction,
    val suggestedWeightKg: Double?,
    val suggestedReps: Int?,
    val reason: String,
)

fun suggestProgression(
    context: ProgressionContext,
    history: List<ProgressionSet>,
    nowMillis: Long,
): ProgressionSuggestion

fun roundToIncrement(value: Double, increment: Double = 2.5): Double
fun completedSessionsSincePlan(
    sessions: List<SessionWithExercises>,
    planCreatedAtMillis: Long,
): Int
fun cycleWeek(planCreatedAtMillis: Long, nowMillis: Long, zoneId: ZoneId): Int
fun deloadSuggested(
    planCreatedAtMillis: Long,
    nowMillis: Long,
    completedSessionsSincePlan: Int,
    zoneId: ZoneId,
): Boolean
```

```kotlin
// ReviewSchedule.kt
fun postponeReviewDate(
    currentIsoDate: String?,
    today: LocalDate,
    weeks: Int = 2,
): LocalDate
```

### Regras de sugestão

1. Considerar apenas séries `isCompleted = true`, `isWarmup = false`,
   `reps != null` e dos últimos **42 dias**.
2. Agrupar por `sessionId`; usar a melhor série de cada sessão (maior
   `weightKg`; empate → maior `reps`).
3. Exigir **pelo menos 2 sessões** com o mesmo `exerciseId` e a mesma faixa
   `targetReps`. Menos que isso → `NONE`.
4. Interpretar `targetReps`:
   - `"8-12"` → mínimo 8, máximo 12;
   - `"10"` → mínimo = máximo = 10;
   - `"AMRAP"`, vazio ou texto livre → sem faixa → `NONE` (não inventar).
5. Incremento de carga:
   - `barbell` + músculo inferior (`quadriceps`, `hamstrings`, `glutes`,
     `calves`) → `5.0 kg`;
   - `barbell` + superior → `2.5 kg`;
   - `dumbbell`, `machine`, `cable`, `e-z curl bar` → `2.5 kg`;
   - `body only`, `bands`, `kettlebells` → sem peso; sugerir `+1 rep`.
6. `INCREASE` quando, nas **duas** últimas sessões:
   - `reps >= maxTarget`; e
   - `rpe == null` ou `rpe <= 8.5`.
   Peso sugerido = peso da última sessão + incremento, arredondado para
   múltiplo de `2.5`, mínimo `2.5`, máximo `500.0`.
7. `DECREASE` quando a última sessão tiver `rpe >= 9.5` **ou**
   `reps < minTarget`:
   - peso = `lastWeight * 0.95`, arredondado para múltiplo de `2.5`, mínimo
     `2.5`;
   - sem peso (bodyweight/bands) → `suggestedReps = max(minTarget - 1, 1)`.
8. `MAINTAIN` nos demais casos com histórico suficiente:
   - peso sugerido = peso da última sessão (pode ser `null`);
   - reps sugeridas = `maxTarget` quando bodyweight, senão `null`.
9. `rpe` ausente não impede `INCREASE` se as reps estiverem no topo nas duas
   sessões; caso contrário, `MAINTAIN`.
10. Peso `null` em exercício com carga → `MAINTAIN` (não há base para
    progressão).
11. `reason` sempre em português e curto:
    - `"Duas sessões no topo da faixa com RPE confortável."`
    - `"Última sessão perto do limite; mantenha a carga."`
    - `"RPE alto ou repetições abaixo da faixa; reduza a carga."`
    - `"Histórico insuficiente para sugerir progressão."`

### Regras do ciclo/revisão

- `cycleWeek` = semanas completas desde `plan.createdAt` (segunda-feira como
  início), mínimo `1`.
- `completedSessionsSincePlan` conta sessões `status == "completed"` com
  `startedAt >= plan.createdAt`.
- `deloadSuggested` = `cycleWeek >= 6` **e** `completedSessionsSincePlan >= 8`.
  É apenas um aviso; não altera o plano.
- Revisão: usar `WorkoutPlanEntity.nextReviewAt` e `reviewIsDue` já existentes.
- “Adiar 2 semanas” grava `nextReviewAt = postponeReviewDate(...)`; se a data
  atual já passou, conta a partir de hoje; nunca diminui a data existente.
- “Gerar novo treino” continua sendo o fluxo atual de geração.
- O card de revisão aparece na Home quando `reviewIsDue = true` e o plano está
  ativo.

### Integração com a UI

- No diálogo de série, se houver sugestão:
  - mostrar `"Sugestão: 42,5 kg — duas sessões no topo da faixa."`;
  - pré-preencher o campo de carga **somente** quando ele estiver vazio;
  - o usuário pode digitar qualquer valor; o valor digitado prevalece.
- Se a sugestão for `NONE`, não mostrar nada.
- O card de deload aparece na Home com o texto
  `"Semana 6 do ciclo — considere reduzir 1 série por exercício."` e um botão
  “Entendi” que apenas dispensa o card na sessão atual (estado em memória).
- Nenhuma sugestão é enviada ao backend.

## Regras e casos de borda

1. Uma única sessão → `NONE`.
2. Histórico com mais de 42 dias → ignorado.
3. Sessões `in_progress` → ignoradas.
4. Warmup → ignorado.
5. Faixa `"8 - 12"` com espaços → aceita.
6. Faixa `"12-8"` invertida → tratar como inválida → `NONE`.
7. Reps exatamente no topo (12) e RPE 8.5 → `INCREASE`.
8. RPE 8.6 → `MAINTAIN` (limite conservador).
9. RPE 9.5 → `DECREASE`.
10. Carga 2.5 kg e `DECREASE` → permanece 2.5 (não zera).
11. Carga sugerida acima de 500 kg → limitar a 500.
12. Exercício de peso corporal no topo → `suggestedWeightKg = null` e
    `suggestedReps = maxTarget + 1`.
13. `targetReps` desconhecido → `NONE`, sem exceção.
14. Plano novo (`createdAt` recente) → `cycleWeek = 1`, sem deload.
15. Relógio do aparelho adiantado/atrasado → datas passadas são tratadas como
    “hoje”; o cálculo não pode lançar exceção.
16. Adiar revisão com `nextReviewAt` nulo → hoje + 2 semanas.
17. Adiar revisão com data futura → data + 2 semanas.
18. Adiar revisão com data passada → hoje + 2 semanas.
19. Usuário ignora a sugestão e registra outra carga → o histórico real manda
    na próxima sugestão.
20. Nenhum dado é alterado ao apenas abrir a tela.

## Critérios de aceite

1. **Dado** duas sessões com 12 reps, 40 kg, RPE 8, alvo 8-12 (barbell,
   superior), **então** sugere 42.5 kg e `INCREASE`.
2. **Dado** o mesmo cenário para barbell + quadríceps, **então** sugere 45.0 kg.
3. **Dado** duas sessões com 12 reps e RPE 8 em exercício de peso corporal,
   **então** sugere `+1 rep` e não sugere peso.
4. **Dado** última sessão com RPE 10, **então** `DECREASE` com 5% a menos.
5. **Dado** uma única sessão, **então** `NONE` e o diálogo não mostra sugestão.
6. **Dado** alvo `AMRAP`, **então** `NONE`.
7. **Dado** RPE 8.6 com reps no topo, **então** `MAINTAIN`.
8. **Dado** que a revisão está atrasada, **quando** abre a Home, **então** o
   card “Revisar plano” aparece.
9. **Dado** “Adiar 2 semanas”, **então** `nextReviewAt` é atualizado, persiste
   após reiniciar e o card desaparece.
10. **Dado** ciclo na semana 6 com 8 sessões concluídas, **então** o aviso de
    deload aparece e o plano **não** muda sozinho.
11. **Dado** modo avião, **então** sugestão e revisão funcionam.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/progression/ProgressionEngineTest.kt` | `historico_insuficiente_retorna_none`, `ignora_warmup_e_incompletas`, `ignora_mais_de_42_dias`, `aumenta_superior_barbell_em_2_5`, `aumenta_inferior_barbell_em_5`, `aumenta_halter_em_2_5`, `peso_corporal_aumenta_uma_rep`, `rpe_alto_reduz_cinco_por_cento`, `reps_abaixo_da_faixa_reduz`, `rpe_ausente_no_topo_aumenta`, `rpe_86_mantem`, `limita_carga_minima`, `limita_carga_maxima`, `faixa_invertida_retorna_none`, `alvo_amrap_retorna_none`, `arredonda_para_multiplo_de_2_5` |
| `app/src/test/java/com/treinos/app/ui/progression/ReviewScheduleTest.kt` | `revisao_atrasada_aparece`, `adiar_duas_semanas_de_data_futura`, `adiar_de_data_passada_conta_de_hoje`, `adiar_sem_data_conta_de_hoje`, `nunca_diminui_data_existente`, `ciclo_comeca_na_semana_1`, `deload_na_semana_6_com_8_sessoes`, `sem_deload_antes_da_semana_6` |

## Verificação manual

1. Registre duas sessões do mesmo exercício com 12 reps, 40 kg e RPE 8.
2. Abra o próximo treino e o diálogo de série; confira a sugestão de 42,5 kg.
3. Digite 50 kg e registre; confirme que o valor digitado prevaleceu.
4. Registre uma sessão com RPE 10 e confirme a sugestão de redução.
5. Faça uma sessão de peso corporal e confirme a sugestão de +1 rep.
6. Gere um plano com revisão para hoje/ontem e confira o card na Home.
7. Toque em “Adiar 2 semanas”, reinicie o app e confirme a nova data.
8. Avance a data do aparelho para a semana 6 e confira o aviso de deload.
9. Ative o modo avião e repita os passos 2–7.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Sugestão lesiva:** incrementos conservadores (+2,5/+5 kg), RPE como
  critério e o usuário sempre pode editar. A UI deve deixar claro que é
  sugestão, não prescrição.
- **Histórico curto/sujo:** `NONE` é a resposta segura; nunca inventar faixa.
- **Mudança de plano:** histórico antigo continua valendo; o ciclo usa
  `createdAt` do plano ativo.
- **Fuso/data do aparelho:** cálculos usam `LocalDate` e tratam datas passadas
  sem exceção.
- **Rollback:** remover `ProgressionEngine`/`ReviewSchedule`, o card e o
  pré-preenchimento; `nextReviewAt` continua sendo o campo já existente.
  Nenhum dado do usuário é apagado.
