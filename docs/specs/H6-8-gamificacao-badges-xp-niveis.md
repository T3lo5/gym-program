# H6-8 — Gamificação: badges, XP e níveis

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H6-8` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-21` |
| Implementação | `concluída em 2026-11-21` |
| Depende de | H4-13 (streak), H4-1 (PRs/1RM), H3-28 (histórico) |

## Objetivo

O usuário vê seu nível, XP acumulado e badges conquistadas em uma tela própria de progresso, com marcos alcançados com base nos treinos concluídos, sequência (streak) e recordes pessoais — tudo local, offline e sem conta.

## Fora de escopo

- Notificações ou lembretes para manter sequência;
- Ranking social, compartilhamento ou comparação entre usuários;
- Recompensas externas, loja ou trocas de XP;
- Mudança de Room, banco, preferências ou `:protocol` (tudo é derivado dos dados existentes);
- Badges baseados em GPS, FC, distância ou passo — só musculação/registro concluído;
- Tela de onboarding ou metas configuráveis pelo usuário.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/gamification/GamificationEngine.kt` | **Novo.** `GamificationLevel`, `Badge`, `UserGamificationState`, `computeLevel(xp)`, `earnedBadges(sessions, streak, personalRecords)` puro, sem Android |
| `app/src/main/java/com/treinos/app/ui/gamification/GamificationScreen.kt` | **Novo.** Composable da tela de progresso: nível, barra de XP, lista de badges (conquistados + bloqueados) |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Expor `gamificationState` derivado de `sessions`, `streak` e `personalRecords`; rota `Screen.Gamification` |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Adicionar rota `Screen.Gamification` ao `AnimatedContent` |
| `app/src/main/java/com/treinos/app/ui/BottomNavigationBar.kt` | Adicionar 4º item "Progresso" → `Screen.Gamification` (apenas em hub) |
| `app/src/main/java/com/treinos/app/ui/Theme.kt` | Adicionar cores de nível/badge (bronze, prata, ouro, diamante) se necessário |
| `app/src/main/res/values/strings.xml` + `values-en/` | Chaves de tela, níveis, badges, estados vazios, descrições de acessibilidade |
| `app/src/test/java/com/treinos/app/ui/gamification/GamificationEngineTest.kt` | **Novo.** Testes JVM de nível, XP e badges |
| `app/src/test/java/com/treinos/app/ui/GamificationScreenTest.kt` | **Novo.** Robolectric: tela renderiza nível/XP/badges |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

Sem mudança de Room, rede, preferências ou `:protocol`. Tudo derivado de:
- `List<SessionWithExercises>` (sessões concluídas)
- `StreakSummary` (H4-13, `buildStreak`)
- `personalRecords` / `isPersonalRecord` (H4-1)

```kotlin
data class GamificationLevel(
    val level: Int,
    val name: String,
    val xpRequired: Int,      // XP necessário para atingir este nível
    val color: Int,           // cor do nível (hex ARGB)
)

data class Badge(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,         // nome do ícone Material (resolvido na UI)
    val xpReward: Int,
    val color: Int,           // cor do badge (hex ARGB)
    val unlocked: Boolean,
    val unlockedAt: Long?,    // millis, null se bloqueado
)

data class UserGamificationState(
    val level: Int,
    val xp: Int,
    val xpToNext: Int,
    val badges: List<Badge>,
    val totalSessions: Int,
    val currentStreak: Int,
    val totalPrs: Int,
)
```

XP por sessão concluída: `+50 XP`. Badge concede seu `xpReward` uma vez.

Níveis (progressão linear): nível `n` requer `50 * n * (n + 1) / 2` XP acumulado (1º nível = 0 XP, 2º = 100, 3º = 300, 4º = 600, …).

## Regras e casos de borda

1. Só sessões `status == "completed"` contam para XP e total de sessões;
2. `in_progress` não gera XP nem conta para streak;
3. Cada sessão concluída rende 50 XP (independentemente de modalidade ou duração);
4. Badges são concedidos uma única vez; `unlockedAt` preenchido na primeira vez que o critério é atingido;
5. Catálogo de badges inicial:
   - `primeiro_treino`: 1 sessão concluída → 50 XP;
   - `semana_de_ouro`: 7 sessões concluídas → 100 XP;
   - `marcador_cem`: 100 sessões concluídas → 500 XP;
   - `estreia_de_streak`: streak atual ≥ 3 → 75 XP;
   - `semana_ininterrupta`: streak atual ≥ 7 → 150 XP;
   - `primeiro_recorde`: ≥ 1 PR pessoal → 100 XP;
   - `colecionador_de_recordes`: ≥ 5 PRs → 200 XP;
   - `maratonista_modalidade`: concluído treino em cada modalidade (força, corrida, mobilidade) → 150 XP;
6. `totalPrs` = número de exercícios com pelo menos um PR (reusa `personalRecords` da H4-1);
7. Sem dados → nível 1, 0 XP, sem badges, sem exceção;
8. Tudo é recalculado em memória a cada leitura; não persiste estado de gamificação;
9. A tela mostra badges conquistados primeiro (opacidade total) e bloqueados depois (opacidade reduzida, ícone cifrado);
10. Barra de XP preenchida proporcionalmente entre `xp` e `xpToNext`;
11. Acessibilidade: cada badge tem `contentDescription` com título, estado (conquistado/bloqueado) e descrição;
12. Modo avião não altera o resultado.

## Critérios de aceite

1. **Dado** nenhuma sessão concluída, **quando** a tela de Progresso abre, **então** mostra nível 1, 0 XP, barra vazia e nenhum badge conquistado;
2. **Dado** 1 sessão concluída, **quando** a tela abre, **então** XP = 50, nível 1, badge "Primeiro treino" conquistado;
3. **Dado** 7 sessões concluídas, **quando** a tela abre, **então** XP = 350, nível 2 (requer 100 XP), badge "Semana de ouro" conquistado;
4. **Dado** streak atual de 7 dias, **quando** a tela abre, **então** badge "Semana ininterrupta" conquistado;
5. **Dado** ≥ 1 PR pessoal, **quando** a tela abre, **então** badge "Primeiro recorde" conquistado;
6. **Dado** treino concluído em cada uma das 3 modalidades (força, corrida, mobilidade), **quando** a tela abre, **então** badge "Maratonista de modalidade" conquistado;
7. **Dado** idioma English, **quando** a tela abre, **então** todos os textos estão em inglês;
8. **Dado** modo avião, **quando** a tela abre, **então** renderiza normalmente sem rede;
9. **Dado** TalkBack ativo, **quando** o foco chega a um badge, **então** é lida a descrição com o estado.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/gamification/GamificationEngineTest.kt` | `nivel_inicial_sem_xp`, `xp_de_sessao_concluida`, `sessao_em_andamento_nao_gera_xp`, `subida_de_nivel_apos_limite`, `badge_primeiro_treino_concedido`, `badge_semana_de_ouro_em_sete`, `badge_marcador_cem_em_cem`, `badge_estreia_de_streak_em_tres`, `badge_semana_ininterrupta_em_sete`, `badge_primeiro_recorde`, `badge_colecionador_de_recorde_em_cinco`, `badge_maratonista_modalidade`, `badge_so_uma_vez`, `sem_dados_nao_quebra`, `xp_para_proximo_nivel`, `barra_proporcional_entre_xp_e_proximo` |
| `app/src/test/java/com/treinos/app/ui/GamificationScreenTest.kt` (Robolectric) | `tela_mostra_nivel_e_xp`, `badges_conquistados_antes_dos_bloqueados`, `badge_bloqueado_tem_opacidade_reduzida`, `tela_vazia_sem_sessoes` |

## Verificação manual

1. Registre um treino concluído → abra Progresso → confira nível 1, 50 XP, badge "Primeiro treino";
2. Registre mais 6 treinos → confira nível 2, 350 XP, badge "Semana de ouro";
3. Faça treinos em 7 dias seguidos → confira badge "Semana ininterrupta";
4. Bata um recorde pessoal → confira badge "Primeiro recorde";
5. Treine nas 3 modalidades (força, corrida, mobilidade) → confira badge "Maratonista de modalidade";
6. Altere para English → confira textos em inglês;
7. Modo avião → a tela renderiza normalmente;
8. TalkBack → percorra os badges e confira a descrição lida.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Rollback:** remover `GamificationEngine.kt`, `GamificationScreen.kt`, a rota `Screen.Gamification`, o 4º item do bottom bar e as strings; nenhum dado criado ou apagado, sem migration;
- **Desempenho:** o cálculo roda em memória sobre as sessões já carregadas; usar `remember(sessions, streak, personalRecords)` para evitar recálculo a cada recomposição;
- **XP duplicado:** como não há persistência de estado de gamificação, recalcular a cada abertura é intencional e idempotente.
