package com.treinos.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.treinos.app.R
import com.treinos.app.ui.AppViewModel

/**
 * H3-38: bottom bar nos hubs — Treinar, Histórico, Ajustes.
 * `selected` é derivado da [AppViewModel.screen] no chamador ([TreinosApp]).
 */
@Composable
fun BottomNavigationBar(selected: Int, viewModel: AppViewModel) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
    ) {
        NavigationBarItem(
            selected = selected == 0,
            onClick = { viewModel.goHome() },
            icon = { Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_bar_treinar)) },
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = { viewModel.openHistory() },
            icon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_bar_historico)) },
        )
        NavigationBarItem(
            selected = selected == 2,
            onClick = { viewModel.openSettings() },
            icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.bottom_bar_ajustes)) },
        )
    }
}
