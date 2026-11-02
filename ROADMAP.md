# Roadmap — Treinos App

## Pendente (alta prioridade)

### 1. Recuperação de backup / login por deviceId (PRIORITÁRIO)
**Problema:** Quando o app é desinstalado e reinstalado, um novo `deviceId` é gerado aleatoriamente no `AppPreferences`. A sincronização busca dados pelo `deviceId` atual, então os treinos salvos no backend ficam órfãos e não são restaurados. O usuário não sabe qual é o seu `deviceId` atual para informar no backend.

**Solução prevista:** 
- Exibir o `deviceId` atual na tela de ajustes para o usuário anotar/copiar.
- Permitir que o usuário informe o `deviceId` antigo (ou use login por e-mail/ID) para associar o novo aparelho aos dados já sincronizados no backend.

**Arquivos envolvidos:**
- `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` — geração/armazenamento do `deviceId`
- `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` — `downloadSync()`
- `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` — tela de ajustes

### 2. Acompanhamento por séries (não circuito)
**Problema:** O fluxo atual obriga o usuário a fazer todos os exercícios do dia em sequência (circuito) e repetir tudo X vezes. Não há como marcar "série 1 de 4" de um exercício, descansar, e só então ir para a série 2 do mesmo exercício antes de passar ao próximo.

**Solução prevista:** Telas de execução de exercício com controle de séries/repetições (ex: "1/4 séries", "8-12 reps"), temporizador de descanso entre séries e navegação só para o próximo exercício após concluir todas as séries do atual.

**Arquivos envolvidos:**
- `app/src/main/java/com/treinos/app/ui/screens/WorkoutScreen.kt` (nova)
- `app/src/main/java/com/treinos/app/data/local/WorkoutDao.kt` — controle de progresso por série
- `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` — lógica de sessão por exercício

### 3. Imagens e/ou vídeos dos exercícios
**Problema:** O usuário não sabe o nome do exercício (ex: "Bent-Arm Dumbbell Pullover") e fica perdido sem saber como executar.

**Solução prevista:** Adicionar campo de URL de imagem e/ou vídeo demonstrativo a cada exercício no catálogo, exibido na tela de detalhes do exercício e durante a execução.

**Arquivos envolvidos:**
- `app/src/main/java/com/treinos/app/data/local/WorkoutPlanExerciseEntity.kt` — campos `imageUrl` e `videoUrl`
- `app/src/main/java/com/treinos/app/ui/screens/ExerciseDetailScreen.kt` (nova)
- Backend: catálogo de exercícios com mídia

## Concluído

- Backend rodando via Docker com endpoint `/v1/sync/download`
- Sincronização bidirecional (envio + download/restauração)
- Keystore fixo para atualizações sem desinstalar o app
- Correção de textos quebrados na tela inicial
- Novo tema verde e ícone de halteres
- IA SenseNova funcionando (timeout aumentado + resolução de IDs de exercícios)
- Correção de crash por `LazyColumn` aninhado na tela inicial
