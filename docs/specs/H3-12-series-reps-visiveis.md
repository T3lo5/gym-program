# H3-12 — Séries/reps visíveis em cada exercício do plano

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-12` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-06` (retroativa, implementada em `7a99520`) |
| Implementação | `concluída` (`7a99520`) |
| Depende de | H3-11 (geração híbrida por sessão e abas por modalidade) |

## Objetivo

O usuário consegue ver, em cada exercício do plano gerado, quantas séries e repetições são prescritas (ex: “Supino — 4 séries de 8-12 reps”), para saber o volume do treino antes de iniciar a sessão, sem precisar abrir cada exercício individualmente.

## Fora de escopo

- Edição manual de séries/reps dentro do app.
- Sugestão automática de carga baseada em histórico (isso é H3-5, progressão de carga).
- Exibir séries/reps apenas na tela de execução (WorkoutScreen) já existe; o foco aqui é a tela de visualização do plano (Home/GenerateScreen) e o card da sessão.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Card de sessão exibe `sets` e `targetReps` de cada exercício da sessão (ou resumo “4 séries × 8-12 reps”) |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Ao visualizar o plano gerado, cada exercício mostra `sets` e `targetReps` |
| `app/src/main/java/com/treinos/app/ui/WeeklySchedule.kt` | Se aplicável, incluir volume de séries/reps no rótulo ou card da sessão |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Confirmar que o ExerciseCard já exibe séries e reps (ajuste se faltar) |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | Teste: card de sessão exibe séries e reps dos exercícios |
| `app/src/test/java/com/treinos/app/ui/WeeklyScheduleTest.kt` | Teste: rótulo do plano ou card inclui séries/reps quando aplicável |

## Modelo de dados / contrato

O backend já envia `sets` (int) e `targetReps` (string, ex: "8-12", "10", "AMRAP") no `ExerciseDTO`. Nenhuma mudança de contrato é necessária; o app já recebe esses campos. Apenas a UI precisa exibi-los.

```kotlin
// Já existente em BackendDtos.kt / ExerciseDTO
exercise.sets        // Int, ex: 4
exercise.targetReps  // String, ex: "8-12"
```

## Regras e casos de borda

1. Cada exercício da sessão mostra `sets` × `targetReps`.
2. Corrida e mobilidade não têm `targetReps` (string vazia); neles mostra apenas `sets` ou nada (conforme a modalidade).
3. Se `targetReps` estiver vazio, mostra apenas “X séries” sem reps.
4. Sessão com 0 exercícios não quebra a UI (estado vazio).
5. O rótulo deve ser curto o suficiente para caber no card (ex: “4 séries × 8-12”).

## Critérios de aceite

1. **Dado** um plano com sessão de musculação de 6 exercícios, **quando** o usuário abre o card da sessão na Home, **então** vê cada exercício com “X séries × Y reps” (ou “X séries” quando reps vazio).
2. **Dado** um plano com sessão de corrida, **quando** abre o card, **então** não mostra reps (apenas distância/duração, conforme já existe).
3. **Dado** um exercício sem `targetReps`, **quando** renderiza o card, **então** não exibe texto de reps quebrado ou vazio.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/screens/HomeScreenTest.kt` | `card_de_sessao_mostra_series_e_reps_de_cada_exercicio`, `corrida_nao_mostra_reps` |
| `app/src/test/java/com/treinos/app/ui/WeeklyScheduleTest.kt` | `card_de_sessao_inclui_volume_de_series_reps` |

## Verificação manual

1. Gere um plano híbrido (3 força + 2 corrida + 2 mobilidade).
2. Abra a aba “Academia” e confira que cada exercício mostra séries e reps.
3. Abra a aba “Corrida” e confira que não mostra reps inválidos.
4. Abra a aba “Mobilidade” e confira que não mostra reps.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Texto muito longo no card pode quebrar o layout: usar quebra de linha ou truncar.
- Rollback: remover a exibição de `targetReps` dos cards, mantendo apenas `sets`. O backend continua enviando os campos; nada quebra no contrato.
