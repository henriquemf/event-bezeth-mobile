package com.beazeth.notifier.ui.componentes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * O interruptor de modo escuro do site: um sol que gira para fora enquanto uma
 * lua gira para dentro.
 *
 * Os dois desenhos ficam empilhados no mesmo ponto e trocam por opacidade,
 * giro e escala -- nenhum layout muda, exatamente como no CSS, onde isso existe
 * para a troca rodar no compositor. E o mesmo motivo aqui: nada disso invalida
 * medida nem posicionamento, so a pintura.
 *
 * **Os tracos sao os do site, letra por letra.** [CAMINHO_LUA] e
 * [CAMINHO_RAIOS] sao os atributos `d` dos `<svg>` de
 * `partials/sidebar.html`, lidos pelo `PathParser`. Redesenhar a lua a mao com
 * dois circulos daria uma lua parecida, e "parecida" e justamente o que se
 * quer evitar: a diferenca aparece lado a lado com o site.
 *
 * As curvas de tempo tambem sao as do CSS: opacidade em 0,26 s com `ease`;
 * giro e escala em 0,32 s com uma bezier que passa de 1 e volta. A ultrapassada
 * e o que faz o astro *chegar* em vez de so aparecer.
 */

/** `path` do `<svg class="icon-moon">`, num quadro de 24x24. */
private const val CAMINHO_LUA = "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"

/** Os oito raios do `<svg class="icon-sun">`. O miolo e um `<circle>`. */
private const val CAMINHO_RAIOS =
    "M12 2.6v2.2M12 19.2v2.2M4.3 4.3l1.6 1.6M18.1 18.1l1.6 1.6" +
        "M2.6 12h2.2M19.2 12h2.2M4.3 19.7l1.6-1.6M18.1 5.9l1.6-1.6"

/** O lado do quadro dos `<svg>`, para converter o traco e o raio do miolo. */
private const val QUADRO = 24f

// As quatro cores do disco sao fixas no site -- nao saem da paleta. Um sol azul
// no tema Ocean seria coerente com o tema e ilegivel como sol.
private val SOL_INICIO = Color(0xFFFF78B2).copy(alpha = 0.30f)
private val SOL_FIM = Color(0xFF7EC8FF).copy(alpha = 0.28f)
private val LUA_INICIO = Color(0xFF8256DE).copy(alpha = 0.75f)
private val LUA_FIM = Color(0xFFE671B7).copy(alpha = 0.70f)

/** `box-shadow: 0 0 14px -4px rgba(160,110,235,.8)` -- so no escuro. */
private val BRILHO = Color(0xFFA06EEB)

/**
 * @param rotulo quando presente, o controle vira a pilula com texto que o site
 *   tem na lateral. Sem rotulo e so o disco, do tamanho de um botao de barra --
 *   e a forma usada no topo, onde nao ha largura para uma palavra a mais.
 */
@Composable
fun InterruptorEscuro(
    escuro: Boolean,
    aoAlternar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    rotulo: String? = null,
    tamanho: Dp = 38.dp,
) {
    val cores = Doce

    // Duas animacoes, e nao uma: no CSS a opacidade e o transform tem duracoes
    // e curvas diferentes. Com um valor so, o astro que sai desapareceria no
    // mesmo instante em que para de girar, e a troca fica dura.
    val fade by animateFloatAsState(
        targetValue = if (escuro) 1f else 0f,
        animationSpec = tween(260, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)),
        label = "fade",
    )
    val giro by animateFloatAsState(
        targetValue = if (escuro) 1f else 0f,
        animationSpec = tween(320, easing = CubicBezierEasing(0.34f, 1.45f, 0.64f, 1f)),
        label = "giro",
    )
    val inicio by animateColorAsState(
        targetValue = if (escuro) LUA_INICIO else SOL_INICIO,
        animationSpec = tween(240),
        label = "inicio",
    )
    val fim by animateColorAsState(
        targetValue = if (escuro) LUA_FIM else SOL_FIM,
        animationSpec = tween(240),
        label = "fim",
    )

    // `remember` sem chave: o traco nao depende de nada que mude. Reinterpretar
    // as duas strings a cada quadro da animacao seria trabalho jogado fora.
    val lua = remember { PathParser().parsePathString(CAMINHO_LUA).toPath() }
    val raios = remember { PathParser().parsePathString(CAMINHO_RAIOS).toPath() }

    val alternar = Modifier.toggleable(
        value = escuro,
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(color = cores.destaque),
        role = Role.Switch,
        onValueChange = aoAlternar,
    )

    if (rotulo == null) {
        // No topo o disco e um botao redondo com a mesma moldura do atalho de
        // Aparencia ao lado: dois circulos iguais lidos como um par.
        Row(
            modifier = modifier
                .size(tamanho)
                .clip(CircleShape)
                .then(alternar)
                .border(1.dp, cores.traco, CircleShape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Disco(tamanho, fade, giro, inicio, fim, cores.tinta, raios, lua)
        }
    } else {
        Row(
            modifier = modifier
                .clip(CircleShape)
                .background(
                    if (escuro) cores.destaque.copy(alpha = 0.14f)
                    else cores.fundoCampo.copy(alpha = 0.6f)
                )
                .then(alternar)
                .border(
                    width = 1.dp,
                    color = if (escuro) lerp(cores.traco, cores.destaque, 0.40f) else cores.traco,
                    shape = CircleShape,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Disco(30.dp, fade, giro, inicio, fim, cores.tinta, raios, lua)
            Text(
                text = rotulo,
                style = TipografiaBeazeth.bodyMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = cores.tinta,
            )
        }
    }
}

@Composable
private fun Disco(
    lado: Dp,
    fade: Float,
    giro: Float,
    inicio: Color,
    fim: Color,
    tinta: Color,
    raios: Path,
    lua: Path,
) {
    Canvas(modifier = Modifier.size(lado)) {
        desenharDisco(fade, giro, inicio, fim, tinta, raios, lua)
    }
}

/**
 * O disco: brilho, degrade de fundo e os dois astros por cima.
 *
 * `fade` e `giro` chegam separados porque no CSS eles correm em curvas
 * diferentes; ver o comentario em [InterruptorEscuro].
 */
private fun DrawScope.desenharDisco(
    fade: Float,
    giro: Float,
    inicio: Color,
    fim: Color,
    tinta: Color,
    raios: Path,
    lua: Path,
) {
    val lado = size.minDimension
    val raio = lado / 2f

    // O halo do `box-shadow` no escuro. Sai junto com o sol, entra junto com a
    // lua -- e por isso segue `fade`, e nao um estado booleano.
    if (fade > 0.01f) {
        val alcance = raio * 1.45f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(BRILHO.copy(alpha = 0.55f * fade), Color.Transparent),
                center = center,
                radius = alcance,
            ),
            radius = alcance,
        )
    }

    // `linear-gradient(130deg, ...)`: no CSS o angulo conta a partir do "para
    // cima" e cresce no sentido horario, entao a direcao e (sen a, -cos a) --
    // com o y ja invertido para a tela, (sen a, cos a) do jeito que esta aqui.
    val angulo = Math.toRadians(130.0)
    val dx = sin(angulo).toFloat()
    val dy = cos(angulo).toFloat()
    val comprimento = lado * (abs(dx) + abs(dy))
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(inicio, fim),
            start = Offset(center.x - dx * comprimento / 2f, center.y - dy * comprimento / 2f),
            end = Offset(center.x + dx * comprimento / 2f, center.y + dy * comprimento / 2f),
        ),
        radius = raio,
    )

    // 17 px de icone num disco de 30 px, a proporcao do site.
    val icone = lado * (17f / 30f)

    astro(
        alpha = 1f - fade,
        giroGraus = 70f * giro,
        escala = 1f - 0.5f * giro,
        icone = icone,
    ) {
        // O miolo do sol e um `<circle r="4.2">`, nao faz parte do `d`.
        drawCircle(
            color = tinta,
            radius = 4.2f,
            center = Offset(12f, 12f),
            style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            alpha = 1f - fade,
        )
        tracar(raios, tinta, 1f - fade)
    }

    astro(
        alpha = fade,
        giroGraus = -70f * (1f - giro),
        escala = 0.5f + 0.5f * giro,
        icone = icone,
    ) {
        tracar(lua, tinta, fade)
    }
}

/**
 * Coloca o quadro de 24x24 do `<svg>` no centro do disco, girado e escalado.
 *
 * Sai daqui com o sistema de coordenadas do proprio `<svg>`: quem desenha
 * dentro fala em unidades de 0 a 24, e a espessura 2 do traco chega na tela
 * ja reduzida na mesma proporcao -- que e o que o navegador faz.
 */
private fun DrawScope.astro(
    alpha: Float,
    giroGraus: Float,
    escala: Float,
    icone: Float,
    corpo: DrawScope.() -> Unit,
) {
    if (alpha <= 0.01f) return
    val k = icone / QUADRO
    withTransform({
        rotate(degrees = giroGraus, pivot = center)
        scale(escala, escala, pivot = center)
        translate(center.x - icone / 2f, center.y - icone / 2f)
        scale(k, k, pivot = Offset.Zero)
    }) {
        corpo()
    }
}

private fun DrawScope.tracar(caminho: Path, cor: Color, alpha: Float) {
    drawPath(
        path = caminho,
        color = cor,
        alpha = alpha,
        style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
