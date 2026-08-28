package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * `<footer class="site-love-footer">EU TE AMO MOMO</footer>`.
 *
 * O site tem esta faixa no fim de TODA pagina -- inclusive nas de entrar e
 * criar conta, porque no `base.html` ela fica fora do bloco da casca. Aqui e
 * igual: aparece embaixo do app inteiro e tambem embaixo das duas telas de
 * entrada.
 *
 * ## Onde ela fica, e por que nao da para copiar exatamente
 *
 * No site a pagina inteira rola como uma coisa so, e o rodape e a ultima delas
 * -- numa janela de 800x500 ele nasce a 1915 px do topo, ou seja, so aparece
 * quando se chega ao fim. No app nao existe esse "fim da pagina": a lateral rola
 * por conta, cada tela rola por conta, e nenhuma das duas tem um ponto final
 * comum onde pendurar isto.
 *
 * Entao ele fica fixo na base da casca, sempre a vista. E uma diferenca
 * deliberada, e na direcao certa: um recado que ninguem acha nao e um recado.
 * Custa 50 dp de altura -- a mesma altura que ele tem no site.
 *
 * ## O desenho
 *
 * Copiado de `.site-love-footer` em `base.css`, valor por valor: fundo
 * `--surface-solid`, borda de 1 px em `--stroke`, tinta `--ink`, fonte de
 * display em negrito a 15 px com `letter-spacing: 0.06em`, e os dois fios que o
 * CSS desenha com `::before`/`::after` -- 1 px de altura, `min(120px, 18%)` de
 * largura, com o destaque a 60% de opacidade sumindo para as pontas.
 *
 * A unica coisa que muda e o raio do canto: 18 px no site, [Canto.cartao] aqui.
 * Nao e descuido -- e a regra que o app ja segue em todos os cartoes, escrita
 * em [Canto]: com a tela cheia de cartoes empilhados, a curva do site vira uma
 * pilha de balas. Uma faixa arredondada de 18 px no meio de cartoes de 6 pareceria
 * um pedaco de outra tela.
 */
@Composable
internal fun RodapeDoAmor(modifier: Modifier = Modifier) {
    val cores = Doce

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // `margin: 6px 16px 18px` do CSS. Embaixo fica menos: no site aquela
            // folga separa o rodape do fim da pagina que ainda rola; aqui embaixo
            // dele so existe a borda da tela.
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
    ) {
        // `width: min(120px, 18%)`, sobre a largura de DENTRO da faixa -- daí
        // descontar os 18 px de respiro de cada lado.
        val fio = minOf(120.dp, (maxWidth - 36.dp) * 0.18f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Canto.cartao))
                .background(cores.superficie)
                .border(1.dp, cores.traco, RoundedCornerShape(Canto.cartao))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Fio(largura = fio, cor = cores.destaque)
            Text(
                text = "EU TE AMO MOMO",
                style = TipografiaBeazeth.titleMedium.copy(
                    fontSize = 15.sp,
                    letterSpacing = 0.06.em,
                ),
                color = cores.tinta,
                // O `gap: 10px` do flex, que aqui separa o texto dos dois fios.
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Fio(largura = fio, cor = cores.destaque)
        }
    }
}

/** Um dos dois fios das pontas: some para as bordas, acende no meio. */
@Composable
private fun Fio(largura: Dp, cor: Color) {
    Box(
        modifier = Modifier
            .width(largura)
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to cor.copy(alpha = 0.6f),
                    1f to Color.Transparent,
                )
            ),
    )
}
