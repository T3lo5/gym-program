package com.treinos.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.treinos.app.R

/**
 * Estado de uma linha de permissão do [PermissionsCard]: o que falta e o
 * texto do botão que pede (diálogo do sistema ou tela de Configurações).
 */
data class PermissionRowState(
    val label: String,
    val granted: Boolean,
    val actionLabel: String?,
    val onAction: () -> Unit = {},
)

/**
 * Card "Permissões" (pedido em tela): mostra só o que falta, com o botão que
 * abre o diálogo/tela certa — o usuário deixa de adivinhar o que conceder em
 * Configurações. Notificações e apps desconhecidos vêm do sistema; Health
 * Connect entra quando o provedor está disponível e falta permissão.
 */
@Composable
fun PermissionsCard(
    rows: List<PermissionRowState>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pendentes = rows.filter { !it.granted && it.actionLabel != null }
    if (pendentes.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.permissions_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            pendentes.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = row.onAction,
                        modifier = Modifier.minimumInteractiveComponentSize(),
                    ) {
                        Text(row.actionLabel.orEmpty())
                    }
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text(stringResource(R.string.permissions_dismiss))
            }
        }
    }
}
