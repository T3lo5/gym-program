# H3-10 — Regras de backup automático do Android ligadas e corretas

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-10` |
| MoSCoW | `S` |
| Spec aprovada em | `pendente` (proposta de 2026-11-05) |
| Implementação | `pendente` |
| Depende de | nada — e é **pré-requisito** da H3-4 |

## Objetivo

Hoje o app já tem um caminho de nuvem que ninguém escolheu: `allowBackup="true"`
com regras de backup que existem no repositório mas **não estão ligadas**, e um
caminho de banco apontando para um arquivo que não existe. Este item decide o
comportamento em vez de herdar o default do sistema.

## Contexto verificado

- `android:allowBackup="true"`
  ([AndroidManifest.xml:12](file:///mnt/Arquivos/github-personal/test-personal/app/src/main/AndroidManifest.xml#L12)),
  sem `fullBackupContent` nem `dataExtractionRules`.
- `app/src/main/res/xml/backup_rules.xml` e `data_extraction_rules.xml` existem,
  mas nenhuma linha do manifest os referencia → hoje o sistema aplica o default,
  que inclui os dados do app.
- Os dois arquivos apontam `<include domain="database" path="treinos_database"/>`,
  mas o banco real chama **`treinos.db`**
  ([AppDatabase.kt:55](file:///mnt/Arquivos/github-personal/test-personal/app/src/main/java/com/treinos/app/data/local/AppDatabase.kt#L55)).
  Ligar as rules **como estão** excluiria o Room do backup em silêncio, porque
  uma lista de `<include>` que não casa com nada não inclui nada.
- `deviceId`, `backendUrl` e (a partir da H3-6/H3-7) perfil moram em
  `SharedPreferences` (`treinos_prefs`), que as rules incluem como `path="."`.
- A H3-4 pretende pôr `apiToken` em SharedPreferences com a regra "não incluir no
  backup JSON" (regra 12) — mas não menciona este segundo vetor, que é o backup
  da plataforma.

## Fora de escopo

- Auto-backup em nuvem do usuário via SAF (H3-8) e automático (H3-9) — são
  caminhos de arquivo, não o `BackupAgent` do sistema.
- `adb backup`/`restore`, `bmgr`, ferramentas de fabricante.
- Criptografia no repouso ou `EncryptedSharedPreferences` (decisão própria, quando
  a H3-4 chegar).
- Qualquer migração de dados: isto muda **política**, não conteúdo.
- `wear/` — o manifest do relógio tem o mesmo `allowBackup="true"` e fica para
  uma decisão do roadmap do relógio.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/AndroidManifest.xml` | `android:fullBackupContent="@xml/backup_rules"` e `android:dataExtractionRules="@xml/data_extraction_rules"` |
| `app/src/main/res/xml/backup_rules.xml` | conteúdo revisado para o nome real (`treinos.db`) e política explícita |
| `app/src/main/res/xml/data_extraction_rules.xml` | idem, separando `cloud-backup` de `device-transfer` |
| `docs/MANUAL.md` | §7 Segurança e privacidade: o que o backup do Android faz e o que o app considera o caminho oficial de restore |
| `README.md` | nota de privacidade se o comportamento mudar de "tudo" para "nada" |

## Modelo de dados / contrato

Política proposta (**precisa da sua aprovação — é a decisão deste item**):

| Dado | `cloud-backup` | `device-transfer` | Motivo |
|---|---|---|---|
| `treinos.db` (+ `-wal`, `-shm`) | **excluir** | excluir | Room com WAL em backup automático é caminho clássico de restauração corrompida; os dois canais oficiais de restore já são JSON (H1-4) e snapshot do backend (H2-3) |
| `treinos_prefs.xml` (deviceId, backendUrl, perfil) | **excluir** | **incluir** | nuvem de terceiro não precisa do nosso identificador; transferência de aparelho é migração iniciada pelo usuário |
| Catálogo/mídias em cache | excluir | excluir | regenerável (H1-3) |

```xml
<!-- backup_rules.xml (Android 11 e anterior) -->
<full-backup-content>
    <exclude domain="database" path="treinos.db" />
    <exclude domain="database" path="treinos.db-wal" />
    <exclude domain="database" path="treinos.db-shm" />
    <exclude domain="sharedpref" path="treinos_prefs.xml" />
</full-backup-content>

<!-- data_extraction_rules.xml (Android 12+) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="treinos.db" />
        <exclude domain="database" path="treinos.db-wal" />
        <exclude domain="database" path="treinos.db-shm" />
        <exclude domain="sharedpref" path="treinos_prefs.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="treinos.db" />
        <exclude domain="database" path="treinos.db-wal" />
        <exclude domain="database" path="treinos.db-shm" />
    </device-transfer>
</data-extraction-rules>
```

Regras:

1. Prefere-se `<exclude>` a `<include>`, porque `include` cria a armadilha que
   encontramos: caminho errado e nada é coberto, silenciosamente.
2. Os nomes devem bater com o arquivo real: `treinos.db` e `treinos_prefs.xml`
   (`getSharedPreferences("treinos_prefs", ...)` em
   [AppPreferences.kt:21](file:///mnt/Arquivos/github-personal/test-personal/app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt#L21)).
   Um teste garante isso (abaixo).
3. Se a H3-4 criar um arquivo de preferências separado para o token, ele é
   excluído dos dois domínios — e a H3-4 passa a referenciar este item.
4. O restore oficial continua sendo o backup JSON (H1-4/H1-7). Reduzir o backup
   do Android **não** remove proteção de dados; remover um caminho não testado, sim.
5. `allowBackup` continua `true`; nada no app depende de `BackupAgent`.

## Regras e casos de borda

1. `adb shell bmgr backupnow <pkg>` não envia banco nem prefs.
2. Restauração em aparelho novo via Google One: app abre com banco vazio e
   `deviceId` novo — e o usuário usa "Restaurar de outro aparelho" (H1-1) ou o
   JSON. Nunca meio-dado.
3. Transferência de aparelho: prefs vão, o `deviceId` pode se repetir em dois
   aparelhos vivos. Isso é escolha, não bug — documentar em MANUAL §7, porque afeta
   a semântica de snapshot por `deviceId` da H2-3.
4. Android 11 e anterior só lê `fullBackupContent`; Android 12+ só lê
   `dataExtractionRules`. Os dois arquivos precisam existir e concordar.
5. Se o nome do banco mudar no futuro, o teste falha — objetivo, não incidental.
6. Nada é criptografado aqui; se o produto quiser criptografia de backup, é outra
   spec.

## Critérios de aceite

1. **Dado** o manifest, **então** `fullBackupContent` e `dataExtractionRules`
   apontam para os dois XMLs existentes.
2. **Dado** os dois XMLs, **então** o caminho do banco é `treinos.db` (não
   `treinos_database`), conferido por teste contra a constante de `AppDatabase`.
3. **Dado** um `bmgr backupnow`, **então** nada do banco nem de `treinos_prefs`
   sobe.
4. **Dado** uma reinstalação em aparelho novo, **então** o app abre com dados
   vazios (não corrompidos) e o `deviceId` é gerado novo.
5. **Dado** o `MANUAL.md` §7, **então** ele descreve o backup do Android como
   caminho **não** oficial e aponta JSON + snapshot.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/local/BackupRulesTest.kt` | **Novo.** `nome_do_banco_bate_com_as_rules`, `prefs_nao_vao_para_a_nuvem`, `wal_e_shm_excluidos` — lê os XMLs de `app/src/main/res/xml/` e compara com a constante do `AppDatabase` e com o nome do arquivo de prefs |

Este teste é a prova de que a armadilha do `treinos_database` não volta. Não há
teste Robolectric para `bmgr` — o item 3 e 4 da lista de aceite são de verificação
manual.

## Verificação manual

1. `adb shell bmgr enabled`, `adb shell bmgr backupnow com.treinos.app`, depois
   `adb shell bmgr restore <pkg>` e confirme ausência de dados.
2. Em Android 12+, confira em Configurações → Google → Cópia de segurança que o
   app não envia banco/prefs.
3. Desinstale, reinstale e confirme: app limpo + mensagem de Ajustes apontando o
   caminho oficial de restore.
4. Rode o teste novo e quebre de propósito o nome no XML para confirmar que ele
   pega.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Regressão percebida:** quem contava com o backup do Google para não perder
  dados passa a contar só com o JSON. Mitigação: H3-6 primeiro, texto em Ajustes e
  MANUAL, e manter `device-transfer` para prefs.
- **Colisão de `deviceId` em dois aparelhos** pela transferência de aparelho:
  documentado; se indesejado, mover `treinos_prefs.xml` para o `device-transfer`
  também é uma linha.
- **Rollback:** remover os dois atributos do manifest. As rules voltam a ser
  inertes, como hoje, e nada do conteúdo do app muda.
