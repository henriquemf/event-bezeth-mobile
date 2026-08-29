package com.beazeth.notifier.ui.componentes

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.misturar

/**
 * O copo d'agua de `css/widgets/water.css`.
 *
 * O app tinha a ampulheta do Pomodoro desenhada e, para a agua, uma gota de
 * emoji -- na barra lateral e na tela. Os dois widgets sao irmaos no site, e
 * so um tinha chegado aqui.
 *
 * **Geometria do site:** copo conico recortado em `polygon(4% 0, 96% 0, 84%
 * 100%, 16% 100%)`, altura de [PROPORCAO] vezes a largura, vidro em degrade
 * horizontal com as bordas mais saturadas que o meio, agua em degrade vertical
 * e um brilho na diagonal por cima.
 *
 * **A agua sobe por translacao, nao por escala.** Esticar o bloco d'agua
 * achataria a onda do topo junto, e com o copo quase vazio ela sumiria. Aqui a
 * superficie e uma linha que desce, e a onda anda com ela.
 *
 * **A onda e um quadradao de cantos quase redondos girando** -- 2,4 larguras de
 * lado, centrado bem acima da superficie, de modo que so a barriga de baixo
 * apareca. Girar faz essa barriga ondular. Sao dois, em sentidos e velocidades
 * diferentes, porque um so gira como um disco; e o cruzamento dos dois que
 * parece agua.
 *
 * **Um detalhe em que este copo NAO copia o site.** La o quadradao da onda nao
 * e recortado pela linha d'agua, entao ele cobre tambem a parte vazia do copo:
 * com um copo em 1/8, o site desenha um copo cheio. Aqui a onda e recortada a
 * [BARRIGA_DA_ONDA] acima da superficie -- que e exatamente a folga que o
 * comentario do CSS diz querer ("sobra 0.06w de barriga para fora da agua").
 * Um medidor que marca cheio com um gole dentro nao e um medidor.
 *
 * @param nivel fracao cheia, de 0 a 1.
 */
@Composable
fun Copo(
    nivel: Float,
    modifier: Modifier = Modifier,
    largura: Dp = 96.dp,
) {
    val cores = Doce
    val transicao = rememberInfiniteTransition(label = "copo")

    val giro by transicao.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "onda",
    )
    val giroDeTras by transicao.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(11_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "onda de tras",
    )

    // A subida da agua tem a curva do site (0.62 s, `cubic-bezier(0.22, 0.61,
    // 0.36, 1)`): sai depressa e chega devagar, como liquido que assenta.
    val cheio by animateFloatAsState(
        targetValue = nivel.coerceIn(0f, 1f),
        animationSpec = tween(620, easing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)),
        label = "nivel",
    )

    // As cores do CSS com o `color-mix` resolvido. O azul e o da paleta em
    // vigor, entao o copo troca de tom junto com o resto do tema.
    val azul = cores.azulSuave
    // O CSS mistura o azul com BRANCO para o vidro vazio, porque no site o
    // cartao atras dele e branco. Aqui a mistura e com a superficie do cartao
    // em vigor: no tema escuro, branco a 61% viraria uma placa cinza opaca no
    // meio de um cartao roxo -- e vidro que nao deixa ver o que tem atras nao
    // parece vidro. No tema claro a conta da no mesmo lugar do site.
    val vidro = misturar(azul, cores.superficie, 0.14f).copy(alpha = 0.55f)
    val fio = azul.copy(alpha = 0.2475f)
    val aguaTopo = misturar(azul, Color.White, 0.85f)
    val aguaFundo = misturar(azul, Color(0xFF2A6F9C), 0.62f)

    Canvas(modifier = modifier.size(width = largura, height = largura * PROPORCAO)) {
        val l = size.width
        val a = size.height

        val corpo = Path().apply {
            moveTo(l * 0.04f, 0f)
            lineTo(l * 0.96f, 0f)
            lineTo(l * 0.84f, a)
            lineTo(l * 0.16f, a)
            close()
        }

        clipPath(corpo) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to fio, 0.02f to vidro, 0.98f to vidro, 1f to fio,
                ),
            )

            val superficie = a * (1f - cheio)

            // Copo vazio nao tem agua nem onda. Sem esta guarda, a faixa de
            // [BARRIGA_DA_ONDA] que a onda pode ocupar acima da superficie
            // sobrava no fundo do copo com o nivel em zero, e cada copinho vazio
            // da fileira do dia ganhava um risco azul embaixo -- agua onde nao
            // ha agua nenhuma.
            if (cheio > 0f) {
                // O degrade da agua corre por uma altura de copo inteira a
                // partir da superficie -- e o bloco do site, que desce mas nao
                // encolhe. Com o copo pela metade, o fundo mostra o meio do
                // degrade, e nao o fim.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(aguaTopo, aguaFundo),
                        startY = superficie,
                        endY = superficie + a,
                    ),
                    topLeft = Offset(0f, superficie),
                    size = Size(l, a - superficie),
                )

                clipRect(top = superficie - l * BARRIGA_DA_ONDA, bottom = a) {
                    val lado = l * 2.4f
                    val centro = Offset(l / 2f, superficie - l * 1.14f)
                    rotate(degrees = giro, pivot = centro) {
                        drawRoundRect(
                            color = aguaTopo,
                            topLeft = Offset(centro.x - lado / 2f, centro.y - lado / 2f),
                            size = Size(lado, lado),
                            cornerRadius = CornerRadius(lado * 0.42f),
                        )
                    }
                    val centroDeTras = Offset(l / 2f, superficie - l * 1.16f)
                    rotate(degrees = giroDeTras, pivot = centroDeTras) {
                        drawRoundRect(
                            color = aguaTopo.copy(alpha = 0.55f),
                            topLeft = Offset(
                                centroDeTras.x - lado / 2f,
                                centroDeTras.y - lado / 2f,
                            ),
                            size = Size(lado, lado),
                            cornerRadius = CornerRadius(lado * 0.45f),
                        )
                    }
                }
            }

            // Brilho do vidro: a faixa clara na diagonal, por cima da agua.
            drawRect(
                brush = Brush.linearGradient(
                    0.12f to Color.Transparent,
                    0.16f to Color.White.copy(alpha = 0.42f),
                    0.24f to Color.White.copy(alpha = 0.12f),
                    0.30f to Color.Transparent,
                    start = Offset(0f, 0f),
                    end = Offset(l, a * 0.25f),
                ),
            )
        }
    }
}

/** `--glass-h: calc(var(--glass-w) * 1.34)`. */
private const val PROPORCAO = 1.34f

/** Quanto da onda pode passar da linha d'agua, em larguras de copo. */
private const val BARRIGA_DA_ONDA = 0.06f
