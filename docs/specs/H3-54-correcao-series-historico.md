# H3-54 — Correção de séries em sessões concluídas (Histórico)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-54` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-14` |
| Implementação | `concluída` |

**Origem:** fora de escopo declarado da H3-27 ("corrigir treinos antigos")
+ varredura de polimento de 2026-11-13. O Histórico é somente leitura: um
erro de registro (carga/reps erradas, série duplicada) fica permanente.

## Objetivo

O usuário corrige ou exclui séries de um treino já finalizado direto do
Histórico, com as mesmas validações do treino em andamento.

## Fora de escopo

- Editar a composição do treino passado (trocar/adicionar exercício) — só
  séries existentes.
- Reabrir sessão concluída, editar data/hora ou duração.
- Excluir a sessão inteira pelo Histórico (permanece como hoje).
- Editar sessão em andamento (já existe na H3-27 via WorkoutScreen).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/components/SetEditDialog.kt` (novo) | extrair o diálogo de série do `WorkoutScreen.kt` (~linha 1100: AlertDialog com helper, sugestão, campos Repetições/Carga/RPE, "Excluir série"/"Cancelar") para reuso |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | usar o diálogo extraído, sem mudança de comportamento |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | card da sessão (~linha 598) ganha "Editar séries" → folha com os exercícios e séries da sessão; cada série abre `SetEditDialog` |
| `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` | `updateSet` (:511) e `deleteSet` (:526) já operam por id sem guarda de status; o caminho do Histórico passa a exigir sessão `completed` (função dedicada ou parâmetro explícito) e mantém o realinhamento de `completedSets` |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `deleteSessionExercise(id)` para remover a entrada do exercício que ficou sem séries |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | funções de edição de sessão concluída + recarga das métricas do dia |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | liga `onUpdateSet`/`onDeleteSet` do Histórico às funções novas do ViewModel |
| `app/src/main/res/values/strings.xml` + `values-en/strings.xml` | chaves novas |
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | novos casos de edição em sessão concluída |

## Modelo de dados / contrato

Sem migration. Reuso de `updateSetLog`/`deleteSetLog`/
`recalculateCompletedSets`: a edição muda só reps/carga/RPE — vínculo com o
exercício e `setNumber` preservados, como na H3-27.

## Regras e casos de borda

- Só sessões com `status == "completed"` são editáveis pelo Histórico
  (guarda explícita no repositório para o caminho novo).
- Mesmas validações da H3-27 (`SetValidation`): reps ≥ 1, carga ≥ 0, RPE
  opcional 6–10; erro inline com submit bloqueado (padrão H3-47).
- Excluir a última série de um exercício remove a entrada do exercício da
  sessão; se a sessão ficar sem séries, o card mostra "Sem séries
  registradas" e as métricas caem a zero — permitido.
- `startedAt`/`endedAt`/duração não mudam.
- Métricas do Histórico (Treinos/Séries/Volume/Duração) derivam do Room e
  recalculam sozinhas após a edição.
- Sessão em andamento não é editável por esse caminho (a edição em andamento
  continua pela tela de treino).

## Critérios de aceite

1. **Dado** sessão concluída com série 40 kg × 10, **quando** editar para
   42,5 kg × 8, **então** o card do Histórico e as métricas do mês refletem
   o novo valor.
2. **Dado** reps 0 ou RPE 5, **quando** salvar, **então** erro inline e
   nada muda.
3. **Dado** exclusão da última série de um exercício, **quando** confirmar,
   **então** o exercício some da sessão e o volume recalcula.
4. **Dado** app morto e reaberto após a edição, **quando** abrir o
   Histórico, **então** o valor corrigido persiste.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/WorkoutRepositorySessionTest.kt` | `update_set_em_sessao_concluida_persiste`, `delete_set_realinia_completed_sets`, `caminho_historico_rejeita_sessao_em_andamento`, `excluir_ultima_serie_vazia_exercicio` |

## Verificação manual

1. Histórico → dia → "Editar séries" → editar carga de uma série → conferir
   card e métricas.
2. Excluir a única série de um exercício → conferir card e métricas.
3. Tentar valor inválido (reps 0, RPE 5) → erro inline, nada salvo.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

Toca em dados históricos: a edição é intencional e visível no próprio app;
o backup da H3-45 protege os dados anteriores. Rollback de código por
`git revert`; séries já editadas não voltam (comportamento desejado).
