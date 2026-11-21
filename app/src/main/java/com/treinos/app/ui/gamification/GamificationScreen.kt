package com.treinos.app.ui.gamification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import com.treinos.app.R
import com.treinos.app.ui.components.ScreenTopBar
import com.treinos.app.ui.components.SectionTitle

/** Ícone do badge por ID (usa um único ícone core para não adicionar extended). */
private fun badgeIcon(id: String) = Icons.Default.Favorite

private fun Int.toColor(): Color = Color(this)

@Composable
fun GamificationScreen(
    state: UserGamificationState,
    onBack: () -> Unit,
) {
    val nivelDesc = "Nível ${state.level} — ${state.xp} XP"
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTopBar(title = stringResource(com.treinos.app.R.string.screen_gamification_title), onBack = onBack)

        // Nível e barra de XP.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nível ${state.level}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = state.level.toColor(),
                )
                Text(
                    text = "${state.xp} XP",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val progress = if (state.xpToNext <= 0) 1f else (state.xp.toFloat() / (state.xp + state.xpToNext).coerceAtLeast(1)).coerceIn(0f, 1f)
                CircularProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${state.xp} / ${state.xp + state.xpToNext} XP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionTitle(text = stringResource(com.treinos.app.R.string.section_badges))

        // Métricas rápidas.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricChip(
                label = stringResource(com.treinos.app.R.string.metric_sessoes),
                value = state.totalSessions.toString(),
                color = MaterialTheme.colorScheme.primary,
            )
            MetricChip(
                label = stringResource(com.treinos.app.R.string.metric_streak),
                value = state.currentStreak.toString(),
                color = MaterialTheme.colorScheme.secondary,
            )
            MetricChip(
                label = stringResource(com.treinos.app.R.string.metric_recordes),
                value = state.totalPrs.toString(),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        // Lista de badges: conquistados primeiro, depois bloqueados.
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (badge in state.badges) {
                BadgeRow(badge = badge)
            }
        }

        if (state.badges.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(com.treinos.app.R.string.msg_no_badges_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.size(4.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}

@Composable
private fun BadgeRow(badge: Badge) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = badgeIcon(badge.id),
                contentDescription = null,
                tint = Color(badge.color),
                modifier = Modifier.size(32.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Text(
                text = "+${badge.xpReward} XP",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = Color(badge.color),
            )
        }
    }
}
