# H4-5 — Calculadora de anilhas e rampa de aquecimento

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H4-5` |
| MoSCoW | `S` |
| Versão alvo | `1.5.0` |
| Spec aprovada em | `pendente` |
| Implementação | `pendente` |

## Objetivo

Dizer, na hora de registrar a série, quais anilhas colocar por lado para chegar
à carga-alvo e quais séries de aquecimento fazer antes da série de trabalho —
sem o usuário fazer conta de cabeça entre as séries.

## Fora de escopo

- Inventário de anilhas configurável pelo usuário e peso de barra por
  equipamento (E-Z bar, barra olímpica feminina): nesta entrega o inventário é
  o padrão de academia (barra 20 kg + 20/15/10/5/2,5/1,25 kg por lado). A
  função já recebe o inventário como parâmetro para um item futuro ligar a
  configuração.
- Anilhas para halteres, máquinas, cabos ou peso corporal: o atalho só aparece
  para equipamento `barbell`.
- Sugerir aquecimento no plano/geração ou registrá-lo automaticamente; a rampa
  é só informativa no diálogo.
- Mudar o motor de progressão (`ProgressionEngine`) ou o descanso.
- Backend e backup: nada muda.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/PlateMath.kt` (novo) | `PlateInventory`, `PlateResult`, `DEFAULT_PLATE_INVENTORY`, `plateBreakdown(targetKg, inventory): PlateResult?`, `isPlateLoadedEquipment(equipment): Boolean` — funções puras |
| `app/src/main/java/com/treinos/app/ui/WarmupRamp.kt` (novo) | `WarmupStep(percent, weightKg, reps)`, `warmupRamp(workingWeightKg, inventory): List<WarmupStep>` — função pura que reusa `plateBreakdown` |
| `app/src/main/java/com/treinos/app/ui/components/SetEditDialog.kt` | parâmetro `plateInventory: PlateInventory? = null`; botão/expansão "Anilhas e aquecimento" com a quebra por lado e a rampa da carga digitada |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | ao abrir o diálogo (:344 e :386), passa `DEFAULT_PLATE_INVENTORY` quando `media?.equipment` é `barbell` (`isPlateLoadedEquipment`), senão `null` |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | `workout_plate_shortcut`, `workout_plate_per_side`, `workout_plate_total`, `workout_plate_remainder`, `workout_warmup_title`, `workout_warmup_step` — `StringParityTest` exige os dois arquivos |
| `app/src/test/java/com/treinos/app/ui/PlateMathTest.kt` (novo) | testes da quebra de anilhas |
| `app/src/test/java/com/treinos/app/ui/WarmupRampTest.kt` (novo) | testes da rampa |
| `app/src/test/java/com/treinos/app/ui/screens/WorkoutScreenTest.kt` | atalho visível para barra, ausente para halteres |

## Modelo de dados / contrato

```text
Sem migration, sem backend, sem dependência nova. Tudo em memória.

PlateInventory(
  barKg: Double = 20.0,
  plateKg: List<Double> = listOf(20.0, 15.0, 10.0, 5.0, 2.5, 1.25),
)

PlateResult(
  targetKg: Double,      // o que o usuário digitou
  loadedKg: Double,      // barKg + 2 * soma(perSideKg); NUNCA ultrapassa targetKg
  perSideKg: List<Double>, // anilhas de UM lado, ordem decrescente
  remainderKg: Double,   // targetKg - loadedKg (>= 0); 0 = carga exata
)

plateBreakdown(targetKg, inventory): PlateResult?
  - null quando targetKg não é finito, é <= 0 ou é < barKg;
  - null quando barKg não é finito ou <= 0;
  - guloso do maior para o menor, anilhas ilimitadas por denominação;
  - anilhas não finitas ou <= 0 são ignoradas.

warmupRamp(workingWeightKg, inventory): List<WarmupStep>
  degraus fixos: 40% × 8 reps, 60% × 5 reps, 80% × 3 reps
  - cada weightKg = plateBreakdown(percent * workingWeightKg)?.loadedKg;
  - descarta degrau <= barKg, degrau >= workingWeightKg e pesos repetidos
    (mantém a primeira ocorrência);
  - vazio quando workingWeightKg não é finito ou <= barKg.
```

## Regras e casos de borda

- Carga-alvo exata (100 kg, barra 20 kg) → por lado `[20, 20]`, total
  100 kg, resto 0.
- Carga não carregável (61 kg com anilhas de 1,25 kg) → arredonda **para
  baixo** (60 kg) e reporta resto 1 kg; nunca carrega acima do alvo.
- Carga abaixo da barra (15 kg) → `null`; o atalho não mostra nada.
- Inventário vazio → `perSideKg` vazio e `loadedKg = barKg`.
- `barKg` inválido (não finito ou <= 0) → `null`.
- Rampa de 60 kg: 40% = 24 kg arredonda para 20 kg (= barra) e é descartada;
  sobram 60% = 35 kg × 5 e 80% = 47,5 kg × 3.
- Rampa de 20 kg (só a barra) → vazia.
- Dois degraus que arredondam para o mesmo peso → mantém o primeiro (menor %).
- Formatação pt-BR com vírgula e sem zeros à direita (`formatDecimal`/`formatKg`
  de `Formatters.kt`); o texto montado usa os recursos de string, não concatena
  número em português hardcoded.
- O atalho só aparece para equipamento `barbell` (`isPlateLoadedEquipment`);
  halteres, máquina, cabo e peso corporal não mostram — evita matemática errada.
- Campo de carga vazio ou inválido no diálogo → atalho oculto, sem crash.
- O diálogo é o mesmo do treino e do Histórico (`SetEditDialog`, H3-54): o
  atalho aparece sempre que `plateInventory != null`, sem mudar o salvamento.

## Critérios de aceite

1. **Dado** carga-alvo 100 kg e barra 20 kg, **quando** `plateBreakdown` roda,
   **então** `perSideKg = [20, 20]`, `loadedKg = 100` e `remainderKg = 0`.
2. **Dado** carga-alvo 61 kg, **quando** `plateBreakdown` roda, **então**
   `loadedKg = 60` e `remainderKg = 1` (nunca ultrapassa o alvo).
3. **Dado** carga-alvo 15 kg (abaixo da barra), **quando** o cálculo roda,
   **então** retorna `null` e o atalho não exibe nada.
4. **Dado** carga de trabalho 60 kg, **quando** `warmupRamp` roda, **então**
   retorna `[35 kg × 5, 47,5 kg × 3]` (o degrau de 40% cai na barra e é
   descartado).
5. **Dado** um exercício de barra, **quando** o usuário abre o diálogo de série
   e digita 100, **então** o atalho mostra "Por lado: 20 + 20 kg" e a rampa
   de aquecimento.
6. **Dado** um exercício de halteres, **quando** o usuário abre o diálogo,
   **então** o atalho de anilhas **não** aparece.
7. **Dado** inventário vazio, **quando** `plateBreakdown(100)` roda, **então**
   `perSideKg` é vazio e `loadedKg = 20`.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/PlateMathTest.kt` | `alvo_100_por_lado_20_20`, `alvo_exato_sem_resto`, `alvo_nao_carregavel_arredonda_para_baixo`, `alvo_menor_que_barra_retorna_null`, `alvo_nao_finito_retorna_null`, `anilhas_vazias_retorna_barra`, `anilhas_invalidas_sao_ignoradas`, `barra_invalida_retorna_null`, `equipamento_de_barra_reconhecido` |
| `app/src/test/java/com/treinos/app/ui/WarmupRampTest.kt` | `rampa_60kg_descarta_40_por_cento`, `rampa_100kg_tres_degraus`, `rampa_abaixo_da_barra_vazia`, `rampa_deduplica_pesos_iguais`, `rampa_arredonda_para_baixo`, `rampa_nao_finita_vazia` |
| `app/src/test/java/com/treinos/app/ui/screens/WorkoutScreenTest.kt` | `atalho_de_anilhas_aparece_para_barra`, `atalho_nao_aparece_para_halteres` |

## Verificação manual

1. Iniciar treino com um exercício de barra (ex.: supino reto), abrir o diálogo
   de série e digitar 100 kg: conferir "Por lado: 20 + 20 kg" e a rampa de
   aquecimento.
2. Digitar 61 kg: conferir 60 kg carregados e o aviso de 1 kg restante.
3. Digitar 15 kg: conferir que o atalho some.
4. Abrir o diálogo de um exercício de halteres: conferir que o atalho não
   aparece.
5. Caminho de erro: digitar texto inválido na carga → o atalho some, sem crash.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Funções puras e sem persistência: o rollback é remover `PlateMath.kt`,
  `WarmupRamp.kt`, o parâmetro novo do `SetEditDialog` e as strings.
- Arredondar para baixo pode divergir da expectativa de quem quer "o mais
  próximo"; a regra é conservadora de propósito (nunca sobrecarregar) e está
  coberta por teste.
- Inventário fixo pode não refletir a academia do usuário; a função já é
  parametrizada, então ligar configuração depois é aditivo.
- O atalho é informativo: não altera a carga salva nem o comportamento do
  diálogo; se a UI falhar, o salvamento da série continua igual.
