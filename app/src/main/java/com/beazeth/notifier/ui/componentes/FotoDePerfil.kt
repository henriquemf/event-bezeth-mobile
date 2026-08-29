package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.Perfil
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * O rosto de quem usa o app: a foto escolhida, ou a inicial do nome.
 *
 * Aparece em tres lugares com o mesmo desenho e tamanhos diferentes -- a
 * lateral, a tela de perfil e a barra de cima dos aparelhos sem lateral. Por
 * isso e um componente, e por isso o unico parametro obrigatorio e o tamanho.
 *
 * ## Sem foto nao ha buraco
 *
 * A alternativa nao e um cinza vazio nem o boneco padrao do Android: e a
 * inicial do nome sobre o degrade da marca. Assim a lateral nunca parece
 * quebrada antes de alguem escolher uma foto, e o circulo ja ocupa o lugar
 * certo -- trocar depois nao remexe o desenho da tela.
 */
@Composable
fun FotoDePerfil(
    nome: String,
    tamanho: Dp,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val foto by lembrarFoto()

    Box(
        modifier = modifier
            .size(tamanho)
            .clip(CircleShape)
            // O anel e o mesmo `--stroke` das caixas, e nao uma cor nova: o
            // circulo tem de parecer da mesma familia dos cartoes em volta.
            .border(1.5.dp, cores.traco, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val imagem = foto
        if (imagem != null) {
            Image(
                bitmap = imagem,
                contentDescription = "Foto de perfil",
                // `Crop` e nao `Fit`: a foto ja foi cortada no quadrado ao ser
                // gravada, mas um circulo sobre um quadrado ainda corta os
                // cantos -- e `Fit` deixaria faixas vazias nas laterais.
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(tamanho),
            )
        } else {
            Inicial(nome = nome, tamanho = tamanho)
        }
    }
}

/**
 * A foto, carregada do disco fora da thread da interface.
 *
 * `produceState` com a versao na chave: gravar uma foto nova sobe o contador e
 * o efeito roda de novo. Sem isso, a foto trocada so apareceria ao reabrir o
 * app -- o caminho do arquivo e sempre o mesmo (ver [Perfil]).
 */
@Composable
private fun lembrarFoto(): State<ImageBitmap?> {
    val contexto = LocalContext.current.applicationContext
    val perfil = remember { Perfil(contexto) }
    val versao by perfil.versaoDaFoto.collectAsState(initial = 0L)

    return produceState<ImageBitmap?>(initialValue = null, versao) {
        value = if (versao == 0L) null else perfil.foto()
    }
}

/** A inicial sobre o degrade da marca, para quem ainda nao escolheu foto. */
@Composable
private fun Inicial(nome: String, tamanho: Dp) {
    val cores = Doce
    val letra = nome.trim().firstOrNull()?.uppercase() ?: "•"

    Box(
        modifier = Modifier
            .size(tamanho)
            .drawBehind {
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(cores.destaque, cores.azulSuave),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                    radius = size.minDimension / 2f,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = letra,
            // A letra acompanha o circulo: a mesma proporcao serve para 26 dp
            // na barra de cima e para 96 dp na tela de perfil.
            style = TipografiaBeazeth.titleLarge.copy(fontSize = (tamanho.value * 0.42f).sp),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}
