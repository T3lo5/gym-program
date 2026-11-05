# H3-9 — Backup automático no destino escolhido

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-9` |
| MoSCoW | `W` |
| Spec aprovada em | `pendente` (proposta de 2026-11-05 — **não** priorizar antes da H3-8) |
| Implementação | `pendente` |
| Depende de | H3-6, H3-8 |

## Objetivo

Depois de o usuário escolher um arquivo de destino, o app reenvia o backup quando
os dados mudam, sem ele precisar abrir o seletor de novo.

## Por que está em "W" e não em "C"

Nada disso existe hoje, e cada peça é uma decisão nova: nenhuma URI é persistível
(`takePersistableUriPermission` tem **zero** ocorrências em `app/src`), não há
WorkManager/JobService/sync adapter no app do celular, e as dependências de work
só aparecem como *version variable* sem uso no `build.gradle.kts` raiz. Automaticidade
em destino de terceiro é o ponto em que um app offline-first começa a acumular
falhas silenciosas. Só vale a pena se H3-8 provar que o manual não basta.

## Fora de escopo

- **Importação automática.** O import é destrutivo
  (`WorkoutRepository.kt:614` → `dao.replaceAllData`). Nada neste item substitui
  dados sem o "Substituir tudo" do usuário. Esta é a regra mais importante da
  spec.
- Sync bidirecional, merge, conflitos de dois aparelhos.
- Qualquer SDK de nuvem ou OAuth.
- Backup do backend (isto é arquivo local do usuário, não snapshot do H2-3).
- Múltiplos destinos simultâneos.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/build.gradle.kts` | dependência `androidx.work:work-runtime-ktx` em versão fixa (justificar no PR, regra do AGENTS.md) |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | `exportLauncher` (`:93`) persiste a URI escolhida com `FLAG_GRANT_READ/WRITE` + `takePersistableUriPermission` |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | `backupTargetUri`, `backupAutoEnabled`, `lastAutoBackupAt`, `lastAutoBackupError` |
| `app/src/main/java/com/treinos/app/data/backup/BackupScheduler.kt` | **Novo.** `WorkManager` com `NetworkType.NOT_ROAMING`, `backoff` e política de *replace* |
| `app/src/main/java/com/treinos/app/data/backup/AutoBackupWorker.kt` | **Novo.** Gera o JSON e escreve no URI persistido |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Interruptor "Backup automático", destino, último upload e erro |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Estado do interruptor, disparo após mudanças de dado, leitura de `lastAutoBackupAt` |

## Contrato

```kotlin
interface BackupTargetStore {
    var backupTargetUri: String?
    var backupAutoEnabled: Boolean
    var lastAutoBackupAt: String?     // ISO-8601, como lastSyncAt
    var lastAutoBackupError: String?
}
```

- URI persistida é escrita **depois** de o export manual ter tido sucesso, não no
  instante em que o seletor fecha.
- Permissão mantida por `takePersistableUriPermission(uri,
  FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION)`.
- Agendamento: um único worker com `ExistingPeriodicWorkPolicy.REPLACE` (nome
  fixo `treinos-auto-backup`), intervalo mínimo do WorkManager (24 h), mais um
  `OneTimeWorkPolicy.REPLACE` disparado ao sair de um treino ou importar, com
  *debounce* de minutos.
- Um arquivo por destino: sobrescreve `backup-treinos.json` no URI escolhido;
  sem retenção de versões, sem "lixeira".

## Regras e casos de borda

1. Interruptor desligado → nenhum worker agendado; exportação manual continua.
2. Permissão revogada (usuário removeu o app da conta, provedor desinstalado) →
   `SecurityException` ao abrir o stream: desliga o automático, mostra
   "Backup automático desligado: o app perdeu o acesso ao arquivo." e mantém os
   dados locais.
3. Falha de rede → `Result.retry()` com backoff; nunca mais que N tentativas por
   janela, e o erro só vira mensagem depois da última.
4. Arquivo maior que o limite da H3-6 ao importar — o automático nunca importa,
   então o limite só aparece no caminho manual.
5. Destino sumiu / `DocumentFile` não resolvido → desliga, não re-tenta para
   sempre consumindo bateria.
6. Dois aparelhos na mesma conta com o mesmo destino: o último escreve por cima.
   A UI deve dizer isso (um arquivo, sem merge).
7. Automaticidade **nunca** chama `prepareImport`/`confirmImport`. Ponto.
8. `BackupFile.exportedAt` é o instante do upload, então duas execuções no mesmo
   dia geram conteúdo diferente — aceitável, porque o destino é um snapshot, não
   um histórico.
9. Bateria/background: o worker não pode exigir `DEVICE_IDLE_NOT_BATTERY_SAVER`
   nem `requireNetwork` de forma que nunca dispare em rede paga; documentar a
   escolha.
10. Reset de fábrica / reinstalar: permissões persistidas somem com os dados —
    esperado, e o fluxo de restore por `deviceId` (H1-1) continua sendo o caminho.
11. Nenhum segredo no arquivo (regra 12 da H3-4) — automático não muda conteúdo.

## Critérios de aceite

1. **Dado** que o usuário exporta manualmente para o Drive e liga "Backup
   automático", **então** a URI escolhida é persistida e o destino aparece em
   Ajustes.
2. **Dado** o automático ligado e uma sessão nova gravada, **quando** o worker
   roda, **então** `lastAutoBackupAt` muda e o arquivo do provedor é substituído.
3. **Dado** o automático ligado e o app reiniciado, **então** o agendamento
   sobrevive (testar com `WorkManager` instance de teste).
4. **Dado** permissão revogada, **quando** o worker roda, **então** o automático
   se desliga, mostra a mensagem e **nenhum** dado local é alterado.
5. **Dado** sem rede, **então** `retry`, nenhum estado de erro permanente antes da
   última tentativa.
6. **Dado** que o worker termina, **então** nenhum `importBackup`/`replaceAllData`
   foi invocado — provado por teste que falha se for.
7. **Dado** o interruptor desligado, **então** nenhum worker com o nome fixo está
   agendado.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/backup/BackupSchedulerTest.kt` | **Novo.** `liga_agenda_unico_worker`, `desliga_cancela`, `reagende_nao_duplica` |
| `app/src/test/java/com/treinos/app/data/backup/AutoBackupWorkerTest.kt` | **Novo.** `escreve_no_uri`, `permicao_revogada_desliga`, `sem_rede_retries`, `nunca_importa` |
| `app/src/test/java/com/treinos/app/ui/BackupFlowUiTest.kt` | `interruptor_mostra_destino_e_ultimo_upload` |

`androidx.work:work-testing` em `testImplementation`, versão fixa.

## Verificação manual

1. Ligue o automático num destino do Drive; complete um treino; force a execução
   do worker (`adb shell cmd jobscheduler run` ou `WorkManager` test harness) e
   confira o arquivo na nuvem.
2. Revogue o acesso (remova o app da conta do Drive) e confirme o desligamento
   com a mensagem, dados intactos.
3. Modo avião por 10 min; confirme retry sem erro permanente.
4. Reinicie o aparelho; confirme que o agendamento continua e `lastAutoBackupAt`
   não foi perdido.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Perda silenciosa de dados por overwrite:** dois aparelhos, um arquivo, último
  ganha. Mitigação: texto explícito em Ajustes e nenhuma importação automática.
- **Permissão que evapora:** provedores revogam; o custo de detectar tarde é um
  backup que não acontece há semanas. Mitigação: `lastAutoBackupAt` visível e
  erro visível — se o usuário vir data antiga, é sinal.
- **Dependência nova + work em background:** aumenta superfície de bugs de
  ciclo de vida e consumo; é o motivo do "W".
- **Rollback:** desligar o interruptor, cancelar o worker e esquecer a URI. Como
  nada é importado automaticamente, nenhum dado do usuário depende do recurso.
