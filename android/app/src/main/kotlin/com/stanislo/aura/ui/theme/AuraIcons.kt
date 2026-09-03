package com.stanislo.aura.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Wlasny komplet ikon rysowanych wektorowo. Jednolita grubosc kreski
 * i zaokraglone zakonczenia daja spojny, lekki charakter zblizony do SF Symbols -
 * zestaw Material wyglada przy tej typografii zbyt cieżko.
 */
object AuraIcons {

    private const val STROKE = 1.7f

    val Mic: ImageVector by lazy {
        icon("Mic") {
            outline {
                // Kapsula mikrofonu.
                moveTo(9f, 6.6f)
                arcTo(3f, 3f, 0f, false, true, 15f, 6.6f)
                lineTo(15f, 10.6f)
                arcTo(3f, 3f, 0f, false, true, 9f, 10.6f)
                close()
            }
            outline {
                // Luk pochwytu.
                moveTo(6f, 11.4f)
                arcTo(6f, 6f, 0f, false, false, 18f, 11.4f)
            }
            outline {
                moveTo(12f, 17.4f)
                lineTo(12f, 20.6f)
            }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            filled {
                moveTo(9.6f, 8f)
                lineTo(14.4f, 8f)
                arcTo(1.6f, 1.6f, 0f, false, true, 16f, 9.6f)
                lineTo(16f, 14.4f)
                arcTo(1.6f, 1.6f, 0f, false, true, 14.4f, 16f)
                lineTo(9.6f, 16f)
                arcTo(1.6f, 1.6f, 0f, false, true, 8f, 14.4f)
                lineTo(8f, 9.6f)
                arcTo(1.6f, 1.6f, 0f, false, true, 9.6f, 8f)
                close()
            }
        }
    }

    val Waveform: ImageVector by lazy {
        icon("Waveform") {
            outline {
                moveTo(3.5f, 10.6f); lineTo(3.5f, 13.4f)
                moveTo(8f, 7.4f); lineTo(8f, 16.6f)
                moveTo(12f, 4.8f); lineTo(12f, 19.2f)
                moveTo(16f, 7.4f); lineTo(16f, 16.6f)
                moveTo(20.5f, 10.6f); lineTo(20.5f, 13.4f)
            }
        }
    }

    val ArrowUp: ImageVector by lazy {
        icon("ArrowUp") {
            outline {
                moveTo(12f, 19.5f); lineTo(12f, 5.2f)
                moveTo(6.2f, 11f); lineTo(12f, 5.2f); lineTo(17.8f, 11f)
            }
        }
    }

    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            // Linie sa przerwane tam, gdzie siedzi galka - inaczej ikona
            // czytalaby sie jak zwykle menu, bo tint zlewa oba ksztalty w jeden.
            outline {
                moveTo(3.4f, 7f); lineTo(6.3f, 7f)
                moveTo(10.9f, 7f); lineTo(20.6f, 7f)

                moveTo(3.4f, 12f); lineTo(13.1f, 12f)
                moveTo(17.7f, 12f); lineTo(20.6f, 12f)

                moveTo(3.4f, 17f); lineTo(8.3f, 17f)
                moveTo(12.9f, 17f); lineTo(20.6f, 17f)
            }
            outline { circle(8.6f, 7f, 2.3f) }
            outline { circle(15.4f, 12f, 2.3f) }
            outline { circle(10.6f, 17f, 2.3f) }
        }
    }

    val Document: ImageVector by lazy {
        icon("Document") {
            outline {
                moveTo(6.6f, 3.4f)
                lineTo(13.4f, 3.4f)
                lineTo(17.6f, 7.6f)
                lineTo(17.6f, 20.6f)
                lineTo(6.6f, 20.6f)
                close()
            }
            outline {
                moveTo(13.4f, 3.4f); lineTo(13.4f, 7.6f); lineTo(17.6f, 7.6f)
            }
            outline {
                moveTo(9.6f, 12.6f); lineTo(14.6f, 12.6f)
                moveTo(9.6f, 16.2f); lineTo(14.6f, 16.2f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            outline {
                moveTo(4.4f, 6.6f); lineTo(19.6f, 6.6f)
            }
            outline {
                moveTo(9.5f, 6.6f); lineTo(9.5f, 4.6f); lineTo(14.5f, 4.6f); lineTo(14.5f, 6.6f)
            }
            outline {
                moveTo(6.5f, 6.6f); lineTo(7.4f, 19.6f); lineTo(16.6f, 19.6f); lineTo(17.5f, 6.6f)
            }
            outline {
                moveTo(10.6f, 10.2f); lineTo(10.9f, 16.2f)
                moveTo(13.4f, 10.2f); lineTo(13.1f, 16.2f)
            }
        }
    }

    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft") {
            outline {
                moveTo(14.6f, 4.6f); lineTo(8.2f, 12f); lineTo(14.6f, 19.4f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            outline {
                moveTo(5f, 12.6f); lineTo(9.8f, 17.4f); lineTo(19f, 6.9f)
            }
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            outline {
                moveTo(6.6f, 6.6f); lineTo(17.4f, 17.4f)
                moveTo(17.4f, 6.6f); lineTo(6.6f, 17.4f)
            }
        }
    }

    val Refresh: ImageVector by lazy {
        icon("Refresh") {
            outline {
                moveTo(19f, 12f)
                arcTo(7f, 7f, 0f, true, true, 12f, 5f)
            }
            outline {
                moveTo(9.2f, 2.6f); lineTo(12.3f, 5.1f); lineTo(9.5f, 7.7f)
            }
        }
    }

    val Eye: ImageVector by lazy {
        icon("Eye") {
            outline {
                moveTo(2.8f, 12f)
                curveTo(6f, 6.8f, 8.8f, 5.3f, 12f, 5.3f)
                curveTo(15.2f, 5.3f, 18f, 6.8f, 21.2f, 12f)
                curveTo(18f, 17.2f, 15.2f, 18.7f, 12f, 18.7f)
                curveTo(8.8f, 18.7f, 6f, 17.2f, 2.8f, 12f)
                close()
            }
            outline { circle(12f, 12f, 2.9f) }
        }
    }

    val EyeOff: ImageVector by lazy {
        icon("EyeOff") {
            outline {
                moveTo(2.8f, 12f)
                curveTo(6f, 6.8f, 8.8f, 5.3f, 12f, 5.3f)
                curveTo(15.2f, 5.3f, 18f, 6.8f, 21.2f, 12f)
                curveTo(18f, 17.2f, 15.2f, 18.7f, 12f, 18.7f)
                curveTo(8.8f, 18.7f, 6f, 17.2f, 2.8f, 12f)
                close()
            }
            outline { circle(12f, 12f, 2.9f) }
            outline {
                moveTo(4.2f, 20.2f); lineTo(19.8f, 3.8f)
            }
        }
    }

    val Sparkle: ImageVector by lazy {
        icon("Sparkle") {
            filled {
                moveTo(12f, 3f)
                curveTo(12.9f, 8f, 15.4f, 10.6f, 20.4f, 11.6f)
                curveTo(15.4f, 12.6f, 12.9f, 15.2f, 12f, 20.2f)
                curveTo(11.1f, 15.2f, 8.6f, 12.6f, 3.6f, 11.6f)
                curveTo(8.6f, 10.6f, 11.1f, 8f, 12f, 3f)
                close()
            }
        }
    }

    // ---------- konstrukcja ----------

    private fun icon(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply(content).build()

    private fun ImageVector.Builder.outline(pathBuilder: PathBuilder.() -> Unit) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        )
    }

    private fun ImageVector.Builder.filled(pathBuilder: PathBuilder.() -> Unit) {
        path(fill = SolidColor(Color.Black), pathBuilder = pathBuilder)
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
        moveTo(cx - radius, cy)
        arcTo(radius, radius, 0f, true, true, cx + radius, cy)
        arcTo(radius, radius, 0f, true, true, cx - radius, cy)
        close()
    }
}
