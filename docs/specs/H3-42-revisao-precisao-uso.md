# H3-42 — Revisão de precisão da geração com base no uso real

> Evolui a H3-25 para fechar o loop: a partir dos exercícios que o usuário realmente faz, pula ou troca, ajustar a seleção da IA para aumentar a precisão das próximas gerações — tudo local, sem enviar dados pessoais para o backend.

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-42` |
| MoSCoW | `C` |
| Spec aprovada em | `pendente de aprovação` |
| Implementação | `pendente` |

**Depende de:** [H3-25](H3-25-selecao-por-nome.md) (catálogo como fonte de verdade), [H3-41](H3-41-troca-exercicio.md) (troca gera sinal de rejeição) e [H3-22](H3-22-robustez-geracao-ia.md) (salvamento parcial e drops já registrados).  
**Não envia dados de uso para o backend** (local-first; só o usuário, se quiser, sincroniza snapshot via H1-7/H2-3).

## Objetivo

Aumentar a precisão do que a IA escolhe (H3-25) observando o uso real: exercícios concluídos reforçam, exercícios pulados/trocados penalizam, e o peso vira ordem de preferência no catálogo do prompt das próximas gerações.

## Fora de escopo

- Enviar histórico de uso para o backend/SenseNova.
- Mudar o modelo de IA ou adicionar fine-tuning.
- Prescrição médica ou recomendação de carga baseada em uso (fica em H3-5 progressão).
- Revisão que altere planos já salvos (só afeta próximas gerações).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/local/Entities.kt` | Nova entidade `ExerciseUsageEvent` (evento de uso: concluído/pulado/trocado, data, plano/dia/exercício). |
| `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` | `insertUsageEvent(...)`, `getUsageEvents(days)`, `rejectionWeight(exerciseId)` puro. |
| `app/src/main/java/com/treinos/app/data/repository/WorkoutRepository.kt` | `recordExerciseUsage(exerciseId, outcome)`, `usageWeightsForCatalog()` puro (mapa exerciseId → peso). |
| `back/app/plan_builder.py` | `catalog_summary_for_request` ordena o pool pelo peso de uso (quando recebido via request, opcional) — mantém todos os itens, só reordena. |
| `back/app/main.py` | Aceitar `exerciseWeights` opcional no request (sem quebrar contrato: campo aditivo, `ignoreUnknownKeys`) e repassar ao builder. |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | (Opcional) exibir "exercícios mais usados / evitados" como insumo da geração. |
| `app/src/test/java/.../ExerciseUsageTest.kt` (novo) | Testes puros de peso de rejeição/confirmação. |
| `back/tests/test_uso_precisao.py` (novo) | Testes de ordenação do catálogo por peso de uso. |

## Modelo de dados / contrato

```text
Nova Room entity (aditiva, migration sem destruição):

ExerciseUsageEvent:
  id (PK, auto), exerciseId (String, não nulo), outcome ("completed"|"skipped"|"swapped"),
  occurredAt (Long, epoch ms), planId (Long?, nullable), dayId (Long?, nullable),
  source ("generation"|"manual"|"swap")

Contrato de uso interno (não é API):
- recordExerciseUsage(exerciseId, outcome) grava 1 evento; nada mais.
- rejectionWeight(exerciseId) = (swapped + skipped) - completed, com piso 0 e teto configurável (ex.: 5).
- usageWeightsForCatalog() = mapa exercício → peso; usado para ordenar o catálogo do prompt (mais relevantes primeiro).
- O peso não exclui exercício do pool (cobertura total preservada, H3-25): só reordena.
- Decaimento opcional: eventos mais antigos valem menos (ex.: peso linear pelos últimos 90 dias).

Contrato backend (aditivo, compatível):
- GenerateRequest ganha campo opcional `exerciseWeights` (mapa id→peso, ignorado se ausente/desconhecido).
- Resposta inalterada (mesmos campos da H3-21).
```

## Regras e casos de borda

- Sinal só de uso do próprio usuário no aparelho (local-first).
- `completed` incrementa confiança; `skipped` e `swapped` incrementam rejeição.
- Peso com piso 0 (não vira negativo) e teto (não deixa um exercício dominar para sempre).
- Decaimento temporal: eventos > 90 dias valem menos (configurável, default ativo).
- Ordenação do catálogo: itens com maior peso aparecem primeiro na lista do prompt; todos ainda listados (cobertura total, sem cap).
- Exercício sem eventos tem peso neutro (ordem original do catálogo).
- Não afeta planos já salvos: só próximas gerações.
- Sem envio de dados pessoais: `exerciseId` não é dado pessoal (não identifica o usuário).
- Migração Room aditiva (nova tabela, sem `fallbackToDestructiveMigration`, padrão H1-6).

## Critérios de aceite

1. **Dado** um exercício concluído 3 vezes e nunca trocado, **quando** gera um novo plano, **então** ele aparece antes (maior peso) no catálogo do prompt, mas ainda está listado se for do pool.
2. **Dado** um exercício trocado 2 vezes (rejeição), **quando** gera, **então** ele aparece depois (menor peso) no catálogo do prompt.
3. **Dado** exercício com peso de rejeição no teto, **quando** gera, **então** ele ainda pode aparecer (cobertura total preservada, nunca excluído).
4. **Dado** evento com mais de 90 dias, **quando** calcula o peso, **então** contribui com peso decaído (não zero, mas reduzido).
5. **Dado** request sem `exerciseWeights`, **quando** o backend monta o catálogo, **então** usa a ordem original do catálogo (sem quebra, compatível com clientes antigos).
6. **Dado** evento de uso gravado, **quando** falha a gravação, **então** não afeta o plano nem a geração (uso é sinal opcional).
7. **Dado** `exerciseWeights` com ID inexistente no catálogo, **quando** o backend processa, **então** ignora o peso desconhecido (sem erro).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/repository/ExerciseUsageTest.kt` | `test_completado_aumenta_peso`, `test_trocado_aumenta_rejeicao`, `test_peso_piso_zero`, `test_peso_teto`, `test_decaimento_evento_antigo`, `test_exercicio_sem_eventos_peso_neutro`, `test_uso_nao_afeta_plano_salvo` |
| `back/tests/test_uso_precisao.py` | `test_catalogo_ordenado_por_peso_uso`, `test_cobertura_total_preservada_com_peso`, `test_peso_id_desconhecido_ignorado`, `test_ordem_original_sem_pesos` |

## Verificação manual

1. Gerar plano, executar e concluir o mesmo exercício em 3 dias diferentes.
2. Gerar novo plano: conferir que esse exercício aparece mais cedo no catálogo (log do prompt / métrica `catalog_summary_metrics`).
3. Trocar um exercício (H3-41) 2 vezes, gerar novamente: conferir que ele cai na ordem.
4. Rodar `./scripts/verify.sh` (gate verde).

## Comando de verificação

```bash
./scripts/verify.sh
```

Critério: exit code `0` e linha `VERIFICAÇÃO COMPLETA OK — build, testes, lint e assinatura`.

## Riscos e rollback

- **Bolha de uso (sempre os mesmos exercícios):** mitigado pelo teto de peso e decaimento temporal; se houver variação baixa, reduzir o teto.
- **Peso distorcido por uso esporádico:** piso 0 e teto evitam dominância; eventos antigos decaem.
- **Rollback:** remover a gravação de `ExerciseUsageEvent` e a ordenação por peso no builder; banco aditivo (nova tabela), não há dados críticos a perder.
