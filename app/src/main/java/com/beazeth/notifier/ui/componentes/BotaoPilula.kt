package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * O botao de barra de ferramentas: pilula que mede o proprio texto.
 *
 * E o `.fc-button` do site -- o mesmo desenho dos controles do calendario e da
 * barra dos post-its. Diferente de [BotaoPrimario], que ocupa a largura toda:
 * aquele e o "faca isto" da tela, este convive com vizinhos numa linha, e por
 * isso nao estica.
 *
 * Nasceu privado dentro de `PostitsScreen`. Quando a barra do calendario
 * precisou dos mesmos botoes, copiar teria criado duas versoes que sairiam de
 * sincronia na primeira mudanca de cor -- e a regra da constituicao para o CSS
 * ("esta em duas telas, vira componente") vale igual aqui.
 */
@Composable
fun BotaoPilula(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    destacado: Boolean = false,
) {
    val cores = Doce
    val forma = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .clip(forma)
            .background(
                if (destacado) {
                    Brush.linearGradient(listOf(cores.destaque, cores.destaqueClaro))
                } else {
                    SolidColor(cores.fundoCampo)
                }
            )
            .border(1.dp, if (destacado) Color.Transparent else cores.traco, forma)
            .clickable(onClick = aoTocar)
            .padding(horizontal = Espaco.e3, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 13.sp),
            color = if (destacado) Color.White else cores.tinta,
        )
    }
}
