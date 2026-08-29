package com.beazeth.notifier.ui.telas.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.beazeth.notifier.data.local.BlocoEntity
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * A semana inteira, em colunas, como no site.
 *
 * **Era um dia por vez, e isso escondia o planner.** A primeira versao mostrava
 * uma aba por dia e uma lista de blocos, com o argumento de que sete colunas
 * nao cabem em 360 dp. Cabem -- o que nao cabe e a tela inteira de uma vez, e
 * essa e a diferenca entre "nao da" e "rola de lado". Um planner semanal existe
 * para mostrar a FORMA da semana: o buraco da terca, as tres horas seguidas de
 * quinta. Uma lista de um dia apaga exatamente isso.
 *
 * Entao a grade e a mesma do site -- cabecalho de dias, regua de horas, blocos
 * em escala de tempo -- e o que se adapta e o enquadramento: as colunas tem
 * largura minima legivel ([COLUNA_MINIMA]) e o quadro rola de lado quando nao
 * cabem. Numa tela larga (celular deitado, tablet) elas se abrem e ocupam tudo,
 * e ai nao ha o que rolar.
 *
 * ## Os dois cabecalhos que nao rolam
 *
 * A regua de horas e a fila de dias precisam ficar presas: rolar ate quinta e
 * perder de vista que aquilo e quinta seria pior do que nao rolar. As duas sao
 * copias que compartilham o `ScrollState` da area central com `enabled = false`
 * -- elas nao capturam gesto nenhum, so acompanham o deslocamento de quem
 * captura.
 *
 * ## Sobreposicao
 *
 * Dois blocos no mesmo horario dividem a largura da coluna, como no
 * FullCalendar. Sem isso o de baixo fica escondido e some da tela sem aviso --
 * o pior tipo de defeito, porque nao parece defeito.
 *
 * ## Arrastar
 *
 * Segurar e arrastar move o bloco. Tem de ser SEGURAR e nao arrastar direto: a
 * grade esta dentro de dois roladores, e um arrasto simples e ambiguo entre
 * "mover o bloco" e "rolar o quadro". Segurar desempata sem que a pessoa
 * precise mirar em nada.
 *
 * Redimensionar continua so no formulario. Uma alca de borda util pede uns
 * 24 dp, e um bloco de 15 minutos tem 13 dp de altura no zoom padrao -- a alca
 * seria maior que o bloco.
 */
@Composable
internal fun GradeDaSemana(
    blocos: List<BlocoEntity>,
    dias: List<Int>,
    faixa: IntRange,
    alturaDaHora: Dp,
    larguraDaColuna: Dp,
    aoTocarBloco: (BlocoEntity) -> Unit,
    aoTocarVazio: (Int, Int) -> Unit,
    aoMover: (BlocoEntity, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val densidade = LocalDensity.current
    val rolarX = rememberScrollState()
    val rolarY = rememberScrollState()
    val hoje = remember { diaDeHoje() }

    val inicioDaFaixa = faixa.first
    val horas = (faixa.last - faixa.first) / 60
    val alturaTotal = alturaDaHora * horas
    val larguraTotal = larguraDaColuna * dias.size

    val porMinuto = with(densidade) { alturaDaHora.toPx() } / 60f
    val colunaEmPx = with(densidade) { larguraDaColuna.toPx() }

    // O risco de "agora". Um relogio parado num planner e pior que nenhum
    // relogio, entao ele se atualiza -- de meio em meio minuto, que e o passo
    // que ninguem ve acontecer.
    var agora by remember { mutableIntStateOf(minutoDeAgora()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            agora = minutoDeAgora()
        }
    }

    // Abre em hoje e perto de agora. Sem isto, a semana comeca na segunda de
    // madrugada: para ver o proprio dia era preciso rolar nos dois eixos antes
    // de a tela servir para alguma coisa.
    LaunchedEffect(dias, faixa, alturaDaHora, larguraDaColuna) {
        // Duas passadas de quadro: antes da primeira medida o `maxValue` dos
        // roladores ainda e zero, e rolar para 300 pararia em 0.
        repeat(2) { withFrameNanos { } }
        val colunaDeHoje = dias.indexOf(hoje)
        if (colunaDeHoje > 0) rolarX.scrollTo((colunaDeHoje * colunaEmPx).roundToInt())
        val alvo = ((agora - inicioDaFaixa - 60) * porMinuto).roundToInt()
        if (alvo > 0) rolarY.scrollTo(alvo)
    }

    val tintaDeHoje = cores.destaque.copy(alpha = 0.07f)
    val linhaCheia = cores.traco
    val linhaMeia = cores.traco.copy(alpha = 0.45f)
    val corDeAgora = cores.destaqueEscuro

    Column(modifier = modifier) {
        // ------------------------------------------------- cabecalho dos dias
        Row {
            Spacer(modifier = Modifier.width(CALHA))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rolarX, enabled = false),
            ) {
                for (dia in dias) {
                    CabecalhoDoDia(
                        dia = dia,
                        ehHoje = dia == hoje,
                        largura = larguraDaColuna,
                    )
                }
            }
        }

        Row(modifier = Modifier.weight(1f)) {
            // --------------------------------------------- regua de horas
            Column(
                modifier = Modifier
                    .width(CALHA)
                    .verticalScroll(rolarY, enabled = false),
            ) {
                for (h in 0 until horas) {
                    Box(
                        modifier = Modifier
                            .width(CALHA)
                            .height(alturaDaHora),
                    ) {
                        Text(
                            text = comoHora(inicioDaFaixa + h * 60),
                            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 10.sp),
                            color = cores.tintaSuave,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                // Sobe meia linha para o rotulo ficar EM CIMA
                                // do traco da hora, e nao logo abaixo dele.
                                .offset(y = (-6).dp)
                                .padding(end = 6.dp),
                        )
                    }
                }
            }

            // --------------------------------------------------- o quadro
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rolarX)
                    .verticalScroll(rolarY),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = larguraTotal, height = alturaTotal)
                        .drawBehind {
                            val colunaPx = size.width / dias.size
                            val horaPx = size.height / horas

                            dias.forEachIndexed { indice, dia ->
                                if (dia == hoje) {
                                    drawRect(
                                        color = tintaDeHoje,
                                        topLeft = Offset(indice * colunaPx, 0f),
                                        size = Size(colunaPx, size.height),
                                    )
                                }
                            }

                            for (h in 0..horas) {
                                val y = h * horaPx
                                drawLine(linhaCheia, Offset(0f, y), Offset(size.width, y))
                                if (h < horas) {
                                    val meia = y + horaPx / 2f
                                    drawLine(
                                        linhaMeia,
                                        Offset(0f, meia),
                                        Offset(size.width, meia),
                                    )
                                }
                            }

                            for (i in 0..dias.size) {
                                val x = i * colunaPx
                                drawLine(linhaCheia, Offset(x, 0f), Offset(x, size.height))
                            }

                            val colunaDeHoje = dias.indexOf(hoje)
                            if (colunaDeHoje >= 0 && agora in inicioDaFaixa..faixa.last) {
                                val y = (agora - inicioDaFaixa) * horaPx / 60f
                                drawLine(
                                    color = corDeAgora,
                                    start = Offset(colunaDeHoje * colunaPx, y),
                                    end = Offset((colunaDeHoje + 1) * colunaPx, y),
                                    strokeWidth = 2.dp.toPx(),
                                )
                            }
                        }
                        // Tocar no vazio ABRE O FORMULARIO naquele dia e hora,
                        // e nao cria o bloco direto. No site o gesto e arrastar,
                        // que ninguem faz sem querer; um toque, dentro de uma
                        // area que tambem rola, e facil de disparar por engano
                        // -- e um bloco criado sozinho e pior que um toque
                        // perdido.
                        .pointerInput(dias, faixa, alturaDaHora, larguraDaColuna) {
                            detectTapGestures { ponto ->
                                val indice = (ponto.x / colunaEmPx).toInt()
                                    .coerceIn(0, dias.size - 1)
                                val minuto = arredondar(
                                    inicioDaFaixa + (ponto.y / porMinuto).roundToInt()
                                )
                                aoTocarVazio(dias[indice], minuto)
                            }
                        },
                ) {
                    for (item in distribuir(blocos, dias)) {
                        BlocoNaGrade(
                            item = item,
                            dias = dias,
                            inicioDaFaixa = inicioDaFaixa,
                            fimDaFaixa = faixa.last,
                            porMinuto = porMinuto,
                            colunaEmPx = colunaEmPx,
                            alturaDaHora = alturaDaHora,
                            larguraDaColuna = larguraDaColuna,
                            aoTocar = { aoTocarBloco(item.bloco) },
                            aoMover = { dia, inicio -> aoMover(item.bloco, dia, inicio) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDoDia(dia: Int, ehHoje: Boolean, largura: Dp) {
    val cores = Doce
    Column(
        modifier = Modifier
            .width(largura)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = DIAS_CURTOS[dia],
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 12.sp),
            color = if (ehHoje) cores.destaqueEscuro else cores.tinta,
        )
        Text(
            text = if (ehHoje) "hoje" else DIAS[dia],
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 10.sp),
            color = cores.tintaSuave,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Um bloco desenhado na grade.
 *
 * A posicao mostrada e do proprio bloco, e nao da linha do Room -- mesmo
 * conserto do post-it, e pelo mesmo motivo: zerar o arrasto antes de o banco
 * confirmar faz o bloco piscar de volta para onde estava.
 */
@Composable
private fun BlocoNaGrade(
    item: Posicionado,
    dias: List<Int>,
    inicioDaFaixa: Int,
    fimDaFaixa: Int,
    porMinuto: Float,
    colunaEmPx: Float,
    alturaDaHora: Dp,
    larguraDaColuna: Dp,
    aoTocar: () -> Unit,
    aoMover: (Int, Int) -> Unit,
) {
    val bloco = item.bloco
    val cores = Doce
    val cor = corDoPlanner(bloco.color)
    val tato = LocalHapticFeedback.current
    val densidade = LocalDensity.current

    // A duracao real, sem piso de meia grade: um bloco de 5 minutos agora
    // existe, e desenha-lo como se tivesse 15 o faria invadir o vizinho e
    // desmentir o proprio rotulo. Quem garante que ele continua tocavel e o
    // piso de 16 dp da ALTURA, que e o que o dedo precisa -- e nao o dado.
    val duracao = (bloco.endMinute - bloco.startMinute).coerceAtLeast(1)
    val larguraDaTrilha = larguraDaColuna / item.trilhas
    val altura = (alturaDaHora * (duracao / 60f)).coerceAtLeast(16.dp)

    val margem = with(densidade) { MARGEM.toPx() }
    val baseX = with(densidade) {
        (larguraDaColuna * item.coluna + larguraDaTrilha * item.trilha).toPx()
    } + margem
    val baseY = (bloco.startMinute - inicioDaFaixa) * porMinuto
    val larguraEmPx = with(densidade) { larguraDaTrilha.toPx() } - margem * 2
    val alturaEmPx = with(densidade) { altura.toPx() }
    val limiteX = colunaEmPx * dias.size - larguraEmPx - margem
    val limiteY = (fimDaFaixa - inicioDaFaixa) * porMinuto - alturaEmPx

    var x by remember(bloco.id, item.coluna) { mutableFloatStateOf(baseX) }
    var y by remember(bloco.id) { mutableFloatStateOf(baseY) }
    var arrastando by remember(bloco.id) { mutableStateOf(false) }
    var enviado by remember(bloco.id) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(baseX, baseY, arrastando, enviado) {
        if (arrastando) return@LaunchedEffect
        val alvo = enviado
        if (alvo == null) {
            x = baseX
            y = baseY
        } else if (bloco.dayOfWeek == alvo.first && bloco.startMinute == alvo.second) {
            enviado = null
        }
    }

    Box(
        modifier = Modifier
            .zIndex(if (arrastando) 2f else 1f)
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(width = larguraDaTrilha - MARGEM * 2, height = altura)
            .shadow(if (arrastando) 8.dp else 0.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(cor.copy(alpha = if (arrastando) 0.42f else 0.24f))
            .border(1.dp, cor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .clickable(onClick = aoTocar)
            .pointerInput(bloco.id, item.coluna, item.trilhas, porMinuto, colunaEmPx, dias) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        tato.performHapticFeedback(HapticFeedbackType.LongPress)
                        arrastando = true
                    },
                    onDragEnd = {
                        arrastando = false
                        val centro = x + larguraEmPx / 2f
                        val indice = (centro / colunaEmPx).toInt().coerceIn(0, dias.size - 1)
                        val destino = if (bloco.isRoutine) bloco.dayOfWeek else dias[indice]
                        val minuto = arredondar(
                            inicioDaFaixa + (y / porMinuto).roundToInt()
                        ).coerceAtMost(MINUTOS_DO_DIA - duracao)

                        // Encaixa o que se ve na grade, sempre -- mesmo quando
                        // nada mudou, senao o bloco fica dois pixels fora do
                        // lugar ate a proxima recomposicao.
                        y = (minuto - inicioDaFaixa) * porMinuto
                        if (destino != bloco.dayOfWeek || minuto != bloco.startMinute) {
                            enviado = destino to minuto
                            aoMover(destino, minuto)
                        } else {
                            x = baseX
                        }
                    },
                    onDragCancel = {
                        arrastando = false
                        x = baseX
                        y = baseY
                    },
                ) { mudanca, deslocamento ->
                    mudanca.consume()
                    if (!bloco.isRoutine) {
                        x = (x + deslocamento.x).coerceIn(0f, maxOf(0f, limiteX))
                    }
                    y = (y + deslocamento.y).coerceIn(0f, maxOf(0f, limiteY))
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Column {
            Text(
                text = bloco.title.ifBlank { "sem título" },
                style = TipografiaBeazeth.titleMedium.copy(fontSize = 11.sp),
                color = cores.tinta,
                maxLines = if (altura >= 40.dp) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (altura >= 34.dp) {
                Text(
                    text = comoHora(bloco.startMinute) + "–" + comoHora(bloco.endMinute),
                    style = TipografiaBeazeth.bodyMedium.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = cores.tintaSuave,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Folga entre o bloco e a linha da coluna, para os dois nao se encostarem. */
private val MARGEM = 2.dp

/** Um bloco com a coluna e a trilha que ocupa. */
private data class Posicionado(
    val bloco: BlocoEntity,
    val coluna: Int,
    val trilha: Int,
    val trilhas: Int,
)

/**
 * Reparte os blocos entre as colunas e resolve a sobreposicao.
 *
 * Bloco de rotina entra em TODAS as colunas -- e o que o site faz, e o motivo
 * de `parse_block_payload` ignorar `day_of_week` quando `is_routine`.
 *
 * Dentro de cada coluna, quem se sobrepoe divide a largura. O algoritmo e o de
 * sempre: agrupa o que se toca, e dentro do grupo poe cada bloco na primeira
 * trilha que ja tenha terminado.
 */
private fun distribuir(blocos: List<BlocoEntity>, dias: List<Int>): List<Posicionado> {
    val saida = mutableListOf<Posicionado>()

    dias.forEachIndexed { coluna, dia ->
        val doDia = blocos
            .filter { it.isRoutine || it.dayOfWeek == dia }
            .sortedWith(compareBy({ it.startMinute }, { it.endMinute }))

        var grupo = mutableListOf<BlocoEntity>()
        var fimDoGrupo = Int.MIN_VALUE

        fun fechar() {
            if (grupo.isEmpty()) return
            val fimDaTrilha = mutableListOf<Int>()
            val trilhaDe = IntArray(grupo.size)
            grupo.forEachIndexed { i, b ->
                val livre = fimDaTrilha.indexOfFirst { it <= b.startMinute }
                if (livre < 0) {
                    fimDaTrilha.add(b.endMinute)
                    trilhaDe[i] = fimDaTrilha.size - 1
                } else {
                    fimDaTrilha[livre] = b.endMinute
                    trilhaDe[i] = livre
                }
            }
            grupo.forEachIndexed { i, b ->
                saida.add(Posicionado(b, coluna, trilhaDe[i], fimDaTrilha.size))
            }
            grupo = mutableListOf()
            fimDoGrupo = Int.MIN_VALUE
        }

        for (b in doDia) {
            if (grupo.isNotEmpty() && b.startMinute >= fimDoGrupo) fechar()
            grupo.add(b)
            fimDoGrupo = maxOf(fimDoGrupo, b.endMinute)
        }
        fechar()
    }

    return saida
}

private fun minutoDeAgora(): Int = LocalTime.now().let { it.hour * 60 + it.minute }
