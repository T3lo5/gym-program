# H3-8 — Backup na nuvem do usuário via SAF

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H3-8` |
| MoSCoW | `C` |
| Spec aprovada em | `pendente` (proposta de 2026-11-05) |
| Implementação | `pendente` |
| Depende de | H1-4/H1-7 (fluxo de arquivo), H3-6 (conteúdo do backup) |

## Objetivo

Você quer guardar o backup no OneDrive/Google Drive/Dropbox que já é seu. Este
item não adiciona SDK nem OAuth: ele valida, conserta e documenta o caminho que o
Storage Access Framework já oferece, para que "salvar na nuvem" seja uma
escolha visível e não uma sorte de fabricante.

## Por que é quase zero código

`CreateDocument("application/json")`
([TreinosApp.kt:93](file:///mnt/Arquivos/github-personal/test-personal/app/src/main/java/com/treinos/app/ui/TreinosApp.kt#L93))
e `OpenDocument` (`:116`) delegam ao sistema, que elenca os *document providers*
instalados. Um provedor de nuvem aparece lá como pasta comum. O app não sabe, não
autoriza e não guarda credencial de ninguém — o princípio "local-first, a nuvem é
opcional" (§3 do ROADMAP) sobrevive intacto. SDK de nuvem é a única alternativa
que **quebra** esse princípio.

## Fora de escopo

- Upload/backup automático, agendado ou em rede (H3-9).
- SDK, OAuth, token ou app client ID de qualquer provedor dentro do app.
- Listar arquivos na nuvem, escolher versão, histórico de revisões do provedor.
- Criptografia ou senha no arquivo (ainda fora, como H1-4/H1-7).
- Provar que todo provedor funciona: a verificação cobre Drive, OneDrive e
  Dropbox, que são os três mais comuns no aparelho brasileiro.
- `ACTION_SEND` / share sheet (poderia ser um item à parte; aqui não é).

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Texto de ajuda do card "Backup e restauração (JSON)" explica que dá para escolher uma pasta do Drive/OneDrive/Dropbox |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | `importLauncher` (`:116`) ganha leitura com feedback: o estado `BackupImportState.Reading` precisa aparecer enquanto um provedor remoto baixa o arquivo |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Nenhum estado novo além do que `BackupImportState` (`Reading/Ready/Error`) já cobre |
| `docs/MANUAL.md` | §6.4 passa a descrever o caminho da nuvem e o que fazer se o provedor falhar |
| `README.md` | Uma linha no fluxo de backup |
| `app/src/test/java/com/treinos/app/ui/BackupFlowUiTest.kt` | Mensagem de ajuda visível; estado "lendo" mostrado antes do resultado |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Contrato

Nenhum formato novo. O que muda é comportamento observável:

| Ação | Local | Nuvem via SAF |
|---|---|---|
| Exportar | grava no arquivo escolhido | provedor faz o upload depois; o app só escreve no stream |
| Importar | lê na hora | o provedor baixa o arquivo durante `openInputStream` |
| Cancelar o seletor | nenhuma escrita | nenhuma escrita |
| Falha de rede no meio da escrita | n/a | exceção → mensagem; `cacheDir` mantém o JSON gerado |

Regras:

1. Exportar continua gerando primeiro em `cacheDir` e copiando para o URI
   escolhido (comportamento atual, [TreinosApp.kt:104-108](file:///mnt/Arquivos/github-personal/test-personal/app/src/main/java/com/treinos/app/ui/TreinosApp.kt#L104))
   — é isso que impede um provedor lento de corromper o backup anterior.
2. Uma escrita que falha no meio pode deixar um arquivo parcial **no provedor**;
   a mensagem deve orientar a exportar de novo, e o nome sugerido
   `treinos-backup-yyyyMMdd-HHmm.json` (`:294`) já ajuda a não sobrescrever o
   anterior sem querer.
3. Importar de um provedor remoto pode demorar; a UI não pode parecer travada. O
   estado `Reading` já existe
   ([AppViewModel.kt:1139-1144](file:///mnt/Arquivos/github-personal/test-personal/app/src/main/java/com/treinos/app/ui/AppViewModel.kt#L1139))
   — este item garante que ele é renderizado durante a leitura, não só na validação.
4. A guarda de tamanho da H3-6 é obrigatória aqui: ler o stream inteiro de um
   arquivo remoto de tamanho desconhecido é o pior caso.
5. Provedor que não aceita escrita (DocumentProvider somente leitura) → mensagem
   de erro, sem apagar `pendingBackupPayload` nem o estado anterior.
6. O texto de ajuda precisa mencionar que o arquivo contém o histórico completo e
   — depois da H3-6 — peso, altura e sexo: nuvem de terceiros não é o app.
7. Nada no app pede, guarda ou mostra credencial de provedor. Se aparecer um
   `client_id` em `app/`, está errado.

## Critérios de aceite

1. **Dado** um app com OneDrive configurado no aparelho, **quando** exporta,
   **então** o seletor mostra o OneDrive e o arquivo aparece na conta.
2. **Dado** um backup na nuvem, **quando** importa pelo mesmo app, **então** a
   pré-visualização aparece e o "Substituir tudo" funciona igual ao de arquivo
   local.
3. **Dado** que o seletor é cancelado, **então** nenhum arquivo é criado ou
   alterado, em qualquer provedor.
4. **Dado** um provedor lento, **quando** importa, **então** a UI mostra o estado
   de leitura em vez de parecer travada.
5. **Dado** que a escrita falha no meio, **então** a mensagem é a de erro já
   existente (`:110-112`) e os dados locais não mudam.
6. **Dado** o card de backup em Ajustes, **então** o texto de ajuda cita salvar na
   nuvem do usuário e avisa do conteúdo do arquivo.
7. **Dado** o diff deste item, **então** nenhuma dependência nova existe em
   `app/build.gradle.kts`.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/ui/BackupFlowUiTest.kt` | `texto_ajuda_menciona_nuvem`, `estado_lendo_aparece_antes_do_resultado`, `erro_de_escrita_mantem_estado` |

Comportamento de provedor real não é testável em JVM — daí a verificação manual.

## Verificação manual (o coração deste item)

1. Instale OneDrive e Google Drive no aparelho ou emulador com Play Services.
2. Ajustes → Exportar backup → escolha o provedor → salve numa pasta da conta.
3. Confirme no site do provedor que o arquivo chegou, íntegro e com o nome
   sugerido.
4. Zere os dados locais (importe um backup vazio) e importe do OneDrive; confirme
   pré-visualização + "Substituir tudo".
5. Repita com Dropbox.
6. Modo avião: tente importar de um provedor que precisa baixar; confirme a
   mensagem de erro e que nada mudou.
7. Cancele o seletor no meio; confirme que nenhum arquivo novo apareceu.
8. Aparelho com 3 provedores e sem nenhum: confirme que o fluxo local continua
   exatamente como hoje.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Provedores são diferentes entre si:** alguns reportam tamanho, outros não;
  alguns recusam `openOutputStream` com `mode` default. A H1-7 já previa
  "permissões e URIs variam por fabricante". Mitigação: o guard de tamanho com
  leitura limitada (H3-6) e as mensagens já existentes.
- **Falsa sensação de privacidade:** o arquivo vai para a conta do usuário, não
  para o app. Mitigação: texto de Ajustes e MANUAL §6.4/§7.
- **Rollback:** remover o texto de ajuda e o feedback de leitura. Nada do formato
  do backup muda, então nenhum arquivo deixa de funcionar.
