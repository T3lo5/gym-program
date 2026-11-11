# H3-41 — Troca de exercício em plano já gerado (swap)

> Evolui a H3-25 para dar ao usuário controle direto sobre o que a IA escolheu: trocar um exercício de um plano existente por outro do catálogo, sem regenerar todo o plano.

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-41` |
| MoSCoW | `C` |
| Spec aprovada em | `pendente de aprovação` |
| Implementação | `pendente` |

**Depende de:** [H3-25](H3-25-selecao-por-nome.md) (catálogo como fonte de verdade e resolução nome→ID) e [H3-24](H3-24-midia-exercicio-plano.md) (mídia/instruções do exercício persistem no plano).  
**Não altera contrato de API/DTO do backend** (troca é decisão local no app; o `exercise_catalog.json` já é fonte local).

## Objetivo

Deixar o usuário trocar um exercício que a IA escolheu por outro do mesmo grupo muscular e equipamento compatível, mantendo o restante do plano intacto — reduzindo o atrito de "não gosto deste exercício" sem jogar fora a geração inteira.

## Fora de escopo

- Troca no relógio (Wear OS) — fica para H3-17..H3-20.
- Troca que muda o músculo/foco do plano ou o objetivo da sessão.
- Regeneração parcial por IA (nova chamada ao backend).
- Troca de exercício dentro de uma sessão já em andamento (só planos salvos/não iniciados).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | Nenhuma mudança de schema (reutiliza `WorkoutPlanExerciseEntity`). |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | Adicionar `updatePlanExercise(exerciseIdRow, exerciseId, exerciseName, primaryMuscles, equipment, exerciseOrder)` puro. |
| `app/src/main/java/com/treinos/app/data/repository/WorkoutRepository.kt` | Adicionar `swapExercise(dayId, position, replacementExerciseId) -> Result<Unit>` (validação + transação). |
| `app/src/main/java/com/treinos/app/data/media/ExerciseMediaRepository.kt` | Reutilizar `catalogForExercise(id)` / índice local para listar candidatos de troca (mesmo músculo, equipamento informado). |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | (Opcional) ação de troca no card do plano gerado antes de salvar. |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | (Opcional) ação de troca no exercício da sessão não iniciada. |
| `app/src/main/java/com/treinos/app/ui/components/ExercisePickerSheet.kt` (novo) | Bottom sheet de escolha do substituto: lista filtrada por músculo + equipamento, com nome canônico (H3-25/H3-36). |
| `app/src/test/java/.../ExerciseSwapTest.kt` (novo) | Testes puros de validação de troca. |

## Modelo de dados / contrato

```text
Sem mudança de Room (WorkoutPlanExerciseEntity já existe, H3-24).
Sem mudança de contrato app↔backend.

Contrato interno da troca:
- Entrada: dayId (Long), posição do exercício no dia (Int), replacementExerciseId (String do catálogo local).
- Validações antes de gravar:
  1. replacementExerciseId existe no catálogo local (exercise_catalog.json).
  2. É exercício de força (_is_strength_exercise) — não se troca alongamento/corrida por força.
  3. Músculo primário igual ao do exercício original (mesmo grupo da troca).
  4. Equipamento do substituto está entre os informados pelo usuário no request (sem equipamento novo).
  5. Não duplicar o mesmo exerciseId dentro do mesmo dia.
  6. Manter mínimo de exercícios por sessão (≥ 3 para força, regra H3-22/H3-25).
- Campos preservados do original: sets, targetReps, targetRpe, restSeconds, notes, exerciseOrder, metricType.
- Campos atualizados: exerciseId, exerciseName, primaryMuscles, equipment (+ instructions/images do substituto via H3-24 quando houver).
- Instruções/imagens do substituto vêm do catálogo (H3-24/H1-3), não do plano original.
```

## Regras e casos de borda

- Mesmo músculo primário: o substituto deve ter `primaryMuscles` contendo o mesmo grupo do original (case-insensitive, normalizado).
- Equipamento compatível: o equipamento do substituto deve estar em `availableEquipment` do plano/request original.
- Sem equipamento novo: nunca sugerir halter se o usuário só informou barra, por exemplo.
- Não duplicar: se o substituto já existe no mesmo dia, bloquear a troca com mensagem.
- Mínimo preservado: se o dia fica com < 3 exercícios de força após a troca (não aplicável, troca é 1-para-1, então o count não muda; regra vale só se houver remoção futura).
- Catálogo vazio/offline: se `exercise_catalog.json` não carregar, a troca fica indisponível (botão desabilitado, motivo no log).
- Nome canônico: o `exerciseName` gravado é o canônico do catálogo no idioma do app (H3-36), não o nome digitado.
- Transação: a troca grava ou não grava nada (rollback se qualquer validação falhar).
- Não afeta sessões já iniciadas: só planos com `status != "in_progress"`.

## Critérios de aceite

1. **Dado** um plano com um exercício de peito (barra) e **quando** o usuário troca por outro exercício de peito compatível, **então** o `exerciseId`/`exerciseName`/`equipment` são atualizados e os demais campos (sets, reps, ordem) são preservados.
2. **Dado** um substituto de músculo diferente (ex.: perna no lugar de peito), **quando** tenta trocar, **então** é rejeitado com erro de "músculo incompatível" e nada é gravado.
3. **Dado** um substituto com equipamento não informado pelo usuário (ex.: máquina quando só tem barra/halter), **quando** tenta trocar, **então** é rejeitado e nada é gravado.
4. **Dado** o substituto já existe no mesmo dia, **quando** tenta trocar, **então** é rejeitado com "exercício já está neste dia".
5. **Dado** substituto inexistente no catálogo local, **quando** tenta trocar, **então** é rejeitado e nada é gravado.
6. **Dado** troca válida, **quando** grava, **então** a operação é atômica (sucesso total ou nenhum efeito).
7. **Dado** sessão já iniciada (`in_progress`), **quando** tenta trocar, **então** é bloqueada.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/repository/ExerciseSwapTest.kt` | `test_troca_valida_preserva_campos_e_atualiza_id_nome`, `test_troca_musculo_diferente_rejeitada`, `test_troca_equipamento_nao_informado_rejeitada`, `test_troca_duplicada_no_dia_rejeitada`, `test_troca_id_inexistente_rejeitada`, `test_troca_eh_atomica_sem_efeito_em_falha`, `test_troca_sessao_andamento_bloqueada`, `test_candidatos_filtram_por_musculo_e_equipamento` |

## Verificação manual

1. Gerar um plano de força, abrir o plano no app, tocar em "trocar" em um exercício.
2. Escolher substituto do mesmo músculo e equipamento disponível → confere que só muda o exercício, mantendo séries/reps/ordem.
3. Tentar trocar por exercício de outro músculo → confere rejeição e plano inalterado.
4. Tentar trocar por equipamento não informado → confere rejeição.
5. Rodar `./scripts/verify.sh` (gate verde).

## Comando de verificação

```bash
./scripts/verify.sh
```

Critério: exit code `0` e linha `VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura`.

## Riscos e rollback

- **Substituto "parecido, mas não igual":** mitigado pela regra de mesmo músculo primário + equipamento informado; se houver troca indesejada, endurecer a chave de match (incluir grupo secundário).
- **Mídia/instruções do substituto ausentes:** o `ExerciseImage` já tem fallback de letra (H3-32/H3-24); não quebra a troca.
- **Rollback:** remover a chamada a `WorkoutRepository.swapExercise` da UI; o banco não muda (sem nova migration).
