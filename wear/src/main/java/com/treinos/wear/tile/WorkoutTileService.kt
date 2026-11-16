package com.treinos.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.treinos.wear.R
import com.treinos.wear.WearApplication
import com.treinos.wear.ui.WorkoutWearActivity
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * Tile do relógio (H4-14): mostra o treino em andamento publicado pelo
 * celular ([WearTileModel.Active]) ou o estado ocioso ([WearTileModel.Idle]).
 *
 * O tile NÃO inicia treino (isso é H4-16): qualquer toque abre o app do
 * relógio, que pede o estado ao celular. Só lê o último estado local — sem
 * rede. `onTileRequest` nunca lança: falha de leitura/uso cai no estado
 * ocioso.
 */
class WorkoutTileService : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        val tile = runCatching { renderTile(requestParams) }
            .getOrElse { idleTile(requestParams.deviceConfiguration) }
        return ImmediateFuture(tile)
    }

    private fun renderTile(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        val state = (application as WearApplication).stateStore.loadState()
        val model = wearTileModel(state, System.currentTimeMillis())
        val root = when (model) {
            is WearTileModel.Active -> activeLayout(requestParams.deviceConfiguration, model)
            WearTileModel.Idle -> idleLayout(requestParams.deviceConfiguration)
        }
        return tile(root)
    }

    /** Falha de leitura/uso: tile no estado ocioso, sem lançar. */
    private fun idleTile(deviceParameters: DeviceParametersBuilders.DeviceParameters): TileBuilders.Tile =
        tile(idleLayout(deviceParameters))

    private fun tile(root: LayoutElementBuilders.LayoutElement): TileBuilders.Tile {
        val layout = LayoutElementBuilders.Layout.Builder().setRoot(root).build()
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(layout)
                    .build()
            )
            .build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .build()
    }

    /** Toque em qualquer parte do tile abre o app do relógio (launcher do :wear). */
    private fun openWorkoutClickable(): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId(CLICK_ID_OPEN)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(WorkoutWearActivity::class.java.name)
                            .build()
                    )
                    .build()
            )
            .build()

    private fun activeLayout(
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        model: WearTileModel.Active,
    ): LayoutElementBuilders.LayoutElement {
        val fallback = getString(R.string.tile_workout_fallback)
        // Regra 12: "N de M séries"; sem séries planejadas, "—" no lugar do número.
        val series = if (model.plannedSets == 0) {
            "—"
        } else {
            getString(R.string.tile_set_progress, model.setNumber, model.plannedSets)
        }
        return PrimaryLayout.Builder(deviceParameters)
            .setContent(
                LayoutElementBuilders.Column.Builder()
                    .addContent(
                        Text.Builder(this, model.dayName.ifBlank { fallback })
                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setMaxLines(2)
                            .build()
                    )
                    .addContent(
                        Text.Builder(this, model.exerciseName.ifBlank { fallback })
                            .setTypography(Typography.TYPOGRAPHY_TITLE1)
                            .setMaxLines(2)
                            .build()
                    )
                    .addContent(
                        Text.Builder(this, series)
                            .setTypography(Typography.TYPOGRAPHY_BODY1)
                            .build()
                    )
                    .build()
            )
            .setPrimaryChipContent(
                Button.Builder(this, openWorkoutClickable())
                    .setTextContent(getString(R.string.tile_open_workout))
                    .build()
            )
            .build()
    }

    private fun idleLayout(
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
    ): LayoutElementBuilders.LayoutElement =
        PrimaryLayout.Builder(deviceParameters)
            .setContent(
                Text.Builder(this, getString(R.string.tile_open_app))
                    .setTypography(Typography.TYPOGRAPHY_TITLE1)
                    .setMaxLines(2)
                    .build()
            )
            .setPrimaryChipContent(
                Button.Builder(this, openWorkoutClickable())
                    .setTextContent(getString(R.string.tile_open))
                    .build()
            )
            .build()

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val CLICK_ID_OPEN = "open_workout"

        /**
         * H4-14 (regra 13): pede ao sistema a re-renderização do tile depois
         * de uma mudança de estado (estado novo do celular, FINISH_WORKOUT do
         * relógio). Protegido por runCatching: sem host de tile a chamada não
         * pode derrubar o chamador.
         */
        fun requestUpdate(context: Context) {
            runCatching {
                TileService.getUpdater(context).requestUpdate(WorkoutTileService::class.java)
            }
        }
    }
}

/**
 * `ListenableFuture` já concluído, sem depender da Guava completa (o artefato
 * `com.google.guava:listenablefuture` traz só a interface). O tile é montado
 * de forma síncrona em `onTileRequest` — leitura de SharedPreferences e
 * protolayout, nada de I/O — e o resultado fica pronto para `get()`.
 */
private class ImmediateFuture<T>(private val value: T) : ListenableFuture<T> {
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false
    override fun isCancelled(): Boolean = false
    override fun isDone(): Boolean = true
    override fun get(): T = value
    override fun get(timeout: Long, unit: TimeUnit): T = value
    override fun addListener(listener: Runnable, executor: Executor) {
        executor.execute(listener)
    }
}
