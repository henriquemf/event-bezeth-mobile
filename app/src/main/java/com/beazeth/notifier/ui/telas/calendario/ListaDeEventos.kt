package com.beazeth.notifier.ui.telas.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.beazeth.notifier.data.local.EventoEntity
import com.beazeth.notifier.ui.LocalModoLocal
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.telas.corDoHex
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * "Proximos eventos": o texto que a grade nao cabe.
 *
 * E o `.upcoming-panel` do site, e por isso tem o mesmo cabecalho com a
 * contagem: a grade responde "como esta o mes", esta responde "o que vem, e o
 * que diz cada um".
 *
 * **Ela olha sempre de hoje para a frente.** Nao recebe o dia escolhido na
 * grade, e isso e de proposito: filtrar por ele fazia a lista de proximos
 * sumir no momento em que se tocava num dia para conferir uma data. Quem
 * escolhe um dia esta mirando a grade e o botao de criar; o que vem por ai nao
 * tem por que mudar junto.
 *
 * **Em coluna quando ha largura, e nao numa fita so.** Embaixo de uma grade de
 * 920 dp, um cartao de dia esticado na largura toda e uma linha de texto com um
 * palmo de vazio a direita. O site resolve com
 * `repeat(auto-fit, minmax(300px, 1fr))`; aqui a conta e a mesma, feita a mao
 * porque `LazyColumn` nao tem auto-fit -- os dias sao repartidos em fileiras de
 * [colunasQueCabem] e cada fileira e um item da lista, entao a preguica da
 * rolagem continua valendo.
 */
@Composable
internal fun ListaDeEventos(
    visiveis: Map<String, List<EventoEntity>>,
    hoje: LocalDate,
    arrastado: EventoEntity?,
    aoEditar: (EventoEntity) -> Unit,
    aoApagar: (EventoEntity) -> Unit,
    aoPegar: (EventoEntity, Offset) -> Unit,
    aoMover: (Offset) -> Unit,
    aoSoltar: () -> Unit,
    aoDesistir: () -> Unit,
    modifier: Modifier = Modifier,
    recuoInicial: Dp = Espaco.e5,
    cabecalho: (@Composable () -> Unit)? = null,
) {
    val total = visiveis.values.sumOf { it.size }

    BoxWithConstraints(modifier = modifier) {
        val colunas = colunasQueCabem(maxWidth - recuoInicial - Espaco.e5)
        val fileiras = visiveis.entries.toList().chunked(colunas)

        LazyColumn(
            contentPadding = PaddingValues(
                start = recuoInicial, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e5,
            ),
            verticalArrangement = Arrangement.spacedBy(Espaco.e3),
        ) {
            if (cabecalho != null) {
                item { cabecalho() }
            }

            item { CabecalhoDaLista(total = total) }

            if (visiveis.isEmpty()) {
                item { CartaoDeVazio() }
            }

            for (fileira in fileiras) {
                item(key = fileira.first().key) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e3),
                    ) {
                        for ((iso, doDia) in fileira) {
                            CartaoDoDia(
                                iso = iso,
                                doDia = doDia,
                                hoje = hoje,
                                arrastado = arrastado,
                                aoEditar = aoEditar,
                                aoApagar = aoApagar,
                                aoPegar = aoPegar,
                                aoMover = aoMover,
                                aoSoltar = aoSoltar,
                                aoDesistir = aoDesistir,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // As sobras da ultima fileira: sem elas os dois dias que
                        // restaram de uma fileira de tres se esticariam para
                        // metade da tela cada, e a coluna do meio saltaria.
                        repeat(colunas - fileira.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** O `.upcoming-header`: o nome da lista e quantos eventos ha nela. */
@Composable
private fun CabecalhoDaLista(total: Int) {
    val cores = Doce

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        Text(
            text = "Próximos eventos",
            style = TipografiaBeazeth.titleLarge,
            color = cores.tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (total > 0) {
            // Sem `fillMaxWidth` aqui dentro. Ele parece inofensivo -- centrar o
            // numero na pilula --, mas `fillMaxWidth` obedece a largura MAXIMA
            // que o pai oferece, e o pai e uma Row sem largura propria: a
            // pilula esticava de ponta a ponta, sobrava zero para o titulo ao
            // lado, e o titulo, espremido em largura zero, quebrava numa letra
            // por linha e empurrava a lista tres telas para baixo. O tamanho
            // minimo mais o alinhamento fazem o mesmo servico sem pedir largura
            // nenhuma emprestada.
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(cores.destaque.copy(alpha = 0.16f))
                    .border(1.dp, cores.destaque.copy(alpha = 0.32f), CircleShape)
                    .widthIn(min = 30.dp)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$total",
                    style = TipografiaBeazeth.labelLarge.copy(fontSize = 13.sp),
                    color = cores.tinta,
                )
            }
        }
    }
}

@Composable
private fun CartaoDeVazio() {
    CartaoDaTela {
        Text(
            text = "Nada marcado daqui para a frente.",
            style = TipografiaBeazeth.bodyLarge,
            color = Doce.tintaSuave,
        )
        Text(
            text = "Toque num dia do mês para criar um, ou espere a sincronização " +
                "trazer os que você criou no site.",
            style = TipografiaBeazeth.bodyMedium,
            color = Doce.tintaSuave,
        )
    }
}

@Composable
private fun CartaoDoDia(
    iso: String,
    doDia: List<EventoEntity>,
    hoje: LocalDate,
    arrastado: EventoEntity?,
    aoEditar: (EventoEntity) -> Unit,
    aoApagar: (EventoEntity) -> Unit,
    aoPegar: (EventoEntity, Offset) -> Unit,
    aoMover: (Offset) -> Unit,
    aoSoltar: () -> Unit,
    aoDesistir: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CartaoDaTela(modifier = modifier) {
        CabecalhoDoDia(iso = iso, hoje = hoje)
        for (evento in doDia) {
            LinhaDeEvento(
                evento = evento,
                sendoArrastado = evento.id == arrastado?.id,
                aoTocar = { aoEditar(evento) },
                aoApagar = { aoApagar(evento) },
                aoPegar = aoPegar,
                aoMover = aoMover,
                aoSoltar = aoSoltar,
                aoDesistir = aoDesistir,
            )
        }
    }
}

@Composable
private fun CabecalhoDoDia(iso: String, hoje: LocalDate) {
    val data = runCatching { LocalDate.parse(iso) }.getOrNull()
    val ehHoje = data == hoje

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        Text(
            text = data?.let { "%02d".format(it.dayOfMonth) } ?: "—",
            style = TipografiaBeazeth.headlineMedium.copy(fontSize = 26.sp),
            color = if (ehHoje) Doce.destaqueEscuro else Doce.tinta,
        )
        Column {
            Text(
                text = data?.let { "${MESES_CURTOS[it.monthValue - 1]} ${it.year}" } ?: iso,
                style = TipografiaBeazeth.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Doce.tintaSuave,
            )
            Text(
                text = if (ehHoje) {
                    "Hoje"
                } else {
                    data?.let { DIAS_LONGOS[it.dayOfWeek.value - 1] }.orEmpty()
                },
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                color = if (ehHoje) Doce.destaqueEscuro else Doce.tintaSuave,
            )
        }
    }
}

@Composable
private fun LinhaDeEvento(
    evento: EventoEntity,
    sendoArrastado: Boolean,
    aoTocar: () -> Unit,
    aoApagar: () -> Unit,
    aoPegar: (EventoEntity, Offset) -> Unit,
    aoMover: (Offset) -> Unit,
    aoSoltar: () -> Unit,
    aoDesistir: () -> Unit,
) {
    val tato = LocalHapticFeedback.current

    // Onde esta linha esta NA JANELA. O dedo comeca aqui e termina noutro
    // pedaco da arvore (a grade), entao os dois lados precisam falar a mesma
    // lingua de coordenadas.
    var lugar by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { lugar = it }
            .clip(RoundedCornerShape(12.dp))
            .background(corDoHex(evento.tagColor).copy(alpha = if (sendoArrastado) 0.05f else 0.14f))
            .clickable(onClick = aoTocar)
            // Segurar e arrastar leva o evento para outro dia. Segurar, e nao
            // arrastar direto: a lista rola no mesmo eixo, e um arrasto simples
            // seria ambiguo entre "rolar" e "mover o evento".
            .pointerInput(evento.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        tato.performHapticFeedback(HapticFeedbackType.LongPress)
                        aoPegar(evento, lugar?.localToWindow(local) ?: Offset.Zero)
                    },
                    onDragEnd = { aoSoltar() },
                    onDragCancel = { aoDesistir() },
                ) { mudanca, deslocamento ->
                    mudanca.consume()
                    aoMover(deslocamento)
                }
            }
            .padding(Espaco.e2),
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = horaDoEvento(evento),
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 14.sp),
            color = Doce.tinta,
            modifier = Modifier.padding(top = 2.dp),
        )

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(corDoHex(evento.tagColor)),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = evento.title,
                style = TipografiaBeazeth.bodyLarge.copy(fontSize = 15.sp),
                color = Doce.tinta,
            )
            if (evento.description.isNotBlank()) {
                Text(
                    text = evento.description,
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 13.sp),
                    color = Doce.tintaSuave,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = evento.tagLabel,
                    style = TipografiaBeazeth.bodyMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Doce.tintaSuave,
                )
                // Ver o mesmo selo em `Postit`: sem conta ele nao diz nada.
                if (evento.id < 0 && !LocalModoLocal.current) {
                    Text(
                        text = "não enviado",
                        style = TipografiaBeazeth.bodyMedium.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Doce.tintaSuave,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = aoApagar),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "×", fontSize = 18.sp, color = Doce.tintaSuave)
        }
    }
}

/**
 * A copia que segue o dedo durante o arrasto.
 *
 * `Popup` porque ela precisa sair de dentro da lista -- que rola e recorta -- e
 * passar por cima da grade do mes, que e justamente o destino. E o `Popup` ja
 * pensa em coordenadas de janela, as mesmas em que o dedo e as celulas foram
 * medidos: nao ha conversao nenhuma pelo caminho.
 */
@Composable
internal fun FantasmaDoEvento(evento: EventoEntity, ponto: Offset) {
    val cor = corDoHex(evento.tagColor)
    Popup(
        popupPositionProvider = remember(ponto) {
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset = IntOffset(
                    (ponto.x - popupContentSize.width / 2f).roundToInt(),
                    (ponto.y - popupContentSize.height / 2f).roundToInt(),
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Doce.superficie)
                .border(1.dp, cor, RoundedCornerShape(12.dp))
                .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(cor),
            )
            Text(
                text = evento.title,
                style = TipografiaBeazeth.bodyLarge.copy(fontSize = 14.sp),
                color = Doce.tinta,
            )
        }
    }
}

/**
 * Quantas colunas de dia cabem na largura, com o piso do site (300 px).
 *
 * Teto de tres: com quatro, cada cartao ficaria mais estreito do que o titulo
 * de um evento precisa, e a lista viraria uma parede de reticencias.
 */
private fun colunasQueCabem(largura: Dp): Int =
    (largura / LARGURA_MINIMA_DO_DIA).toInt().coerceIn(1, 3)

private val LARGURA_MINIMA_DO_DIA = 300.dp
