# H2-1 — Descoberta LAN automática (mDNS/NSD)

## Status

| Campo | Valor |
|---|---|
| Item do roadmap | `H2-1` |
| MoSCoW | `S` |
| Spec aprovada em | `2026-11-03` |
| Implementação | `pendente` |
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
| `app/src/main/AndroidManifest.xml` | `networkSecurityConfig` e permissão de rede local quando aplicável |
| `app/src/main/java/com/treinos/app/data/discovery/BackendDiscoveryManager.kt` | **Novo.** `NsdManager` + resolve |
| `app/src/main/java/com/treinos/app/data/discovery/DiscoveredBackend.kt` | **Novo.** modelo `name`, `host`, `port`, `url` |
| `app/src/main/java/com/treinos/app/TreinosApplication.kt` | Construir o manager no `AppContainer` |
| `app/src/main/java/com/treinos/app/ui/AppViewModel.kt` | Estados de descoberta e conexão |
| `app/src/main/java/com/treinos/app/ui/TreinosApp.kt` | Passar estados/callbacks |
| `app/src/main/java/com/treinos/app/ui/screens/SettingsScreen.kt` | Lista de backends + “Conectar” + estados |
| `app/src/test/java/com/treinos/app/data/discovery/DiscoveredBackendTest.kt` | **Novo.** URL, dedupe, validação |

Não edite `shared/`, `wear/` nem `com.treinos.shared/`.

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
| `app/src/test/java/com/treinos/app/data/discovery/DiscoveredBackendTest.kt` | `monta_url_http`, `ignora_porta_invalida`, `ignora_host_vazio`, `deduplica_por_host_e_porta`, `aceita_ipv4_e_ipv6_entre_colchetes` |

Testes de `NsdManager` exigem device/emulador; a verificação é manual.

## Verificação manual

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
