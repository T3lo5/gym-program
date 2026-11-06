# H3-13 — Título da sessão varia conforme o split aplicado

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-13` |
| MoSCoW | `M` |
| Spec aprovada em | `pendente` |
| Implementação | `concluída` |
| Depende de | H3-11 (geração híbrida por sessão e abas por modalidade) |

## Objetivo

Cada sessão de força no plano híbrido tem um título que reflita o split aplicado (ex: “Treino A — Empurrar”, “Treino B — Puxar”, “Treino C — Pernas e Core”) em vez de todas se chamarem “Academia A — Superiores”, para o usuário distinguir rapidamente qual sessão está iniciando.

## Fora de escopo

- Mudar o split com base no histórico ou adesão do usuário (isso é progressão/revisão, fora de escopo desta spec).
- Nomes customizáveis pelo usuário.
- Aplicar split a corrida ou mobilidade (elas mantêm seus próprios nomes de template).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `back/app/plan_builder.py` | `build_strength_session` recebe `focus_muscles` (já feito em H3-11); `_session_name_for_modality` ou novo nome deriva do split (push/pull/legs) e índice da sessão |
| `back/tests/test_hybrid_plan.py` | Teste: sessões de força têm nomes diferentes conforme o split (push/pull/legs) |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Exibe o `name` do dia/sessão conforme recebido do backend (já faz; verificar que não sobrescreve) |
| `app/src/test/java/com/treinos/app/ui/screens/HomeScreenTest.kt` | Teste: card exibe nome diferenciado por split |

## Modelo de dados / contrato

Nenhuma mudança de contrato. O campo `name` do `DayDTO` já existe e é exibido pelo app. A mudança está apenas em como o backend preenche esse nome para sessões de força.

Exemplo esperado:
- Sessão 1 de força: `"Treino A — Empurrar"` (peito, ombros, tríceps)
- Sessão 2 de força: `"Treino B — Puxar"` (costas, bíceps)
- Sessão 3 de força: `"Treino C — Pernas e Core"` (pernas, abdômen)
- Sessão 4 de força: `"Treino A — Empurrar"` (ciclo repete)

## Regras e casos de borda

1. O split segue a ordem: empurrar → puxar → pernas/core → repete.
2. O índice da letra (A, B, C…) segue a ordem das sessões de força na semana, não o weekday.
3. Se houver apenas 1 sessão de força, ela recebe “Treino A — Empurrar”.
4. Se houver mais de 3 sessões de força, o ciclo repete (A→B→C→A…).
5. Corrida e mobilidade mantêm seus nomes atuais de template (não entram no split).

## Critérios de aceite

1. **Dado** um plano com 3 sessões de força, **quando** o usuário vê o plano, **então** as sessões se chamam “Treino A — Empurrar”, “Treino B — Puxar” e “Treino C — Pernas e Core”.
2. **Dado** um plano com 1 sessão de força, **quando** o usuário vê o plano, **então** a sessão se chama “Treino A — Empurrar”.
3. **Dado** um plano com 4 sessões de força, **quando** o usuário vê o plano, **então** a 4ª sessão se chama “Treino A — Empurrar” (ciclo repetido).

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_hybrid_plan.py` | `forca_sessao_1_tem_nome_empurrar`, `forca_sessao_2_tem_nome_puxar`, `forca_sessao_3_tem_nome_pernas_core`, `forca_sessao_4_repete_nome_empurrar` |

## Verificação manual

1. Gere um plano híbrido com 3 sessões de força.
2. Confira que os títulos das sessões de força são diferentes e refletem o split (empurrar/puxar/pernas).
3. Gere um plano com 1 sessão de força e confira o título “Treino A — Empurrar”.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Rollback: voltar `_session_name_for_modality` para “Academia A — Superiores” genérico. O backend continua funcionando; apenas o título volta a ser genérico.
