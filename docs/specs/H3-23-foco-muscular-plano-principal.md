# H3-23 — Foco muscular respeitado no plano principal

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-23` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-07` (decisão: foco = prioridade no principal; exclusivo só no complementar) |
| Implementação | `concluída` |

**Decisão de produto aprovada em 2026-11-07** (ver "Decisão" abaixo):
no plano principal, foco muscular significa **prioridade**, não
exclusividade. Exclusividade continua sendo o plano complementar (H3-16).

## Objetivo

Quando o usuário seleciona um grupo muscular no plano principal (ex.:
Abdômen), o treino gerado reflete a escolha em todas as sessões de força — e a
UI explica que "APENAS aquele grupo" é o modo complementar. Hoje a escolha é
ignorada em silêncio.

## Contexto (estado atual verificado)

- **App:** `focusMuscles` só é enviado no modo complementar
  (`AppViewModel.kt` L530: `if (isComplementary && muscles.isNotEmpty())`); no
  modo principal vai apenas `targetMuscles` (`GenerateScreen.kt` L173 e L613).
- **Fallback ignora o foco:** `build_hybrid_plan` (`plan_builder.py` L1291)
  passa `focus_muscles = STRENGTH_SPLITS[...]` (rotação Empurrar/Puxar/Pernas)
  e `build_strength_session` (L793) usa `focus_muscles` **sempre que
  presente** — o `targetMuscles` do usuário é sobrescrito em todas as sessões.
  Pediu abdômen, recebe "Treino A — Empurrar", "Treino B — Puxar", "Treino C —
  Pernas e Core": exatamente o relato do usuário.
- **Prompt da IA:** `build_hybrid_user_prompt` (`main.py` L615) envia "Foco
  muscular global" sem nenhuma regra de prioridade; com `daysPerWeek` default
  4, a IA devolve semana completa sem destaque do grupo pedido.
- **H3-16 decidiu** (2026-11-06): foco EXCLUSIVO é do complementar; o
  principal é semana completa multi-modalidade. Esta spec não reverte isso.

## Decisão de produto (aprovada em 2026-11-07)

1. **Plano principal + músculos selecionados = prioridade:** cada sessão de
   força garante ≥ 1 exercício de cada grupo pedido (quando existir no pool)
   **antes** de completar com o split do ciclo. A semana continua completa
   (o `daysPerWeek` escolhido manda).
2. **"Apenas aquele grupo" = complementar:** a UI passa a dizer isso no modo
   principal quando há músculos selecionados.
3. Corrida/mobilidade nunca recebem foco de músculo (como hoje).

## Fora de escopo

- Mudar o comportamento do plano complementar (H3-16 intocado).
- Foco exclusivo no plano principal.
- Novos chips de músculo além dos 8 existentes (`GenerateScreen.kt` L73–82).
- Redistribuir dias da semana conforme o foco (o split continua ciclando).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/plan_builder.py` | `build_strength_session`: quando `focus_muscles` é o split do ciclo **e** o request traz `targetMuscles`, os grupos da sessão passam a ser `grupos pedidos + grupos do split` (dedup, pedidos primeiro); assinatura ganha parâmetro explícito `user_target_muscles` para não confundir os dois papéis |
| `back/app/plan_builder.py` | `build_hybrid_plan` (caminho não complementar, L1291) passa `user_target_muscles = _expand_muscles(target_muscles)` em vez de descartá-lo |
| `back/app/main.py` | `build_hybrid_user_prompt`: nova linha de regra — "Cada sessão de força deve incluir pelo menos 1 exercício de cada grupo em 'Foco muscular global' (quando existir no catálogo)" |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | No modo principal, quando `muscles.isNotEmpty()`: texto de ajuda sob os chips — "Os grupos escolhidos entram em todas as sessões de força. Para um plano APENAS de um grupo (ex.: só abdômen), use 'Adicionar plano complementar'." |
| `back/tests/test_hybrid_plan.py` | Testes da prioridade de foco no fallback |
| `back/tests/test_complementary_plan.py` | Regressão: complementar continua exclusivo |

## Modelo de dados / contrato

```text
Sem mudança de contrato (targetMuscles/focusMuscles já existem nos dois lados).
Mudança comportamental apenas no fallback local e no texto do prompt:
  targetMuscles = ["abs"], sessionsPerWeek = 3 (força)
    ANTES: 3 sessões = Empurrar / Puxar / Pernas e Core (foco ignorado)
    DEPOIS: cada sessão começa com ≥ 1 exercício de abdominals e completa
            com o split do ciclo até o nº de exercícios da duração
```

## Regras e casos de borda

- Grupo pedido sem candidato no pool (equipamento filtrado): a sessão segue
  com o split — não quebra, não inventa exercício.
- Vários grupos pedidos: todos garantidos antes do split; ordem = ordem
  informada pelo usuário.
- Sem `targetMuscles`: comportamento atual (split puro), byte a byte.
- Plano complementar (`focusMuscles` presente): caminho exclusivo da H3-16
  intocado — `user_target_muscles` não se aplica (`targetMuscles` vem vazio).
- O primeiro exercício composto (regra de equipamento de carga em academia)
  continua valendo após a reordenação de grupos.
- `enforce_complementary_plan` não é chamado no caminho principal (sem foco
  exclusivo) — sem interação com esta mudança.

## Critérios de aceite

1. **Dado** um request principal com `targetMuscles = ["abs"]` e 3 sessões de força, **quando** o fallback local gera, **então** cada sessão tem ≥ 1 exercício com `primaryMuscles` contendo `abdominals`.
2. **Dado** um request principal sem `targetMuscles`, **quando** o fallback local gera, **então** as sessões seguem exatamente o split atual (regressão zero).
3. **Dado** um request principal com `targetMuscles = ["abs", "glutes"]` em academia, **quando** o fallback gera, **então** cada sessão tem ≥ 1 exercício de cada um dos dois grupos antes dos grupos do split.
4. **Dado** um request complementar com `focusMuscles = ["abs"]`, **quando** gera, **então** nenhum exercício fora de `abdominals` (regressão da H3-16).
5. **Dado** o prompt híbrido com `targetMuscles` preenchido, **quando** o backend monta o prompt, **então** a regra de prioridade aparece no texto.
6. **Dado** o modo principal com músculos selecionados, **quando** a tela abre, **então** a dica sobre o modo complementar aparece sob os chips.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_hybrid_plan.py` | `test_foco_principal_garante_grupo_em_cada_sessao`, `test_foco_com_dois_grupos_garante_os_dois`, `test_sem_foco_mantem_split_atual`, `test_grupo_sem_candidato_nao_quebra_sessao` |
| `back/tests/test_complementary_plan.py` | `test_complementar_continua_exclusivo` (regressão) |

## Verificação manual

1. Gerar plano principal com foco "Abdômen" (3x força, academia): cada sessão de força tem um exercício de abdômen; a semana continua completa.
2. Gerar plano complementar "Abdômen": só exercícios de abdômen (como hoje).
3. Conferir a dica nova na tela de geração do modo principal.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Volume do grupo pedido pode ficar alto se o usuário pedir vários grupos em
  sessões curtas: a garantia é de **1 exercício por grupo**, o restante segue o
  split — sem inflar a sessão além do nº da duração.
- Mudança só no fallback e no prompt: se a IA estiver no ar, o efeito vem pela
  regra do prompt (H3-22 reduz o fallback); rollback = reverter os dois pontos
  (um commit).
