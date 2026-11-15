# H4-1 — Recordes pessoais (PR) e 1RM estimado

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-1` |
| MoSCoW | `M` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `2026-11-15` |
| Implementação | `concluída em 2026-11-15` |
| Depende de | H3-46 (mecanismo de háptico), H3-27 (validação de série) |

**Origem:** levantamento de mercado de 2026-11-14 (Horizon 4, Tier 1). Hevy e
Strong tratam PR e 1RM como básico; as séries já estão em `SetLogEntity`, então
o dado existe e só não é lido.

**Implementação (2026-11-15):** gate verde. Duas notas do que ficou diferente
do texto original: (1) `estimateOneRepMax` devolve a **própria carga** quando
`reps = 1` nas duas fórmulas — a série de 1 rep é o 1RM real, o que remove o
artefato de ~3,3% do Epley e alinha as fórmulas na origem (o critério 9 e o
teste de Brzycki foram ajustados de `reps = 36` para `reps = 1`); (2) no
`WorkoutScreenTest`, o clique do "Repetir última série" rola antes até
"Registrar série 2" — na janela do Robolectric o `performScrollToNode`
subestima o alcance do LazyColumn e um nó abaixo do clip não recebe o toque.

**Relacionado:** [H3-5](H3-5-progression.md) usa a melhor série por sessão
(`ProgressionEngine.bestSetPerSession`, privado) para sugerir carga. Esta spec
**não** altera a sugestão de progressão; ela expõe recordes e 1RM como leitura
derivada, sem aplicar nada ao plano.

## Objetivo

Ao registrar uma série, o usuário vê na hora se bateu um recorde pessoal, e no
detalhe do exercício vê o melhor de todos os tempos (carga × reps e 1RM
estimado), sem depender de conta, nuvem ou backend.

## Fora de escopo

- Nova tabela/coluna no Room: PR é **derivado** de `SetLogEntity` a cada
  leitura; não há cache persistido.
- Migration, backup novo ou alteração de contrato do `:protocol`/backend.
- Alterar a sugestão de progressão (H3-5) ou aplicar carga ao plano (H4-12).
- Gráficos de evolução (H4-2) e drill-down por exercício (H4-3).
- Corrida, natação e mobilidade: PR/1RM só existem para musculação com reps.
- Tipos de série (aquecimento/drop set/AMRAP/RIR) — H4-4. Aqui apenas se
  respeita `isWarmup = true` quando ele existir.
- Fórmulas além de Epley e Brzycki; periodização; prescrição médica.
- Compartilhar PR, notificação de PR ou PR no resumo pós-treino (H3-53).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/progression/PersonalRecords.kt` | **Novo.** Funções puras de 1RM, melhor série e detecção de PR. |
| `app/src/main/java/com/treinos/app/ui/Feedback.kt` | Novo `HapticEvent.PersonalRecord` e o mapeamento em `hapticFeedbackForEvent`. |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `personalRecords: StateFlow<Map<String, PersonalRecord>>` (derivado de `sessions`); `selectedExerciseRecord: StateFlow<PersonalRecord?>` (derivado de `_selectedExerciseId`). Nenhuma escrita nova. |
| `app/src/main/java/com/treinos/app/ui/components/SetEditDialog.kt` | Novo parâmetro `confirmHaptic: (Int?, Double?, Double?) -> HapticEvent = { HapticEvent.SetSaved }`; `submit()` dispara `confirmHaptic(...)` em vez de `SetSaved` fixo. |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Receber `personalRecords`; decidir `PersonalRecord` vs `SetSaved` no `onConfirm` e no "Repetir última série"; selo "Recorde pessoal!" no card do exercício; anúncio de acessibilidade. |
| `app/src/main/java/com/treinos/app/ui/components/ExerciseDetailSheet.kt` | Nova seção "Melhor de todos os tempos" a partir de `record: PersonalRecord?`. |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Coletar `personalRecords`/`selectedExerciseRecord` e passar a `WorkoutScreen` e `ExerciseDetailSheet`. |
| `app/src/main/res/values/strings.xml` e `app/src/main/res/values-en/strings.xml` | Novas chaves de selo, detalhe, "sem registro" e anúncio (paridade obrigatória). |
| `app/src/test/java/com/treinos/app/ui/progression/PersonalRecordsTest.kt` | **Novo.** Testes JVM das funções puras. |
| `app/src/test/java/com/treinos/app/ui/HapticFeedbackTest.kt` | Caso do novo `PersonalRecord`. |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` (Robolectric) | Selo e háptico ao bater PR. |
| `app/src/test/java/com/treinos/app/ui/ExerciseDetailSheetTest.kt` (Robolectric) | **Novo.** Seção de melhor de todos os tempos. |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

Sem mudança de banco, rede ou preferências. Todo o contrato é puro e local,
derivado de `List<SessionWithExercises>`:

```kotlin
enum class OneRepMaxFormula { EPLEY, BRZYCKI }

/** Série normalizada para cálculo, sem dependência do Room. */
data class RecordSet(
    val sessionId: Long,
    val sessionStartedAt: Long,
    val reps: Int?,
    val weightKg: Double?,
    val rpe: Double? = null,
    val isCompleted: Boolean = true,
    val isWarmup: Boolean = false,
)

data class PersonalRecord(
    val exerciseId: String,
    val weightKg: Double?,        // null em peso corporal
    val reps: Int?,
    val estimated1RmKg: Double?,  // null quando não há carga
    val achievedAtMillis: Long?,
)

/** Epley: w * (1 + reps/30). Brzycki: w * 36 / (37 - reps), só 1..36. */
fun estimateOneRepMax(
    weightKg: Double?,
    reps: Int?,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): Double?

/** Melhor série de todos os tempos do exercício; null sem série válida. */
fun personalRecord(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): PersonalRecord?

/** Traduz o histórico do exercício para o modelo puro. */
fun recordSetsFor(
    sessions: List<SessionWithExercises>,
    exerciseId: String,
): List<RecordSet>

/** O candidato supera [record]? `record == null` = primeiro registro válido. */
fun isPersonalRecord(
    candidate: RecordSet,
    record: PersonalRecord?,
    formula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
): Boolean
```

`PersonalRecord` é exibido, nunca gravado. A UI usa **Epley** por padrão;
Brzycki fica disponível e testado para uma eventual preferência futura.

## Regras e casos de borda

1. Só entram séries `isCompleted = true` e `isWarmup = false`; `reps` deve
   estar em `1..100` (`MIN_SET_REPS..MAX_SET_REPS` de `SetValidation.kt`).
2. `weightKg` só conta se for finito e `> 0`; `null`, `0` ou `NaN`/`Infinity`
   tratam a série como peso corporal.
3. Epley: `w * (1 + reps / 30.0)`. Brzycki: `w * 36.0 / (37 - reps)`, apenas
   para `reps in 1..36`; fora disso retorna `null` (não inventar). Série de
   1 rep devolve a própria carga nas duas fórmulas — ela é o 1RM real.
4. Comparação de PR com carga é por **1RM estimado estrito** (`>`), nunca por
   carga isolada: `40 kg × 10` (Epley 53,33) perde para `45 kg × 8` (Epley
   57,0) e ganha de `40 kg × 10` (empate não é PR).
5. Empate de 1RM desempata por maior `weightKg`, depois maior `reps`, depois
   `sessionStartedAt` mais recente — apenas para escolher o **melhor registro**,
   não para classificar empate como PR novo.
6. Peso corporal: o PR é a maior contagem de reps; empate não é PR novo.
7. Candidato com carga contra recorde de peso corporal → é PR; candidato de
   peso corporal contra recorde com carga → **não** é PR.
8. `record == null` (exercício nunca registrado) e candidato válido → é PR.
9. Candidato inválido (não concluído, warm-up, reps fora de `1..100`, sem reps)
   → `isPersonalRecord` é `false` e `estimateOneRepMax` é `null`.
10. Lista vazia de sessões → `personalRecord` é `null`; a UI mostra "Sem
    registro ainda".
11. Sessões `in_progress` contam: a série registrada é dado real e entra no
    histórico do exercício (o PR é do conjunto todo, não só de sessões
    concluídas).
12. Exercício ausente do histórico → `personalRecord` é `null`; nunca lançar
    exceção.
13. O selo de PR é **por sessão**: uma vez batido, permanece visível no card do
    exercício até o fim da sessão (estado local da tela), mesmo que outra série
    posterior não seja PR.
14. Um PR dispara exatamente **um** háptico: `PersonalRecord` substitui
    `SetSaved` naquela confirmação (não há vibração dupla). Sem PR, o
    comportamento da H3-46 continua (`SetSaved`/`RepeatLastSet`).
15. "Repetir última série" também avalia PR e usa `PersonalRecord` quando bate
    o recorde; caso contrário mantém `RepeatLastSet`.
16. Nada é calculado ou gravado ao apenas abrir telas; modo avião não altera o
    resultado.
17. O 1RM é **estimativa**: a UI rotula como "1RM estimado" e nunca como carga
    real.

## Critérios de aceite

1. **Dado** um exercício sem histórico, **quando** o usuário registra
   `40 kg × 10`, **então** o selo "Recorde pessoal!" aparece e o háptico
   `PersonalRecord` é disparado uma vez.
2. **Dado** recorde de `40 kg × 10` (Epley 53,33), **quando** registra
   `45 kg × 8` (Epley 57,0), **então** é PR.
3. **Dado** recorde de `40 kg × 10`, **quando** registra `40 kg × 10` de novo,
   **então** **não** é PR (empate não conta) e o háptico é o `SetSaved`.
4. **Dado** recorde de `40 kg × 10`, **quando** registra `30 kg × 20` (Epley
   50,0), **então** **não** é PR.
5. **Dado** exercício de peso corporal com recorde de 15 reps, **quando**
   registra 16 reps, **então** é PR; com 15 reps, não é.
6. **Dado** uma série marcada `isWarmup = true`, **quando** ela é registrada,
   **então** não vira PR nem altera o melhor de todos os tempos.
7. **Dado** o detalhe de um exercício com histórico, **quando** a folha abre,
   **então** mostra a melhor carga × reps e o 1RM estimado (Epley); sem
   histórico, mostra "Sem registro ainda".
8. **Dado** o candidato `45 kg × 8` contra um recorde de peso corporal,
   **então** é PR; o inverso (peso corporal contra recorde com carga) não é.
9. **Dado** Brzycki com `reps = 37`, **então** `estimateOneRepMax` retorna
   `null`; com `reps = 1`, retorna `w` (nas duas fórmulas).
10. **Dado** um aparelho sem suporte a háptico, **quando** bate PR, **então** o
    selo aparece e o app não trava.
11. **Dado** modo avião, **quando** registra séries e abre o detalhe, **então**
    PR e 1RM funcionam sem rede.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/progression/PersonalRecordsTest.kt` | `epley_reps_um_igual_carga`, `epley_40x10_estima_53_33`, `brzycki_reps_um_igual_carga`, `brzycki_reps_37_retorna_null`, `carga_invalida_retorna_null`, `primeiro_registro_valido_e_pr`, `empate_nao_e_pr`, `carga_maior_1rm_menor_nao_e_pr`, `peso_corporal_maior_reps_e_pr`, `peso_corporal_empate_nao_e_pr`, `carga_vence_recorde_de_peso_corporal`, `peso_corporal_nao_vence_recorde_com_carga`, `ignora_warmup_e_incompletas`, `reps_fora_da_faixa_ignorada`, `melhor_registro_desempata_por_carga_depois_reps`, `historico_vazio_retorna_null`, `exercicio_ausente_retorna_null`, `sessao_em_andamento_conta` |
| `app/src/test/java/com/treinos/app/ui/HapticFeedbackTest.kt` | `evento_personal_record_mapeia_confirmacao` |
| `app/src/test/java/com/treinos/app/ui/WorkoutScreenTest.kt` (Robolectric) | `bater_pr_mostra_selo`, `serie_sem_pr_nao_mostra_selo`, `repetir_ultima_serie_com_pr_mostra_selo` |
| `app/src/test/java/com/treinos/app/ui/ExerciseDetailSheetTest.kt` (Robolectric) | `detalhe_mostra_melhor_de_todos_os_tempos`, `detalhe_sem_registro_mostra_estado_vazio` |

## Verificação manual

1. Em aparelho físico, faça um treino novo e registre a primeira série de um
   exercício: confira o selo "Recorde pessoal!" e o toque tátil.
2. Repita a mesma carga × reps na série seguinte: o selo **não** deve
   reaparecer e o toque deve ser o de confirmação comum.
3. Suba a carga e reduza as reps mantendo o 1RM estimado maior: confira novo
   selo.
4. Faça um exercício de peso corporal e melhore as reps: confira o PR por reps.
5. Abra o detalhe do exercício (Histórico ou treino) e confira "Melhor de todos
   os tempos" com carga × reps e 1RM estimado.
6. Abra um exercício nunca treinado e confira "Sem registro ainda".
7. Ative o modo avião e repita os passos 1–6.
8. Caminho de erro: registre uma série com carga inválida no diálogo; ela é
   bloqueada pela H3-27 e **não** gera selo nem háptico de PR.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **1RM superestimado em reps altas:** Epley é impreciso acima de ~12 reps;
  por isso o texto é sempre "estimado" e o PR também aceita comparação por
  carga/reps. Brzycki fica limitado a 36 reps para não gerar valor negativo.
- **Falso PR por histórico desatualizado:** a tela decide o háptico com o mapa
  `personalRecords` antes da inserção; em dois registros quase simultâneos, o
  segundo pode comparar com o melhor anterior. Mitigação: o mapa é derivado do
  Room e o selo é por sessão; a inconsistência é visual e se corrige na próxima
  emissão.
- **Duplo háptico:** evitado ao mover a decisão para `confirmHaptic`; se o
  parâmetro não for passado, o default `SetSaved` preserva a H3-46.
- **Exercícios de peso corporal e com carga no mesmo ID:** a regra 7 define o
  vencedor; sem ela, o 1RM `null` poderia ser comparado como zero.
- **Rollback:** remover `PersonalRecords.kt`, o parâmetro `confirmHaptic`, o
  selo e a seção do detalhe. Nenhum dado do usuário é criado ou apagado, então
  reverter não exige migration nem restauração de backup.
