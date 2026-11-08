package com.treinos.app.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Ícones do player de mobilidade (H3-32) que não existem em
 * `material-icons-core`. Desenhados como [ImageVector] local para não adicionar
 * `material-icons-extended` — o release não usa minify e o pacote inflaria o APK.
 * Os traços são os mesmos do Material Design (24 dp).
 */
object AppIcons {
    val Pause: ImageVector
        get() {
            if (_pause != null) return _pause!!
            _pause = materialIcon(name = "AppIcons.Pause") {
                materialPath {
                    moveTo(6.0f, 19.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineToRelative(-14.0f)
                    horizontalLineToRelative(-4.0f)
                    verticalLineToRelative(14.0f)
                    close()
                    moveTo(14.0f, 5.0f)
                    verticalLineToRelative(14.0f)
                    horizontalLineToRelative(4.0f)
                    verticalLineToRelative(-14.0f)
                    horizontalLineToRelative(-4.0f)
                    close()
                }
            }
            return _pause!!
        }
    private var _pause: ImageVector? = null

    val SkipNext: ImageVector
        get() {
            if (_skipNext != null) return _skipNext!!
            _skipNext = materialIcon(name = "AppIcons.SkipNext") {
                materialPath {
                    moveTo(6.0f, 18.0f)
                    lineToRelative(8.5f, -6.0f)
                    lineToRelative(-8.5f, -6.0f)
                    verticalLineToRelative(12.0f)
                    close()
                    moveTo(16.0f, 6.0f)
                    verticalLineToRelative(12.0f)
                    horizontalLineToRelative(2.0f)
                    verticalLineToRelative(-12.0f)
                    horizontalLineToRelative(-2.0f)
                    close()
                }
            }
            return _skipNext!!
        }
    private var _skipNext: ImageVector? = null
}