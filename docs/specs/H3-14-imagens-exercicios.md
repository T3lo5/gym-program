# H3-14 — Imagens dos exercícios no plano

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-14` |
| MoSCoW | `C` |
| Spec aprovada em | `pendente` |
| Implementação | `pendente` |
| Depende de | H1-3 (imagens dos exercícios — catálogo e media já existem), H3-11 (geração híbrida por sessão) |

## Objetivo

Cada exercício do plano gerado exibe sua imagem (do catálogo, via Coil) ao lado do nome, para o usuário reconhecer visualmente o exercício quando não conhece o nome técnico (ex: “Supino Inclinado com Pegada Média”).

## Fora de escopo

- Download/cache de imagens (já existe em H1-3 via `ExerciseMediaRepository` e Coil com cache 7 dias).
- Vídeos/animações de exercício (fora de escopo do roadmap H3-11).
- Imagens para corrida e mobilidade (elas não têm exercício de catálogo com imagem; usar ícone genérico ou sem imagem).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/HomeScreen.kt` | Card de sessão exibe imagem de cada exercício (via `ExerciseImage` + `exerciseMedia` map) |
| `app/src/main/java/com/treinos/app/ui/screens/GenerateScreen.kt` | Ao visualizar o plano gerado, cada exercício exibe imagem |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Garantir que `exerciseMedia` é carregado e passado para as telas de plano (já existe `loadExerciseMedia`) |
| `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` | Confirmar que já exibe imagem dos exercícios (já faz via `exerciseMedia` param) |
| `app/src/main/java/com/treinos/app/ui/screens/MobilityScreen.kt` | Não aplicar (mobilidade não tem imagem de catálogo) |
| `app/src/test/java/com/treinos/app/ui/HomeScreenTest.kt` | Teste: card de exercício exibe imagem quando disponível |

## Modelo de dados / contrato

O backend já envia `images` (List<String>, ex: `["Barbell_Incline_Bench_Press_-_Medium_Grip/0.jpg"]`) e `instructions` no `ExerciseDTO` (feito em correção recente). O app já tem `ExerciseMedia` com `imageUrl` e `ExerciseMediaRepository` para carregar via Coil.

O fluxo de imagem já existe para `WorkoutScreen` e `HistoryScreen`; precisa ser estendido para a tela de visualização do plano (Home/GenerateScreen).

## Regras e casos de borda

1. Se o exercício não tem imagem (ex: corrida/mobilidade com ID sintético `run_*`/`mobility_*`), não exibe imagem quebrada; mostra sem imagem ou ícone genérico.
2. Se a imagem falhar de carregar (rede), mostra placeholder sem quebrar o card (Coil já lida com isso).
3. Sessão com 0 exercícios não quebra a UI.
4. Imagem é opcional: o plano continua usável mesmo sem imagem.

## Critérios de aceite

1. **Dado** um plano com sessão de musculação, **quando** o usuário abre o card da sessão, **então** cada exercício exende sua imagem ao lado do nome (quando disponível no catálogo).
2. **Dado** uma sessão de corrida (ID `run_*`), **quando** o usuário abre o card, **então** não exibe imagem quebrada nem erro.
3. **Dado** um exercício sem imagem no catálogo, **quando** renderiza, **então** o card continua visível sem imagem.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/screens/HomeScreenTest.kt` | `card_de_exercicio_exibe_imagem_quando_disponivel`, `corrida_sem_imagem_nao_quebra_card` |

## Verificação manual

1. Gere um plano híbrido com musculação.
2. Abra a aba “Academia” e confira que os exercícios mostram imagem.
3. Abra a aba “Corrida” e confira que não há imagem quebrada.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Rollback: remover a exibição de imagem dos cards de plano, mantendo apenas o nome. O backend continua enviando `images`; nada quebra no contrato.
- Imagem ausente não impede o uso do app; o usuário ainda lê o nome do exercício.
