# H3-28 — Home do dia e histórico escaneável

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-28` |
| MoSCoW | `M` |
| Spec aprovada em | `pendente de aprovação` (proposta de 2026-11-07) |
| Implementação | `pendente` |

**Origem:** auditoria UX/UI de 2026-11-07 ([relatório](../auditoria-ux-ui-2026-11-07.md) §P2.1, P2.5 e P2.7).

## Objetivo

Ao abrir o app, o treino de hoje está no topo com ação direta — e no
histórico, filtro e resultado ficam visíveis juntos.

## Contexto (estado atual verificado)

- Ordem fixa da Home: InProgress → chips de plano → ActivePlanCard → revisão
  → deload → TabRow → só então os DayCards (`HomeScreen.kt:121-247`) — o
  primeiro "Iniciar" fica após ~700+dp; nada marca "hoje".
- `defaultModalityTab` cai na aba de **amanhã** quando hoje é descanso
  (`WeeklySchedule.kt:41-44`).
- Home acumula ~15 itens: 3 `OutlinedButton` empilhados + 3 parágrafos
  permanentes de rodapé (`HomeScreen.kt:259-319`); "Excluir plano
  complementar" (destrutivo) no corpo da lista (`:175-187`); ActivePlanCard é
  o card mais destacado (`primaryContainer`) e não é acionável (`:526-576`).
- Histórico: chips de modalidade e chip do dia vêm **depois** de calendário +
  4 métricas + volume (`HistoryScreen.kt:148-255`) — tocar num dia não muda
  nada na área visível.

## Fora de escopo

- Bottom bar e pilha de navegação (H3-38).
- Novos cards de métrica; edição de plano na Home.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/WeeklySchedule.kt` | `todaySessionSelector` puro: sessão de hoje / próximo dia / descanso |
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | card-herói "Hoje"; badge "Hoje"; ações compactas; rodapé só sem plano; destrutivo no overflow; ActivePlanCard neutro |
| `app/src/main/java/com/treinos/app/ui/screens/HistoryScreen.kt` | chips logo após o calendário; com dia selecionado, lista antes das métricas |

## Modelo de dados / contrato

Sem mudança de dados. `todaySessionSelector(plan, weekday, tabKind)` puro
retorna `Today` (dia/índice), `Next(day)` ou `Rest(nextDay)` — consumido pela
Home e pela escolha de aba inicial.

## Regras e casos de borda

- Card-herói "Hoje — <nome da sessão>" logo após o InProgressCard, com botão
  "Iniciar" direto; badge "Hoje" no DayCard correspondente.
- Hoje é descanso → herói diz "Hoje é descanso — próximo: <dia>" (sem botão
  de iniciar); `defaultModalityTab` passa a preferir a aba que contém hoje
  quando hoje tem sessão; senão a próxima sessão futura.
- Ações secundárias ("Registrar atividade", "Mobilidade", "Gerar novo treino")
  em uma única Row de ações compactas (ícone + rótulo curto), no lugar dos 3
  botões empilhados.
- Os 3 parágrafos de rodapé só aparecem quando `plan == null` (empty state);
  com plano ativo, a Home termina na lista do dia.
- "Excluir plano complementar" sai do corpo da lista e vai para o menu de
  overflow do chip/seletor de planos (mantém a confirmação existente).
- ActivePlanCard perde `primaryContainer` (fica `surface`/neutro) — o destaque
  da tela passa a ser o herói de hoje.
- Histórico: chips de modalidade + chip "Dia X • limpar" logo após o
  calendário; **quando** um dia está selecionado, a lista filtrada vem logo
  após os chips e métricas/volume ficam depois dela.

## Critérios de aceite

1. **Dado** plano ativo com sessão hoje, **quando** abre a Home, **então** o
   herói "Hoje — <sessão>" está visível sem rolar e "Iniciar" começa o treino.
2. **Dado** hoje é descanso, **quando** abre a Home, **então** o herói diz
   "Hoje é descanso — próximo: X" e a aba inicial é a próxima sessão.
3. **Dado** plano ativo, **quando** rola a Home, **então** não há parágrafos
   de rodapé permanentes nem 3 botões empilhados.
4. **Dado** plano complementar ativo, **quando** quer removê-lo, **então** a
   ação está no overflow do seletor (com confirmação), não no corpo da lista.
5. **Dado** histórico com dia selecionado, **quando** toca o dia no
   calendário, **então** a lista daquele dia aparece logo abaixo dos chips,
   antes das métricas.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/.../WeeklyScheduleTest.kt` | `aba_inicial_e_a_de_hoje`, `descanso_escolhe_proxima_sessao`, `heroi_hoje_descanso_com_proximo` |
| `app/src/test/java/.../HomeScreenTest.kt` (Robolectric) | `heroi_hoje_visivel_sem_rolagem`, `badge_hoje_no_card`, `rodape_somente_sem_plano` |
| `app/src/test/java/.../HistoryScreenTest.kt` | `chips_antes_das_metricas`, `dia_selecionado_lista_primeiro` |

## Verificação manual

1. Abrir o app num dia de treino → herói no topo; iniciar direto.
2. Abrir num dia de descanso → mensagem coerente; aba correta.
3. Histórico: selecionar um dia → lista visível sem rolar uma tela.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Mudança de ordem pode quebrar testes existentes de Home que assumem ordem
  atual — atualizá-los junto (não enfraquecer). Rollback trivial (UI).
