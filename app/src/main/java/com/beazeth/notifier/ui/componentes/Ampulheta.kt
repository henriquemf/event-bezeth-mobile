package com.beazeth.notifier.ui.componentes

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.misturar

/**
 * A ampulheta do Pomodoro, igual a de `css/widgets/pomodoro.css`.
 *
 * **Geometria do site, tracinho por tracinho:** tampa 6%, bulbo 44%, bulbo 44%,
 * tampa 6% -- o gargalo cai exatamente na metade da altura. Os bulbos sao
 * trapezios que deixam 12% de largura no meio; sem essa folga as duas pontas se
 * encontram num ponto de espessura zero e a ampulheta vira uma gravata.
 *
 * **A areia nao e animacao.** O nivel vem de [progresso], a mesma fracao que
 * move o anel. A ampulheta esvazia no ritmo do tempo configurado, seja ele de 1
 * ou de 600 minutos -- e nao num ciclo fixo que mentiria sobre quanto falta.
 *
 * **A chacoalhada** ocupa os ultimos 12% de um ciclo de 7 segundos; o resto do
 * tempo o vidro fica parado. Giro continuo competiria com o resto da tela pela
 * atencao o tempo todo. Nao e uma virada de 180 graus por outro motivo: depois
 * de virar, o bulbo cheio passaria a ser o de cima e a areia teria de subir
 * para a contagem continuar. Com uma inclinacao pequena, "para baixo" nunca
 * muda de lugar.
 *
 * @param progresso fracao ja decorrida, de 0 a 1.
 * @param correndo se o filete de areia deve cair.
 */
@Composable
fun Ampulheta(
    progresso: Float,
    correndo: Boolean,
    modifier: Modifier = Modifier,
    largura: Dp = 96.dp,
) {
    val cores = Doce
    val transicao = rememberInfiniteTransition(label = "ampulheta")

    // Os mesmos quadros-chave de `@keyframes hgShake`. `ease-in-out` e nao
    // linear porque a chacoalhada precisa desacelerar nas pontas -- linear dava
    // um vaivem de metronomo.
    val inclinacao by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 7_000
                0f at 0
                0f at 6_160
                -5f at 6_300
                4f at 6_510
                -2f at 6_720
                0f at 7_000
            },
        ),
        label = "chacoalhada",
    )

    // O grao desce do gargalo, 0,55 s por volta.
    val grao by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "grao",
    )

    // As cores do CSS, com `color-mix` resolvido.
    val vidro = misturar(cores.destaque, Color.White, 0.14f).copy(alpha = 0.57f)
    val borda = cores.destaqueEscuro.copy(alpha = 0.65f)
    val areiaGrao = misturar(cores.destaque, Color.White, 0.88f)
    val areia = Brush.verticalGradient(
        listOf(misturar(cores.destaque, Color.White, 0.72f), cores.destaqueEscuro)
    )

    Canvas(
        modifier = modifier.size(width = largura, height = largura * 1.43f),
    ) {
        rotate(degrees = inclinacao, pivot = center) {
            val l = size.width
            val a = size.height
            val tampa = a * 0.06f
            val bulbo = a * 0.44f

            drawRoundRect(
                color = borda,
                topLeft = Offset(0f, 0f),
                size = Size(l, tampa),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(tampa / 2f),
            )
            drawRoundRect(
                color = borda,
                topLeft = Offset(0f, a - tampa),
                size = Size(l, tampa),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(tampa / 2f),
            )

            // ------------------------------------------------- bulbo de cima
            val topoY = tampa
            val recorteTopo = Path().apply {
                moveTo(0f, topoY)
                lineTo(l, topoY)
                lineTo(l * 0.56f, topoY + bulbo)
                lineTo(l * 0.44f, topoY + bulbo)
                close()
            }
            clipPath(recorteTopo) {
                drawRect(color = vidro, topLeft = Offset(0f, topoY), size = Size(l, bulbo))
                // Ancorada embaixo: no bulbo de cima a areia fica encostada no
                // gargalo e o vazio cresce por cima.
                val altura = bulbo * (1f - progresso).coerceIn(0f, 1f)
                drawRect(
                    brush = areia,
                    topLeft = Offset(0f, topoY + bulbo - altura),
                    size = Size(l, altura),
                )
            }

            // ------------------------------------------------ bulbo de baixo
            val baseY = topoY + bulbo
            val recorteBase = Path().apply {
                moveTo(l * 0.44f, baseY)
                lineTo(l * 0.56f, baseY)
                lineTo(l, baseY + bulbo)
                lineTo(0f, baseY + bulbo)
                close()
            }
            clipPath(recorteBase) {
                drawRect(color = vidro, topLeft = Offset(0f, baseY), size = Size(l, bulbo))
                val altura = bulbo * progresso.coerceIn(0f, 1f)
                drawRect(
                    brush = areia,
                    topLeft = Offset(0f, baseY + bulbo - altura),
                    size = Size(l, altura),
                )
            }

            // ------------------------------------------------------- filete
            // Areia so cai quando o tempo esta correndo.
            if (correndo) {
                val largo = maxOf(2.dp.toPx(), l * 0.05f)
                val alto = a * 0.32f
                val x = (l - largo) / 2f
                clipPath(
                    Path().apply {
                        addRect(Rect(x, a * 0.5f, x + largo, a * 0.5f + alto))
                    }
                ) {
                    // O grao percorre de -100% a +100% da propria altura, que e
                    // o que `@keyframes hgGrain` faz.
                    val deslocamento = (grao * 2f - 1f) * alto
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to areiaGrao,
                            0.65f to areiaGrao,
                            1f to Color.Transparent,
                        ),
                        topLeft = Offset(x, a * 0.5f + deslocamento),
                        size = Size(largo, alto),
                    )
                }
            }
        }
    }
}

/** Um `DrawScope` para desenhar o anel do mostrador. Fica aqui para a tela do
 *  Pomodoro nao precisar repetir a conta do arco. */
fun DrawScope.anelDoMostrador(
    progresso: Float,
    corViva: Color,
    corApagada: Color,
    espessura: Float,
) {
    val canto = Offset(espessura / 2f, espessura / 2f)
    val medida = Size(size.width - espessura, size.height - espessura)

    drawArc(
        color = corApagada,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = canto,
        size = medida,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = espessura,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        ),
    )
    drawArc(
        color = corViva,
        // -90 comeca no topo, como todo relogio.
        startAngle = -90f,
        sweepAngle = 360f * progresso.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = canto,
        size = medida,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = espessura,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        ),
    )
}
