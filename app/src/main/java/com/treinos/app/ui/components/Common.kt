package com.treinos.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treinos.app.R
import com.treinos.app.ui.exerciseContentDescription

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp).semantics { heading() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val chipSelectedDesc = stringResource(R.string.chip_selected)
        val chipNotSelectedDesc = stringResource(R.string.chip_not_selected)
        options.forEach { (value, label) ->
            FilterChip(
                selected = value in selected,
                onClick = { onToggle(value) },
                label = { Text(label) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics {
                        stateDescription = if (value in selected) chipSelectedDesc else chipNotSelectedDesc
                    },
            )
        }
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Card de métrica do histórico. Altura variável para não cortar com fonte grande. */
@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Card(modifier = modifier.wrapContentHeight().semantics(mergeDescendants = true) { }) {
        Column(
            modifier = Modifier
                .wrapContentHeight()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val loadingDesc = stringResource(R.string.common_loading)
    Box(
        modifier = modifier.fillMaxWidth().padding(24.dp).semantics(mergeDescendants = true) {
            contentDescription = loadingDesc
        },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Erro persistente e acionável (H3-29): título com a ação que falhou, mensagem
 * curta e "Tentar de novo" quando a ação pode ser repetida. O visual é
 * exclusivo de erro — sucesso continua no snackbar.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp).semantics(mergeDescendants = true) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        onRetry?.let {
            Button(onClick = it, modifier = Modifier.minimumInteractiveComponentSize()) { Text(stringResource(R.string.common_retry)) }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .semantics(mergeDescendants = true) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke()
    }
}

fun exerciseHeadingDescription(name: String): String = exerciseContentDescription(name)

/**
 * Botão ocupado padrão (H3-32): spinner de 18 dp na cor do conteúdo
 * ([LocalContentColor]) e texto de progresso, desabilitado enquanto [busy].
 * Substitui as cópias manuais de `Row + CircularProgressIndicator + Text`.
 */
@Composable
fun BusyButton(
    busy: Boolean,
    idleText: String,
    busyText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.minimumInteractiveComponentSize(),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(10.dp))
            Text(busyText)
        } else {
            Text(idleText)
        }
    }
}

/**
 * Mensagem de status com semântica visual (H3-32): sucesso em `primary` com
 * check, erro em `error` com alerta — antes ambas eram idênticas em `primary`.
 */
@Composable
fun StatusText(
    text: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
    announce: Boolean = false,
) {
    val color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            if (announce) liveRegion = LiveRegionMode.Polite
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (isSuccess) Icons.Default.Check else Icons.Default.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

/**
 * Convenção de classificação das mensagens de Ajustes (H3-32): só os prefixos
 * de sucesso conhecidos são verdes; qualquer outra mensagem (falha de rede,
 * dado ausente, validação) é erro. Pura para poder ser testada em JVM.
 */
fun statusIsSuccess(message: String): Boolean =
    SUCCESS_PREFIXES.any { message.startsWith(it) }

private val SUCCESS_PREFIXES = listOf(
    "Conectado",
    "Enviado:",
    "Restaurado:",
    "Backup importado",
    "Catálogo atualizado",
)

/**
 * H3-47: diálogo de confirmação para ações destrutivas ou que substituem dados.
 * Reuso do mesmo padrão (título descritivo, mensagem de impacto, Cancelar neutro
 * e ação rotulada explicitamente) para evitar variação entre telas.
 *
 * - `onConfirm` é acionado apenas após o toque explícito; o `onDismiss` (ou
 *   toque fora) é sempre neutro.
 * - `confirmLabel` é um `stringResource`; o chamador passa o rótulo para que
 *   o diálogo seja testado sem depender de um estado interno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = stringResource(R.string.common_cancel),
    confirmTint: Color = MaterialTheme.colorScheme.error,
    isDestructive: Boolean = true,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(confirmLabel, color = if (isDestructive) confirmTint else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Text(dismissLabel)
            }
        },
    )
}
