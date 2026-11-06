# H3-16 — Geração complementar: foco exclusivo (abdômen) e casa/peso corporal

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-16` |
| MoSCoW | `M` |
| Spec aprovada em | `2026-11-06` |
| Implementação | `concluída` |

**Depende da [H3-15](H3-15-planos-multiplos.md)** (modo "complementar" e
persistência sem substituir o plano principal).

## Objetivo

Gerar um plano só de força com foco **exclusivo** (ex.: apenas abdômen) e/ou
ambiente **casa/peso corporal**, curto o suficiente para caber na rotina, sem
mexer no treino de academia que o usuário já tem.

## Contexto (estado atual verificado)

- O contrato já tem `environment: Literal["gym","home","bodyweight"]`
  (`back/app/main.py` L275) e `targetMuscles`, mas **nada é exclusivo**:
  `_day_groups` (`plan_builder.py` L451–464) só *adiciona* os músculos pedidos
  ao template do split; `build_hybrid_plan` força a rotação `STRENGTH_SPLITS`
  (L939–951). "Só abdômen" é inalcançável hoje.
- `_build_pool` (L405–428) só aplica o filtro de equipamento se sobrarem ≥ 20
  exercícios — com peso corporal o filtro pode ser silenciosamente ignorado.
- O catálogo (`back/app/exercise_catalog.json`, 876 exercícios) tem campos
  `equipment` e `primaryMuscles`: **93 exercícios de abdômen, 45 sem
  equipamento** (`body only`/`None`). Dá para filtrar deterministicamente.
- `MUSCLE_ALIASES` (L17–31) já mapeia `abs → abdominals`.
- O app sempre envia `modalities` (caminho híbrido desde a H3-11); o foco
  precisa funcionar nesse caminho, não só no legado.
- Decisão de produto (2026-11-06): plano complementar é **só força**;
  corrida/mobilidade continuam no plano principal via híbrido da H3-11.

## Fora de escopo

- Planos complementares de corrida, mobilidade ou híbridos.
- Foco exclusivo no plano **principal** (geração principal segue com splits).
- Novos exercícios no catálogo ou vídeos (H3-14 cobre imagens).
- Progressão de carga específica de peso corporal além da já existente
  (H3-5: peso corporal → +1 rep).
- Revisão por IA de plano complementar (H3-15 deixa para o main).
- Prescrição médica / reabilitação: foco em abdômen não promete tratar nada.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/remote/BackendDtos.kt` | `GeneratePlanRequest` ganha `focusMuscles: List<String>? = null` (aditivo) |
| `app/src/main/java/com/treinos/app/ui/ProfileValidator.kt` ou novo `app/src/main/java/com/treinos/app/ui/ComplementaryValidator.kt` | Validação pura do modo complementar (espelha as regras do backend) |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | No modo complementar (H3-15): chips de músculo viram **foco exclusivo** (seleção explícita), modalidades somem (força fixa), durações 15/20/30/45 min, dias 1–6, chips de ambiente gym/home/bodyweight visíveis |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | `generatePlan` monta `focusMuscles` quando modo complementar; envia `modalities = [strength]` |
| `back/app/main.py` | `GenerateRequest.focusMuscles: Optional[List[str]]` validado contra `MUSCLE_ALIASES`; validações condicionais de `daysPerWeek`/`sessionDurationMinutes`; `build_hybrid_user_prompt` e `SYSTEM_PROMPT_HYBRID` recebem as restrições de foco/ambiente |
| `back/app/plan_builder.py` | `_day_groups`: com `focusMuscles`, grupos = foco expandido **sem template**; `_build_pool`: filtro **estrito** de equipamento quando `environment ∈ {home, bodyweight}` ou foco presente (sem regra dos ≥ 20); `build_strength_session`: sem rotação `STRENGTH_SPLITS` quando foco exclusivo, nº de exercícios pela duração; `build_hybrid_plan`/`normalize_hybrid_plan`: só sessões de força, pós-filtro de músculo/equipamento na resposta da IA |
| `back/tests/test_complementary_plan.py` (novo) | Testes do fallback e da normalização com foco |
| `back/tests/test_contract_v2.py` | Fixture com `focusMuscles` |
| `app/src/test/java/com/treinos/app/ui/ComplementaryValidatorTest.kt` (novo) | Regras do validador |

## Modelo de dados / contrato

```text
GeneratePlanRequest (app) / GenerateRequest (backend) — aditivo:
  focusMuscles: ["abs"]            // opcional; 1–4 grupos de MUSCLE_ALIASES
                                   // presente ⇒ plano COMPLEMENTAR de força

Validações condicionais (backend, 422 com mensagem clara):
  focusMuscles presente:
    - modalities, se enviada, deve ser exatamente ["strength"]
    - daysPerWeek: 1..6        (sem foco: mantém 2..6)
    - sessionDurationMinutes: 15..120  (sem foco: mantém 30..120)
    - cada item deve existir em MUSCLE_ALIASES (ex.: "abs", "glutes")
  environment = "bodyweight":
    - pool restrito a equipment ∈ {"body only", "none"} (case-insensitive)
  environment = "home":
    - pool restrito a availableEquipment ∪ {"body only", "none"}

Resposta: mesmo GenerateResponse de hoje (sem campo novo).
Nome do plano (fallback e prompt): "Complementar — <foco>" (ex.:
"Complementar — Abdômen") ou "Complementar — Casa" quando só ambiente.
O `kind = "extra"` é definido LOCALMENTE pelo app ao salvar (H3-15);
o backend não precisa conhecê-lo.
```

## Regras e casos de borda

- **Foco exclusivo**: todo exercício do plano tem `primaryMuscles` intersectando
  o foco expandido por `MUSCLE_ALIASES`. Nenhum grupo de template entra junto.
- **Equipamento estrito**: com `bodyweight`/`home` (ou foco presente), o filtro
  de equipamento nunca é ignorado por falta de candidatos. Pool vazio →
  **422** "nenhum exercício compatível com o foco/ambiente", nunca plano
  silently cheio de exercício de academia.
- Nº de exercícios por sessão pela duração: 15 min → 3, 20 min → 4,
  30 min → 5, ≥ 45 min → 6 (máximo 6 no complementar).
- Pool menor que o nº de exercícios pedido → reduz o nº até o mínimo 3;
  abaixo de 3 → 422.
- Sem repetição de exercício dentro da mesma sessão (já é regra do
  `_pick_exercise` via `used`); entre dias pode repetir.
- Resposta da IA fora do foco/equipamento: `normalize_hybrid_plan` remove os
  exercícios não conformes; sessão que sobrar com < 3 exercícios é reconstruída
  pelo fallback local com as mesmas restrições.
- `focusMuscles` vazio (`[]`) → tratado como ausente? **Não**: 422 (lista vazia
  é entrada inválida; o app omite o campo quando não há foco).
- `focusMuscles` + `modalities` com run/mobility → 422 (decisão de produto).
- Sem `focusMuscles` e sem modo complementar: comportamento **idêntico** ao
  atual (regressão zero nos testes existentes de `build_local_plan`/híbrido).
- `environment = "gym"` com foco: filtro de equipamento segue o
  `availableEquipment` informado (academia), foco continua exclusivo.
- Dias de descanso: `daysPerWeek = 1` gera 1 sessão + dias de descanso até
  completar a semana, como no fluxo atual.
- Nomeação de sessões: reaproveitar o helper de título da **H3-13** se já
  estiver merged na implementação; senão `"Abdômen"` + índice (A, B, C) quando
  `daysPerWeek > 1`. Coordenar para não divergir dos títulos do split.

## Critérios de aceite

1. **Dado** o modo complementar com foco `abs`, **quando** o plano é gerado
   (IA ou fallback), **então** 100% dos exercícios têm abdômen como músculo
   primário e o plano de academia anterior continua ativo (H3-15).
2. **Dado** ambiente `bodyweight`, **quando** o plano é gerado, **então** todo
   exercício tem `equipment ∈ {body only, none}` — inclusive quando a IA
   responde com exercícios de máquina (normalização remove/substitui).
3. **Dado** foco `abs` + duração 15 min + 2 dias/semana, **quando** gerado,
   **então** cada sessão tem 3 exercícios e o request é aceito (sem 422).
4. **Dado** `focusMuscles: ["nao-existe"]`, **quando** enviado, **então** 422
   com mensagem que cita o valor inválido.
5. **Dado** `focusMuscles` + `modalities: ["strength","run"]`, **quando**
   enviado, **então** 422.
6. **Dado** um request principal sem `focusMuscles`, **quando** gerado,
   **então** o plano é idêntico ao comportamento pré-H3-16 (testes existentes
   verdes sem alteração).
7. **Dado** o backend offline, **quando** o usuário gera o complementar,
   **então** o fallback local entrega o plano com as mesmas restrições.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_complementary_plan.py` | `test_foco_abs_exclusivo_no_fallback`, `test_bodyweight_so_peso_corporal`, `test_home_respeita_available_equipment`, `test_pool_vazio_422`, `test_duracao_15_tres_exercicios`, `test_um_dia_por_semana`, `test_foco_mais_run_422`, `test_grupo_invalido_422`, `test_lista_vazia_422`, `test_normalizacao_remove_exercicio_fora_do_foco`, `test_normalizacao_remove_equipamento_proibido`, `test_sessao_insuficiente_reconstruida`, `test_sem_foco_comportamento_inalterado` |
| `back/tests/test_contract_v2.py` | `test_generate_com_focus_muscles` (fixture nova em `contracts/`) |
| `app/src/test/java/com/treinos/app/ui/ComplementaryValidatorTest.kt` | `foco_valido`, `foco_invalido`, `lista_vazia_rejeitada`, `duracao_15_aceita_so_com_foco`, `dias_1_aceito_so_com_foco`, `sem_foco_regras_atuais` |
| `app/src/test/java/com/treinos/app/data/remote/` (ContractTest existente) | round-trip de `focusMuscles` no fixture compartilhado |

## Verificação manual

1. Home → "Gerar novo treino" → modo complementar → foco "Abdômen", ambiente
   "Peso corporal", 15 min, 2 dias → gerar → conferir que todo exercício é de
   abdômen sem equipamento e que o plano de academia segue na Home.
2. Repetir com backend desligado (fallback local) → mesmas garantias.
3. Ambiente "Casa" com chips "body only" + "bands" → conferir que só esses
   equipamentos aparecem.
4. Caminho de erro: foco sem seleção de músculo nem ambiente alternativo →
   botão gerar desabilitado com hint; request inválido direto na API → 422.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **IA ignorar o foco**: mitigado pela normalização determinística pós-resposta
  (filtro + reconstrução local); teste com fixture de IA "rebelde".
- **Pool pequeno demais** (ex.: glutes + bodyweight): a regra do mínimo 3 com
  422 explícito evita plano quebrado; mensagem orienta abrir o ambiente.
- **Regressão na geração principal**: todas as mudanças são condicionais a
  `focusMuscles`/modo complementar; `test_sem_foco_comportamento_inalterado`
  e a suíte híbrida existente são a trava.
- **Divergência de títulos com a H3-13** (em implementação no mesmo working
  tree): implementar H3-16 só depois da H3-13 merged, reusando o helper.
- Rollback: campos aditivos no contrato; reverter o commit não quebra
  app/backend antigos (campo desconhecido é ignorado nos dois lados).