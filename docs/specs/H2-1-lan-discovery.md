# H2-1 — Descoberta LAN automática (mDNS/NSD)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H2-1` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `concluída em 2026-11-04` (gate `./scripts/verify.sh` verde; verificação NSD em device pendente) |
| Depende de | backend mDNS (já existe) |

## Objetivo

Encontrar o backend na mesma rede Wi-Fi e conectar com um toque, sem o usuário
precisar digitar IP:porta. O campo manual continua como fallback.

## Fora de escopo

- Descoberta entre sub-redes/VLANs ou fora da LAN.
- Funcionar através do Docker bridge (mDNS não atravessa; ver §Riscos).
- Descoberta de múltiplos backends remotos/cloud.
- Autenticação (H3-4) ou pareamento.
- iOS/desktop.

## Arquivos e símbolos que devem mudar

| Arquivo | Mudança |
|---|---|
| `app/src/main/AndroidManifest.xml` | `ACCESS_LOCAL_NETWORK` declarado (obrigatório a partir do Android 15; `targetSdk` segue 34); `@xml/network_security_config` já estava ligado no `<application>` |
| `app/src/main/java/com/treinos/app/data/discovery/BackendDiscoveryManager.kt` | `NsdManager` + resolve com timeout de 5 s, leitura do TXT `version`, erros legíveis, limpeza em `stop()` |
| `app/src/main/java/com/treinos/app/data/discovery/DiscoveredBackend.kt` | modelo `name`, `host`, `port`, `url`, `version`, `endpoint`, `isVersionKnown` |
| `app/src/main/java/com/treinos/app/data/discovery/BackendConnector.kt` | **Novo.** lógica pura da conexão: sonda `/health` e **só** salva a URL em 200 (falha mantém a anterior) |
| `app/src/main/java/com/treinos/app/data/discovery/HttpBackendHealthChecker.kt` | **Novo.** sonda real com OkHttp e timeout de 5 s |
| `app/src/main/java/com/treinos/app/data/prefs/AppPreferences.kt` | passa a implementar `BackendUrlStore` (costura para a lógica pura) |
| `app/src/main/java/com/treinos/app/TreinosApplication.kt` | manager construído no `AppContainer` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | estados de descoberta/conexão e `connectDiscoveredBackend` real |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | passa estados/callbacks e para a busca ao sair de Ajustes |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | seção "Backend na rede local": lista, "Conectar", estados e avisos |
| `app/src/test/java/com/treinos/app/data/discovery/DiscoveredBackendTest.kt` | URL, dedupe, validação, versão desconhecida |
| `app/src/test/java/com/treinos/app/data/discovery/BackendConnectorTest.kt` | **Novo.** salvar/não salvar conforme o `/health` |

Não edite `shared/` nem `com.treinos.shared/` (legado).

## Modelo de dados / contrato

O backend já publica:

```
service type: _treinos._tcp.
instance:     TreinosBackend._treinos._tcp.local.
TXT:          version=1.0.0, api=v1, model=<modelo>
port:         3000
```

App:

```kotlin
data class DiscoveredBackend(
    val name: String,
    val host: String,
    val port: Int,
    val url: String, // http://host:port
)

class BackendDiscoveryManager(context: Context) {
    fun start(onFound: (DiscoveredBackend) -> Unit, onError: (String) -> Unit)
    fun stop()
}
```

Regras:

- Usar `android.net.nsd.NsdManager` (sem JmDNS/dependência nova).
- `discoverServices("_treinos._tcp.", NsdManager.PROTOCOL_DNS_SD, ...)`.
- Resolver cada serviço com timeout de 5 s; ignorar host vazio/porta inválida.
- Deduplicar por `host:port`; atualizar o nome se o mesmo endpoint reaparecer.
- Não iniciar descoberta automaticamente ao abrir o app; só quando o usuário
  tocar em “Buscar backends” (economia de bateria).
- Parar a descoberta em `onCleared()` do ViewModel e ao sair da tela.
- “Conectar” testa `GET /health` antes de salvar. Só salva em
  `AppPreferences.backendUrl` se o health retornar 200.
- Fallback manual permanece: o campo de URL e “Testar conexão” continuam
  funcionando.
- Android 16+ (API 36): declarar/checar `ACCESS_LOCAL_NETWORK`; em versões
  anteriores, não solicitar. O `targetSdk` atual é 34; manter o código
  compatível com os dois cenários.
- Manifest deve referenciar `@xml/network_security_config` (hoje o arquivo
  existe mas não está ligado no `<application>`).

## Regras e casos de borda

1. Nenhum backend encontrado → estado vazio com dica “ligue o backend no PC e
   confirme que está na mesma Wi-Fi”.
2. Backend encontrado mas health falha → erro “encontrado, mas não respondeu”;
   não salvar.
3. Dois anúncios do mesmo host/porta → um item só.
4. Serviço sem TXT `version` → aceitar, mas mostrar aviso de versão desconhecida.
5. Wi-Fi desligado/sem rede → erro claro, sem crash.
6. Usuário conecta e o backend muda de IP → nova busca encontra o novo; a URL
   antiga continua salva até conectar.
7. Descoberta em andamento → botão vira “Parar” e não permite duplicar.
8. `NsdManager` indisponível/erro de sistema → mensagem e fallback manual.
9. O Docker Compose atual tem `MDNS_ENABLED=false`; a descoberta funciona com o
   backend rodando nativo (`python`/`uvicorn`) ou com `network_mode: host` em
   Linux, que é opcional nesta entrega.

## Critérios de aceite

1. **Dado** backend nativo com `MDNS_ENABLED=true` na mesma Wi-Fi, **quando** o
   usuário toca em “Buscar backends”, **então** vê “TreinosBackend” com o
   endereço `host:porta`.
2. **Dado** o resultado encontrado, **quando** toca em “Conectar”, **então** o
   app testa `/health`, salva a URL e mostra sucesso.
3. **Dado** um backend que não responde, **quando** conecta, **então** vê erro e
   a URL anterior não é alterada.
4. **Dado** que o usuário sai da tela, **então** a descoberta para.
5. **Dado** que o usuário prefere IP manual, **então** o fluxo atual continua
   funcionando sem regressão.
6. **Dado** dois anúncios iguais, **então** aparece um único item.

## Testes a criar

| Arquivo | Testes |
|---|---|
| `app/src/test/java/com/treinos/app/data/discovery/DiscoveredBackendTest.kt` | `monta_url_http`, `ignora_porta_invalida`, `ignora_host_vazio`, `deduplica_por_host_e_porta`, `aceita_ipv4_e_ipv6_entre_colchetes`, `sem_txt_de_versao_marca_desconhecida`, `deduplica_preservando_versao_e_nome` |
| `app/src/test/java/com/treinos/app/data/discovery/BackendConnectorTest.kt` | `salva_url_quando_health_responde`, `nao_salva_quando_health_nao_responde`, `mantem_url_anterior_quando_a_sonda_falha`, `recusa_endereco_invalido_sem_sondar_a_rede`, `normaliza_host_com_colchetes_antes_de_salvar` |

Testes de `NsdManager` exigem device/emulador; a verificação é manual.

## Verificação manual

> **Status (2026-11-04):** o gate passou, mas os passos abaixo exigem celular +
> backend nativo na mesma Wi-Fi e **não** foram executados. A entrega está
> verificada por código/teste, não por smoke test em device.

1. Rode o backend sem Docker: `cd back && uvicorn app.main:app --host 0.0.0.0 --port 3000` com `MDNS_ENABLED=true`.
2. No celular, mesma Wi-Fi, abra Ajustes → “Buscar backends”.
3. Conecte e confirme `GET /health` no app.
4. Desligue o backend e tente conectar; confirme o erro.
5. Teste o campo manual e “Testar conexão” para garantir o fallback.
6. Saia da tela e confirme (logs) que a descoberta parou.

## Comando de verificação

```bash
./scripts/verify.sh
```

## Riscos e rollback

- **Docker bridge:** mDNS não atravessa; documentar que a descoberta é para
  backend nativo. Não prometer no README que funciona no Compose atual.
- **Android 16:** permissão de rede local pode exigir fluxo runtime; manter
  fallback manual e testar em preview.
- **Bateria:** descoberta só sob demanda e com `stop()` garantido.
- **Rollback:** remover o manager e a seção da UI; a URL manual continua sendo a
  fonte de verdade.
