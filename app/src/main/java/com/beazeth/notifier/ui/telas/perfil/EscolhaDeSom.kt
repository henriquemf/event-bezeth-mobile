package com.beazeth.notifier.ui.telas.perfil

import android.media.Ringtone
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.avisos.OpcaoDeSom
import com.beazeth.notifier.avisos.tocarUmaVez
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * A lista de toques, com botao de ouvir em cada um.
 *
 * **Ouvir antes de escolher nao e enfeite.** Um nome como "Gotinha" nao diz se
 * o som e discreto ou irritante, e o unico jeito de descobrir sem isto seria
 * escolher, esperar o proximo lembrete e trocar de novo -- o que, no caso da
 * agenda, pode levar dias.
 *
 * A previa toca pelo canal de NOTIFICACAO do aparelho, e nao pelo de midia:
 * assim o volume que se ouve aqui e o mesmo que vai chegar depois, e nao o
 * volume da musica.
 */
@Composable
internal fun EscolhaDeSom(
    opcoes: List<OpcaoDeSom>,
    escolhida: OpcaoDeSom,
    aoEscolher: (OpcaoDeSom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    var tocando by remember { mutableStateOf<Ringtone?>(null) }

    // Sair da tela com o toque no meio deixaria o som tocando sozinho por cima
    // da tela seguinte.
    DisposableEffect(Unit) {
        onDispose { tocando?.stop() }
    }

    fun ouvir(som: OpcaoDeSom) {
        // Parar o anterior antes: tocar dois de uma vez nao deixa julgar
        // nenhum dos dois.
        tocando?.stop()
        tocando = som.uri(contexto)?.let { tocarUmaVez(contexto, it) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Espaco.e1),
    ) {
        for (som in opcoes) {
            LinhaDeSom(
                som = som,
                ativo = som == escolhida,
                aoTocar = { aoEscolher(som) },
                aoOuvir = { ouvir(som) },
            )
        }
    }
}

@Composable
private fun LinhaDeSom(
    som: OpcaoDeSom,
    ativo: Boolean,
    aoTocar: () -> Unit,
    aoOuvir: () -> Unit,
) {
    val cores = Doce
    val forma = RoundedCornerShape(Canto.caixa)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (ativo) cores.destaque.copy(alpha = 0.18f) else cores.fundoCampo)
            .border(1.dp, if (ativo) cores.destaque else cores.traco, forma)
            .clickable(onClick = aoTocar)
            .padding(horizontal = Espaco.e2, vertical = Espaco.e2),
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Bolinha(marcada = ativo)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = som.nome,
                style = TipografiaBeazeth.titleMedium,
                color = if (ativo) cores.destaqueEscuro else cores.tinta,
            )
            Text(
                text = som.descricao,
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                color = cores.tintaSuave,
            )
        }

        // "Sem som" nao tem o que ouvir, e um botao que nao faz nada e pior do
        // que botao nenhum.
        if (som.uri(LocalContext.current) != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(cores.superficie)
                    .border(1.dp, cores.traco, CircleShape)
                    .clickable(onClick = aoOuvir)
                    .padding(horizontal = Espaco.e2, vertical = Espaco.e1),
            ) {
                Text(
                    text = "Ouvir",
                    style = TipografiaBeazeth.labelLarge.copy(fontSize = 12.sp),
                    color = cores.destaqueEscuro,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** O ponto de "escolhido", no lugar de um `RadioButton` do Material. */
@Composable
private fun Bolinha(marcada: Boolean) {
    val cores = Doce
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(if (marcada) cores.destaque else cores.superficie)
            .border(1.dp, if (marcada) cores.destaque else cores.traco, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (marcada) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(cores.superficie),
            )
        }
    }
}
