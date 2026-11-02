# Roadmap — Treinos App

## Pendente (alta prioridade)

### Sincronização por deviceId (reinstalação do app)
**Problema:** Quando o app é desinstalado e reinstalado, um novo `deviceId` é gerado aleatoriamente no `AppPreferences`. A sincronização busca dados pelo `deviceId` atual, então os treinos salvos no backend ficam órfãos e não são restaurados.

**Solução prevista:** Permitir que o usuário informe/recupe o `deviceId` antigo na tela de ajustes (ou login por e-mail/ID) para associar o novo aparelho aos dados já sincronizados no backend.

**Arquivos envolvidos:**
- `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` — geração/armazenamento do `deviceId`
- `app/src/main/java/com/treinos/app/data/WorkoutRepository.kt` — `downloadSync()`
- `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` — tela de ajustes

## Concluído

- Backend rodando via Docker com endpoint `/v1/sync/download`
- Sincronização bidirecional (envio + download/restauração)
- Keystore fixo para atualizações sem desinstalar o app
- Correção de textos quebrados na tela inicial
- Novo tema verde e ícone de halteres
- IA SenseNova funcionando (timeout aumentado + resolução de IDs de exercícios)
