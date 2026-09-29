package com.beazeth.notifier.ui.componentes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.radio.ControleDaRadio
import com.beazeth.notifier.radio.ESTACOES
import com.beazeth.notifier.radio.EstadoDaRadio
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * A radio lo-fi no canto da tela -- o mesmo player do site
 * (`partials/radio.html`), com as mesmas tres formas:
 * - parada: um botao redondo com o radinho, para nao tapar nada;
 * - tocando: a pilula com as ondas e o nome da musica;
 * - aberta: o cartao com estacoes, volume e o link da radio.
 *
 * O som nao mora aqui: e do `RadioService`. Sair da tela, trocar de aba ou
 * fechar o app nao para a musica -- isto so desenha e aperta botoes.
 */
@Composable
fun PlayerDaRadio(controle: ControleDaRadio, modifier: Modifier = Modifier) {
    val estado by controle.estado.collectAsState()
    var aberto by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        AnimatedVisibility(
            visible = aberto,
            enter = fadeIn() + scaleIn(initialScale = 0.96f, transformOrigin = TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(targetScale = 0.96f, transformOrigin = TransformOrigin(1f, 1f)),
        ) {
            CartaoDaRadio(estado, controle, aoFechar = { aberto = false })
        }
        Pilula(estado, aoAbrir = { aberto = !aberto }, aoAlternar = controle::alternar)
    }
}

@Composable
private fun Pilula(estado: EstadoDaRadio, aoAbrir: () -> Unit, aoAlternar: () -> Unit) {
    val cores = Doce
    val forma = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .shadow(10.dp, forma, ambientColor = cores.sombra, spotColor = cores.sombra)
            .clip(forma)
            .background(cores.superficie)
            .border(1.dp, cores.traco, forma)
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(forma)
                .clickable(onClickLabel = "Abrir a radio", role = Role.Button, onClick = aoAbrir)
                .semantics { contentDescription = "Rádio lo-fi" }
                .height(40.dp)
                .padding(horizontal = if (estado.tocando) Espaco.e2 else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            if (estado.tocando) {
                Ondas(tocando = true, cor = cores.destaque, altura = 16.dp)
                Column(modifier = Modifier.widthIn(max = 170.dp)) {
                    Text(
                        text = estado.musica?.titulo ?: estado.estacao.nome,
                        style = TipografiaBeazeth.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                        color = cores.tinta,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = estado.musica?.artista?.ifEmpty { null } ?: estado.estacao.nome,
                        style = TipografiaBeazeth.bodySmall.copy(fontSize = 11.sp),
                        color = cores.tintaSuave,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text("📻", fontSize = 19.sp)
            }
        }
        BotaoTocar(estado, tamanho = 40.dp, aoTocar = aoAlternar)
    }
}

@Composable
private fun CartaoDaRadio(estado: EstadoDaRadio, controle: ControleDaRadio, aoFechar: () -> Unit) {
    val cores = Doce
    val forma = RoundedCornerShape(18.dp)
    val abrirLink = LocalUriHandler.current

    Column(
        modifier = Modifier
            .widthIn(max = 330.dp)
            .shadow(16.dp, forma, ambientColor = cores.sombra, spotColor = cores.sombra)
            .clip(forma)
            .background(cores.superficie)
            .border(1.dp, cores.traco, forma)
            .padding(Espaco.e3),
        verticalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "📻 Rádio lo-fi",
                style = TipografiaBeazeth.titleMedium,
                color = cores.tinta,
                modifier = Modifier.weight(1f),
            )
            BotaoRedondo(simbolo = "—", descricao = "Recolher a rádio", tamanho = 34.dp, aoTocar = aoFechar)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Espaco.e2)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(cores.destaque, cores.azulSuave))),
                contentAlignment = Alignment.Center,
            ) {
                Ondas(tocando = estado.tocando, cor = Color.White, altura = 24.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = estado.musica?.titulo ?: estado.estacao.nome,
                    style = TipografiaBeazeth.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = cores.tinta,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    text = when {
                        estado.carregando -> "conectando…"
                        estado.tocando -> estado.musica?.artista?.ifEmpty { null } ?: estado.estacao.descricao
                        else -> "toque para ouvir"
                    },
                    style = TipografiaBeazeth.bodySmall,
                    color = cores.tintaSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // O credito da radio, e o caminho ate ela.
                Text(
                    text = "${estado.estacao.nome} · ${estado.estacao.fonte} ↗",
                    style = TipografiaBeazeth.bodySmall.copy(fontSize = 11.sp),
                    color = cores.destaqueEscuro,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClickLabel = "Abrir o site da rádio", role = Role.Button) {
                            abrirLink.openUri(estado.estacao.site)
                        },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotaoRedondo("⏮", "Estação anterior", tamanho = 36.dp) { controle.vizinha(-1) }
            BotaoTocar(estado, tamanho = 44.dp, aoTocar = controle::alternar)
            BotaoRedondo("⏭", "Próxima estação", tamanho = 36.dp) { controle.vizinha(1) }
            Spacer(Modifier.width(2.dp))
            Slider(
                value = estado.volume,
                onValueChange = controle::definirVolume,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Volume" },
                colors = SliderDefaults.colors(
                    thumbColor = cores.destaque,
                    activeTrackColor = cores.destaque,
                    inactiveTrackColor = cores.traco,
                ),
            )
        }

        ESTACOES.chunked(2).forEach { par ->
            // Altura intrinseca: os dois cartoes da linha ficam da altura do
            // mais alto, sem `fillMaxHeight` esticar ate o fim da tela.
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                par.forEach { estacao ->
                    val escolhida = estacao.id == estado.estacao.id
                    val formaItem = RoundedCornerShape(12.dp)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(formaItem)
                            .background(if (escolhida) cores.destaque.copy(alpha = 0.14f) else Color.Transparent)
                            .border(1.dp, if (escolhida) cores.destaque else cores.traco, formaItem)
                            .clickable(role = Role.Button) { controle.escolher(estacao) }
                            .semantics { selected = escolhida }
                            .padding(horizontal = Espaco.e2, vertical = 8.dp),
                    ) {
                        Text(
                            estacao.nome,
                            style = TipografiaBeazeth.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = cores.tinta,
                        )
                        Text(
                            estacao.descricao,
                            style = TipografiaBeazeth.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                            color = cores.tintaSuave,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tocar e pausar, DESENHADOS. Os caracteres ▶ e ⏸ sairiam como emoji no
 * Android -- o pause vira um quadrado laranja no meio do botao rosa.
 */
@Composable
private fun BotaoTocar(estado: EstadoDaRadio, tamanho: Dp, aoTocar: () -> Unit) {
    val cores = Doce
    val pausar = estado.tocando || estado.carregando
    Box(
        modifier = Modifier
            .size(tamanho)
            .clip(RoundedCornerShape(999.dp))
            .background(cores.destaque)
            .clickable(role = Role.Button, onClick = aoTocar)
            .semantics { contentDescription = if (pausar) "Pausar a rádio" else "Tocar a rádio" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(tamanho * 0.36f)) {
            if (pausar) {
                val barra = size.width * 0.32f
                val raio = CornerRadius(barra / 3)
                drawRoundRect(Color.White, size = Size(barra, size.height), cornerRadius = raio)
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(size.width - barra, 0f),
                    size = Size(barra, size.height),
                    cornerRadius = raio,
                )
            } else {
                // O triangulo vai um fio para a direita: centrado pela caixa,
                // ele PARECE torto, porque o peso dele esta na base.
                val caminho = Path().apply {
                    moveTo(size.width * 0.12f, 0f)
                    lineTo(size.width, size.height / 2)
                    lineTo(size.width * 0.12f, size.height)
                    close()
                }
                drawPath(caminho, Color.White)
            }
        }
    }
}

@Composable
private fun BotaoRedondo(
    simbolo: String,
    descricao: String,
    tamanho: Dp,
    destacado: Boolean = false,
    aoTocar: () -> Unit,
) {
    val cores = Doce
    Box(
        modifier = Modifier
            .size(tamanho)
            .clip(RoundedCornerShape(999.dp))
            .background(if (destacado) cores.destaque else cores.destaque.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = aoTocar)
            .semantics { contentDescription = descricao },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            simbolo,
            color = if (destacado) Color.White else cores.tinta,
            fontSize = if (tamanho > 40.dp) 17.sp else 14.sp,
        )
    }
}

/**
 * As quatro barrinhas que sobem e descem com a musica.
 *
 * `graphicsLayer`, e nao altura animada: escala roda na GPU, sem recalcular
 * o layout da pilula a cada quadro -- a regra do `transform` da constituicao,
 * no Compose.
 */
@Composable
private fun Ondas(tocando: Boolean, cor: Color, altura: Dp) {
    Row(
        modifier = Modifier.height(altura),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val largura = if (altura > 20.dp) 4.dp else 3.dp
        // Parada, nada anima: uma transicao infinita pediria um quadro novo a
        // cada 16 ms para desenhar barras que nao mexem.
        if (tocando) {
            val transicao = rememberInfiniteTransition(label = "ondas")
            listOf(900, 700, 1100, 800).forEach { duracao ->
                val escala by transicao.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(duracao, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "onda",
                )
                Barra(largura, cor) { escala }
            }
        } else {
            repeat(4) { Barra(largura, cor) { 0.3f } }
        }
    }
}

/** A escala e lida DENTRO do `graphicsLayer`: a cada quadro so a camada e
 *  redesenhada, sem recompor nem remedir nada. */
@Composable
private fun Barra(largura: Dp, cor: Color, escala: () -> Float) {
    Box(
        modifier = Modifier
            .width(largura)
            .fillMaxHeight()
            .graphicsLayer {
                scaleY = escala()
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clip(RoundedCornerShape(2.dp))
            .background(cor),
    )
}
