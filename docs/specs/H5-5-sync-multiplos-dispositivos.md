# H5-5 — Sync entre múltiplos dispositivos sem conta

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H5-5` |
| MoSCoW | `C` |
| Versão alvo | `Horizon 5` |
| Spec aprovada em | `2026-11-18` |
| Implementação | `concluída em 2026-11-18` |
| Depende de | H3-4, H5-1 |

## Objetivo

Permitir que celular e possíveis aparelhos adicionais compartilhem o mesmo conjunto de treinos sem login ou conta, usando um namespace e segredo criados pelo usuário.

## Fora de escopo

- OAuth, Google Sign-In, Apple Account ou cadastro.
- Compartilhamento com outra pessoa por padrão.
- Permissões granulares por dispositivo.
- Sincronização de mídia privada em outra conta cloud.
- Recuperação impossível sem a frase/código de espaço.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | Guardar `syncSpaceId`, `syncSecret` e status de participação. |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Criar/escolar vínculo de outro aparelho via código curto ou QR local. |
| `app/src/main/java/com/treinos/app/data/remote/BackendAuth.kt` | Resolver espaço secreto e cabeçalho Bearer sem incluir segredo no URL/log. |
| `back/app/auth.py` | Autorizar o namespace somente pelo secreto de sync. |
| `back/app/database.py` | Namespace por `syncSpaceId`, separar mutações e device registry. |
| `back/app/main.py` | Validar `syncSpaceId` em endpoints de sync gerados. |
| `app/src/test/java/com/treinos/app/data/prefs/SyncSpaceTest.kt` | Geração, validação, redigido e rotação. |
| `back/tests/test_sync_spaces.py` | Isolamento, join, revogação e vazamento cruzado. |

## Modelo de dados / contrato

```text
sync_space:
  id: UUID
  secretHash: hash(salt + secret)
  createdAt: ISO
  revokedAt: ISO|null

Device:
  deviceId: UUID local
  syncSpaceId: UUID
  name: string curto opcional
  firstSeenAt: ISO
  lastSeenAt: ISO

Cabeçalho:
  Authorization: Bearer <API_TOKEN>       (H3-4; backend local sem token aceita vazio)
  X-Sync-Secret: <syncSecret>
  X-Sync-Space: <syncSpaceId>

Secret:
  gerado localmente com 128 bits aleatórios
  mostrado uma única vez em QR/código curto
  nunca enviado ao backup JSON
```

## Regras e casos de borda

- Criar espaço não migra dados automaticamente; o usuário confirma sincronização inicial.
- Entrar em espaço existente exige confirmação e mantém cópia local até o merge terminar.
- Dois dispositivos usam `deviceId` distintos dentro do mesmo espaço.
- Revogar segredo impede novos mutations, mas não apaga dados já baixados nos aparelhos.
- Alteração de espaço limpa somente credenciais remotas, nunca Room local.
- Código expirado ou inválido mostra erro sem vazamento do segredo.
- Backend antigo não reconhece `X-Sync-Space` e não participa deste fluxo.
- Logs nunca registram segredo ou QR.

## Critérios de aceite

1. **Dado** espaço criado em um celular, **quando** outro é vinculado, **então** recebe as mutações do mesmo namespace.
2. **Dado** segredo compartilhado errado, **quando** sincroniza, **então** recebe 401.
3. **Dado** dois espaços diferentes, **quando** cada um sincroniza, **então** nenhum recebe dados do outro.
4. **Dado** revogação, **quando** o dispositivo antigo tenta sync, **então** falha sem apagar o banco local.
5. **Dado** join cancelado no meio, **quando** o app reabre, **então** os dados locais permanecem intactos e o vínculo não fica parcial.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `back/tests/test_sync_spaces.py` | `test_space_isolation`, `test_join_secret_valid`, `test_join_secret_invalid`, `test_revoked_secret`, `test_device_registry` |
| `app/src/test/java/com/treinos/app/data/prefs/SyncSpaceTest.kt` | `create_space`, `join_requires_confirmation`, `cancel_join_preserves_local`, `secret_never_backed_up` |

## Verificação manual

1. Criar espaço no aparelho A e vincular o aparelho B.
2. Confirmar que treino criado em A aparece em B.
3. Tentar segredo errado e conferir 401 sem alteração local.
4. Revogar o espaço e confirmar que o aparelho antigo para de sincronizar sem apagar dados.
5. Conferir backup JSON sem segredo ou ID de espaço.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- Compartilhar um segredo é a autenticação deste modelo; vazamento concede acesso aos dados.
- Perder o segredo bloqueia vinculação futura, mas não precisa apagar o aparelho atual.
- QR/código curto não substitui HTTPS no backend remoto.
- Reverter o recurso mantém o uso de `deviceId` atual isolado.
