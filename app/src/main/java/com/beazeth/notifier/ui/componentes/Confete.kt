package com.beazeth.notifier.ui.componentes

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import kotlin.math.pow
import kotlin.random.Random

/**
 * O confete do fim do foco.
 *
 * ## Uma animacao so, e nao uma por peca
 *
 * Ha um `Animatable` unico, de 0 a 1, e a posicao de cada peca e uma conta
 * sobre ele. Quarenta `animateFloatAsState` seriam quarenta animacoes que o
 * Compose precisa acompanhar; aqui e um valor e um `Canvas`, que redesenha sem
 * recompor nada.
 *
 * ## Cada peca cai no seu tempo
 *
 * O atraso individual e o que separa "confete" de "cortina": sem ele as
 * quarenta pecas descem em bloco, na mesma altura, e parece uma persiana. Cada
 * uma tem o proprio atraso, a propria deriva lateral e o proprio giro, todos
 * sorteados a partir da [chave] -- o instante em que o pomodoro terminou. Mesma
 * chave, mesmo sorteio: girar o aparelho no meio da festa nao embaralha o que
 * ja estava caindo.
 *
 * ## Animacao desligada no sistema
 *
 * Quem desliga as animacoes nos ajustes do Android (ou usa economia de bateria
 * extrema) recebe [aoTerminar] na hora e nao ve peca nenhuma. E o mesmo que o
 * site faz com `prefers-reduced-motion`: o efeito nao acontece, e nada fica
 * parado na tela no lugar dele.
 */
@Composable
fun Confete(chave: Long, aoTerminar: () -> Unit, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    val animar = remember {
        Settings.Global.getFloat(
            contexto.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    val pecas = remember(chave) { sortearPecas(chave) }
    val progresso = remember(chave) { Animatable(0f) }

    LaunchedEffect(chave) {
        if (animar) {
            progresso.animateTo(1f, tween(DURACAO, easing = LinearEasing))
        }
        aoTerminar()
    }

    if (!animar) return

    Canvas(modifier = modifier.fillMaxSize()) {
        for (peca in pecas) {
            // Tempo da peca: 0 enquanto o atraso dela nao passou, 1 no fim.
            val t = ((progresso.value - peca.atraso) / (1f - peca.atraso)).coerceIn(0f, 1f)
            if (t <= 0f) continue

            val y = -ALTURA_DA_PECA + t * (size.height + ALTURA_DA_PECA * 2)
            val x = peca.x * size.width + peca.deriva * size.width * t
            val largura = LARGURA_DA_PECA * peca.escala
            val altura = ALTURA_DA_PECA * peca.escala

            rotate(degrees = peca.giro * t, pivot = Offset(x + largura / 2f, y + altura / 2f)) {
                drawRect(
                    // Some no fim em vez de desaparecer de uma vez: `t^3` deixa
                    // a peca opaca quase o caminho todo e apaga so no ultimo
                    // terco, que e quando ela ja saiu da tela.
                    color = peca.cor.copy(alpha = 1f - t.pow(3)),
                    topLeft = Offset(x, y),
                    size = Size(largura, altura),
                )
            }
        }
    }
}

private class Peca(
    val x: Float,
    val deriva: Float,
    val giro: Float,
    val escala: Float,
    val atraso: Float,
    val cor: Color,
)

private fun sortearPecas(chave: Long): List<Peca> {
    val sorte = Random(chave)
    return List(QUANTAS) {
        Peca(
            x = sorte.nextFloat(),
            deriva = sorte.nextFloat() * 0.3f - 0.15f,
            giro = 240f + sorte.nextFloat() * 660f,
            escala = 0.6f + sorte.nextFloat() * 0.7f,
            // Teto em 0.45 para a ultima peca ainda ter mais da metade da
            // animacao para atravessar a tela.
            atraso = sorte.nextFloat() * 0.45f,
            cor = TONS[it % TONS.size],
        )
    }
}

/** As seis cores de papel, as mesmas dos post-its e dos humores do diario. */
private val TONS = listOf(
    Color(0xFFFF9EC4),
    Color(0xFFFFE066),
    Color(0xFF7FE0C0),
    Color(0xFFC0A8FB),
    Color(0xFF8FCDFF),
    Color(0xFFFFB08A),
)

private const val QUANTAS = 44
private const val DURACAO = 2600

private const val LARGURA_DA_PECA = 18f
private const val ALTURA_DA_PECA = 28f
