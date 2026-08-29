package com.beazeth.notifier.ui.telas.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.local.EventoEntity
import com.beazeth.notifier.ui.componentes.BotaoPilula
import com.beazeth.notifier.ui.telas.corDoHex
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * A grade do mes, no desenho do `dayGridMonth` do site.
 *
 * **Era uma lista, e a lista escondia o mes.** A primeira versao trocou a grade
 * por uma lista por dia, com o argumento de que numa tela de 360 dp cada celula
 * teria 45 dp e o titulo do evento nao caberia em nenhuma. O argumento estava
 * certo sobre o titulo e errado sobre o resto: quem abre um calendario nao vem
 * ler titulos, vem ver a FORMA do mes -- que semana esta cheia, quantos dias ha
 * ate o proximo compromisso, se o feriado cai na sexta.
 *
 * **E depois a celula de 44 dp foi para o tablet e nao cresceu junto.** Naquela
 * altura cabiam um numero e tres pontinhos, e num aparelho de 1280x800 dp isso
 * virou um calendario de brinquedo no canto de um cartao vazio. As medidas
 * daqui agora sao as do site, na mesma conta: [ALTURA_POR_LARGURA] e o `11cqi`
 * do CSS, [ALTURA_MINIMA] e [ALTURA_MAXIMA] sao o `clamp` em volta dele. Com a
 * grade ocupando a largura do cartao (~920 dp no tablet, os mesmos ~925 px que
 * o site tem numa janela do mesmo tamanho), a celula da ~100 dp -- e ai cabe o
 * que o site poe dentro dela: o evento com hora e titulo, e nao um ponto.
 *
 * **Tambem por isso os dias dos meses vizinhos aparecem.** Com a celula grande,
 * o buraco onde a semana comeca no meio pesa; e ver "31" na primeira linha e o
 * que diz de que semana se trata.
 *
 * **As celulas se medem em coordenadas de JANELA.** [aoMedirDia] devolve os
 * limites de cada dia para a tela saber onde o dedo largou um evento arrastado.
 * Janela e nao local porque quem arrasta esta noutro pedaco da arvore -- a
 * lista, que rola por conta propria -- e coordenada local de um nao diz nada
 * sobre a do outro.
 */
@Composable
internal fun GradeDoMes(
    mes: YearMonth,
    porDia: Map<String, List<EventoEntity>>,
    selecionado: LocalDate?,
    hoje: LocalDate,
    aoTrocarMes: (YearMonth) -> Unit,
    aoEscolherDia: (LocalDate) -> Unit,
    aoTocarEvento: (EventoEntity) -> Unit,
    aoCriar: () -> Unit,
    modifier: Modifier = Modifier,
    diaAlvo: LocalDate? = null,
    alturaDasSemanas: Dp = Dp.Unspecified,
    aoMedirDia: (LocalDate, Rect) -> Unit = { _, _ -> },
) {
    val cores = Doce

    // Segunda e a primeira coluna, como no planner e no to-do. O site comeca no
    // domingo porque o FullCalendar em pt-br comeca ali, mas dentro do app a
    // semana ja tinha um comeco -- e duas semanas diferentes em duas telas
    // vizinhas confundem mais do que a diferenca para o site.
    val primeiro = mes.atDay(1)
    val vazias = primeiro.dayOfWeek.value - 1
    val dias = mes.lengthOfMonth()
    val linhas = (vazias + dias + 6) / 7

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val estreito = maxWidth < LARGURA_PARA_BARRA_NUMA_LINHA
        val altura = alturaDaCelula(maxWidth, alturaDasSemanas, linhas)
        val larguraDaCelula = maxWidth / 7

        Column(verticalArrangement = Arrangement.spacedBy(Espaco.e3)) {
            BarraDoMes(
                mes = mes,
                estreito = estreito,
                aoTrocarMes = aoTrocarMes,
                aoIrParaHoje = { aoTrocarMes(YearMonth.from(hoje)) },
                aoCriar = aoCriar,
                rotuloDeCriar = rotuloDeCriar(selecionado, estreito),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Canto.caixa))
                    .border(1.dp, cores.traco, RoundedCornerShape(Canto.caixa)),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (nome in DIAS_DA_SEMANA) {
                        Text(
                            text = nome,
                            style = TipografiaBeazeth.labelLarge.copy(fontSize = 13.sp),
                            color = cores.tintaSuave,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .separadores(cores.traco)
                                .padding(vertical = Espaco.e2, horizontal = Espaco.e1),
                        )
                    }
                }

                for (linha in 0 until linhas) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (coluna in 0 until 7) {
                            val data = primeiro.plusDays(
                                (linha * 7 + coluna - vazias).toLong()
                            )
                            val doMes = YearMonth.from(data) == mes
                            CelulaDoDia(
                                data = data,
                                doMes = doMes,
                                eventos = porDia[data.toString()].orEmpty(),
                                ehHoje = data == hoje,
                                escolhido = data == selecionado,
                                alvo = data == diaAlvo,
                                altura = altura,
                                comHora = larguraDaCelula >= LARGURA_PARA_A_HORA,
                                aoTocar = {
                                    if (!doMes) aoTrocarMes(YearMonth.from(data))
                                    aoEscolherDia(data)
                                },
                                aoTocarEvento = aoTocarEvento,
                                aoMedir = { aoMedirDia(data, it) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A barra de controles: navegacao, mes e a acao de criar.
 *
 * E o `headerToolbar` do site sem os botoes de Mes/Semana/Dia, que aqui nao
 * existem -- o app so tem a visao de mes, e um grupo de tres botoes onde dois
 * nao levam a lugar nenhum e pior que nenhum botao.
 *
 * **O criar deixou de ser uma faixa.** Ele era um `BotaoPrimario` de largura
 * cheia embaixo da grade; num cartao de 920 dp isso e uma tarja roxa de um
 * palmo para uma acao que cabe num botao. Aqui ele fica no canto da barra, que
 * e onde o site tambem o poe.
 *
 * Numa largura de celular os tres pedacos nao cabem lado a lado, entao o titulo
 * sobe para uma linha propria -- o mesmo refluxo que o `flex-wrap` da barra faz
 * no site, so que decidido pela largura em vez de descoberto por acidente.
 */
@Composable
private fun BarraDoMes(
    mes: YearMonth,
    estreito: Boolean,
    aoTrocarMes: (YearMonth) -> Unit,
    aoIrParaHoje: () -> Unit,
    aoCriar: () -> Unit,
    rotuloDeCriar: String,
) {
    val titulo = @Composable { modificador: Modifier ->
        Text(
            text = "${MESES[mes.monthValue - 1].replaceFirstChar { it.uppercase() }} ${mes.year}",
            style = TipografiaBeazeth.headlineMedium,
            color = Doce.tinta,
            textAlign = if (estreito) TextAlign.Start else TextAlign.Center,
            maxLines = 1,
            modifier = modificador,
        )
    }

    val controles = @Composable {
        BotaoPilula(texto = "‹", aoTocar = { aoTrocarMes(mes.minusMonths(1)) })
        BotaoPilula(texto = "Hoje", aoTocar = aoIrParaHoje)
        BotaoPilula(texto = "›", aoTocar = { aoTrocarMes(mes.plusMonths(1)) })
    }

    if (estreito) {
        Column(verticalArrangement = Arrangement.spacedBy(Espaco.e2)) {
            titulo(Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                controles()
                Box(modifier = Modifier.weight(1f))
                BotaoPilula(texto = rotuloDeCriar, destacado = true, aoTocar = aoCriar)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            controles()
            titulo(Modifier.weight(1f))
            BotaoPilula(texto = rotuloDeCriar, destacado = true, aoTocar = aoCriar)
        }
    }
}

@Composable
private fun CelulaDoDia(
    data: LocalDate,
    doMes: Boolean,
    eventos: List<EventoEntity>,
    ehHoje: Boolean,
    escolhido: Boolean,
    alvo: Boolean,
    altura: Dp,
    comHora: Boolean,
    aoTocar: () -> Unit,
    aoTocarEvento: (EventoEntity) -> Unit,
    aoMedir: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce

    // Quantos eventos cabem: o que sobra da altura depois do numero, dividido
    // pela linha do evento. Contado e nao fixado em tres porque a mesma celula
    // tem 76 dp num celular e 124 num tablet -- numero fixo sobraria numa e
    // vazaria na outra.
    val espacoDeEventos = altura - ALTURA_DO_NUMERO - Espaco.e1 * 2
    val cabem = (espacoDeEventos / ALTURA_DO_EVENTO).toInt().coerceAtLeast(0)
    val mostrados = if (eventos.size > cabem) (cabem - 1).coerceAtLeast(0) else cabem

    Column(
        modifier = modifier
            .height(altura)
            .separadores(cores.traco)
            .onGloballyPositioned { aoMedir(it.boundsInWindow()) }
            .background(
                when {
                    alvo -> cores.destaque.copy(alpha = 0.45f)
                    escolhido -> cores.destaque.copy(alpha = 0.16f)
                    ehHoje -> cores.destaque.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (alvo || escolhido) {
                    Modifier.border(2.dp, cores.destaqueEscuro)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = aoTocar)
            .padding(Espaco.e1),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        // O numero fica na ponta direita, como no site. Alinhado ao fim e nao
        // centrado porque e o canto que sobra: o meio da celula e dos eventos.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (ehHoje) cores.destaqueEscuro else Color.Transparent)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${data.dayOfMonth}",
                    style = TipografiaBeazeth.labelLarge.copy(fontSize = 14.sp),
                    color = when {
                        ehHoje -> Color.White
                        !doMes -> cores.tintaSuave.copy(alpha = 0.5f)
                        else -> cores.tinta
                    },
                )
            }
        }

        for (evento in eventos.take(mostrados)) {
            LinhaNaCelula(
                evento = evento,
                comHora = comHora,
                aoTocar = { aoTocarEvento(evento) },
            )
        }

        if (eventos.size > mostrados) {
            Text(
                text = "+${eventos.size - mostrados} mais",
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
                color = cores.tintaSuave,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}

/**
 * O evento dentro da celula: ponto da cor da tag, hora e titulo.
 *
 * E o desenho do `fc-daygrid-event` para evento com hora -- ponto e texto, e
 * nao um retangulo pintado. Retangulo pintado o site reserva para evento de dia
 * inteiro, que aqui nao existe: `event_datetime` e sempre um instante.
 */
@Composable
private fun LinhaNaCelula(evento: EventoEntity, comHora: Boolean, aoTocar: () -> Unit) {
    val cores = Doce

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ALTURA_DO_EVENTO)
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = aoTocar)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(corDoHex(evento.tagColor)),
        )
        // A hora sai quando a coluna e estreita. Ela e a primeira a cair
        // porque, disputando 70 dp com o titulo, o que sobrava era "19:00 …" --
        // a hora inteira e nenhuma letra do que o evento e. O titulo diz qual
        // compromisso e; a hora esta na lista ao lado, e no editor.
        if (comHora) {
            Text(
                text = horaDoEvento(evento),
                style = TipografiaBeazeth.labelLarge.copy(fontSize = 13.sp),
                color = cores.tinta,
                maxLines = 1,
            )
        }
        Text(
            text = evento.title,
            style = TipografiaBeazeth.labelLarge.copy(fontSize = 13.sp),
            color = cores.tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * As linhas da grade, do jeito que uma tabela as desenha: uma so entre duas
 * celulas.
 *
 * Dar borda inteira a cada celula somaria a da direita com a da esquerda da
 * vizinha, e a grade sairia com o dobro da espessura nos meios. Aqui cada
 * celula traca so o proprio fim; a moldura de fora e do conteiner.
 *
 * A cor entra por parametro porque dentro de `drawBehind` nao ha composicao --
 * ler `Doce` ali nao compila.
 */
private fun Modifier.separadores(cor: Color): Modifier = drawBehind {
    val fio = 1.dp.toPx()
    drawLine(cor, Offset(size.width, 0f), Offset(size.width, size.height), fio)
    drawLine(cor, Offset(0f, size.height), Offset(size.width, size.height), fio)
}

/**
 * A altura da celula.
 *
 * Duas contas, e vale a menor. A primeira e a do site: `clamp(76px, 11cqi,
 * 124px)`, ou seja, a altura acompanha a largura do cartao para o quadrado nao
 * achatar quando a coluna encolhe.
 *
 * A segunda so existe aqui: **o mes inteiro tem que caber na tela.** O site
 * rola, e rolar pagina longa e natural no navegador; um mes que exige arrastar
 * para ver a ultima semana nao e um mes, e meio mes. Quando a tela diz quanto
 * sobra, a celula encolhe ate as seis linhas caberem -- nunca abaixo do minimo,
 * senao o remedio viraria a doenca.
 */
private fun alturaDaCelula(largura: Dp, disponivel: Dp, linhas: Int): Dp {
    val doSite = (largura * ALTURA_POR_LARGURA).coerceIn(ALTURA_MINIMA, ALTURA_MAXIMA)
    if (!disponivel.value.isFinite() || linhas <= 0) return doSite
    return doSite.coerceAtMost((disponivel / linhas).coerceAtLeast(ALTURA_MINIMA))
}

/**
 * A hora do evento no formato do site (`eventTimeFormat` com `hour12: false`).
 *
 * Devolve `--:--` quando o texto do servidor vem estranho, em vez de lancar: um
 * evento com data torta ainda e um evento, e sumir com ele da grade seria pior
 * do que mostra-lo sem hora.
 */
internal fun horaDoEvento(evento: EventoEntity): String = runCatching {
    LocalDateTime.parse(evento.eventDatetime).format(FORMATO_DA_HORA)
}.getOrDefault("--:--")

private val FORMATO_DA_HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** O rotulo do botao de criar, que ja diz a data quando ha uma escolhida. */
private fun rotuloDeCriar(selecionado: LocalDate?, estreito: Boolean): String = when {
    selecionado == null -> "Novo evento"
    estreito -> "Novo em %02d/%02d".format(selecionado.dayOfMonth, selecionado.monthValue)
    else -> "Novo evento em %02d/%02d".format(
        selecionado.dayOfMonth, selecionado.monthValue,
    )
}

/** `11cqi`: a celula tem ~1/7 da largura, e a altura fica em tres quartos dela. */
private const val ALTURA_POR_LARGURA = 0.11f
private val ALTURA_MINIMA = 76.dp
private val ALTURA_MAXIMA = 124.dp

/** O que o numero do dia ocupa no topo da celula, ja com o respiro da pilula. */
private val ALTURA_DO_NUMERO = 24.dp

/** A linha de um evento, na medida do site (21 px). */
private val ALTURA_DO_EVENTO = 21.dp

/** Abaixo desta largura de CELULA, a hora do evento nao cabe junto do titulo. */
private val LARGURA_PARA_A_HORA = 110.dp

/** Abaixo disto o titulo do mes nao cabe entre os botoes e sobe uma linha. */
private val LARGURA_PARA_BARRA_NUMA_LINHA = 560.dp

/** Segunda primeiro, como o planner e como o `todayIndex` do site. */
internal val DIAS_DA_SEMANA = listOf("SEG", "TER", "QUA", "QUI", "SEX", "SÁB", "DOM")

internal val MESES = listOf(
    "janeiro", "fevereiro", "março", "abril", "maio", "junho",
    "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
)

internal val MESES_CURTOS = listOf(
    "jan", "fev", "mar", "abr", "mai", "jun",
    "jul", "ago", "set", "out", "nov", "dez",
)

internal val DIAS_LONGOS = listOf(
    "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo",
)
