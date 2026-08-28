package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.beazeth.notifier.ui.theme.Doce
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * O fundo do site: um degrade inclinado com tres bolhas de luz por cima.
 *
 * Equivale ao `body` mais o `.bg-candy` do CSS. Vale a matematica em vez de um
 * degrade vertical qualquer porque a bolha azul no canto superior direito e o
 * que impede a tela de virar um rosa chapado.
 *
 * As bolhas mudam entre claro e escuro (o CSS tem um `.bg-candy` proprio para
 * `[data-dark="true"]`), e por isso vem da paleta em vez de serem constantes.
 *
 * `val cores = Doce` antes do modificador nao e estilo: dentro de `drawBehind`
 * nao ha contexto de composicao, e ler `Doce` la seria erro de compilacao.
 */
@Composable
fun FundoDoce(
    modifier: Modifier = Modifier,
    conteudo: @Composable BoxScope.() -> Unit,
) {
    val cores = Doce

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // linear-gradient(170deg, --bg-top, --bg-mid 48%, --bg-bottom)
                drawRect(
                    gradienteCss(
                        graus = 170f,
                        cores = listOf(cores.fundoTopo, cores.fundoMeio, cores.fundoBase),
                        paradas = listOf(0f, 0.48f, 1f),
                    )
                )
                cores.posicoesDasBolhas.forEachIndexed { indice, (x, y, ate) ->
                    bolha(x, y, cores.bolhas[indice], ate)
                }
            },
        content = conteudo,
    )
}

/**
 * Um `linear-gradient(Ndeg, ...)` do CSS, com a mesma geometria.
 *
 * No CSS o angulo e medido no sentido horario a partir de "para cima", e a
 * linha do degrade e centrada na caixa com comprimento tal que as duas pontas
 * toquem os cantos. Reproduzir isso importa: um degrade vertical simples
 * perderia a inclinacao, que e sutil mas e o que faz o canto direito puxar
 * para o lilas.
 */
private fun DrawScope.gradienteCss(
    graus: Float,
    cores: List<Color>,
    paradas: List<Float>,
): Brush {
    val rad = Math.toRadians(graus.toDouble())
    // Eixo y do CSS cresce para cima; o da tela, para baixo. Dai o sinal.
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()

    val comprimento = abs(size.width * dx) + abs(size.height * dy)
    val centro = Offset(size.width / 2f, size.height / 2f)
    val meio = Offset(dx * comprimento / 2f, dy * comprimento / 2f)

    return Brush.linearGradient(
        colorStops = paradas.zip(cores).map { (parada, cor) -> parada to cor }.toTypedArray(),
        start = centro - meio,
        end = centro + meio,
    )
}

/**
 * Um `radial-gradient(circle at X% Y%, cor, transparent P%)`.
 *
 * O tamanho padrao de um circulo no CSS e `farthest-corner`: 100% fica no canto
 * mais distante do centro. Entao a parada em P% e P% dessa distancia -- e nao
 * uma fracao da largura da tela, que e o erro obvio e daria bolhas de tamanho
 * errado em tela de proporcao diferente.
 */
private fun DrawScope.bolha(x: Float, y: Float, cor: Color, ate: Float) {
    val centro = Offset(size.width * x, size.height * y)
    val cantoMaisLonge = listOf(
        Offset(0f, 0f),
        Offset(size.width, 0f),
        Offset(0f, size.height),
        Offset(size.width, size.height),
    ).maxOf { hypot(it.x - centro.x, it.y - centro.y) }

    drawRect(
        Brush.radialGradient(
            colors = listOf(cor, Color.Transparent),
            center = centro,
            radius = cantoMaisLonge * ate,
        )
    )
}
