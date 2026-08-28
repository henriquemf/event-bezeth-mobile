package com.beazeth.notifier.ui.telas.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.local.EventoEntity
import com.beazeth.notifier.ui.telas.corDoHex
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import java.time.LocalDate
import java.time.YearMonth

/**
 * A grade do mes, como o `dayGridMonth` do FullCalendar no site.
 *
 * **Era uma lista, e a lista escondia o mes.** A primeira versao trocou a grade
 * por uma lista por dia, com o argumento de que numa tela de 360 dp cada celula
 * teria 45 dp e o titulo do evento nao caberia em nenhuma. O argumento estava
 * certo sobre o titulo e errado sobre o resto: quem abre um calendario nao vem
 * ler titulos, vem ver a FORMA do mes -- que semana esta cheia, quantos dias ha
 * ate o proximo compromisso, se o feriado cai na sexta. A lista responde "o que
 * vem depois"; so a grade responde "como esta o mes".
 *
 * Entao aqui estao as duas: a grade mostra a forma, com um ponto colorido por
 * evento (a cor e a da tag, igual ao site), e a lista embaixo mostra o texto.
 * Tocar num dia liga uma na outra.
 *
 * **Tres pontos e um "+N".** Um dia com onze eventos viraria uma celula de
 * pontos ilegiveis; tres pontos e uma contagem dizem a mesma coisa e cabem.
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
    modifier: Modifier = Modifier,
    diaAlvo: LocalDate? = null,
    aoMedirDia: (LocalDate, Rect) -> Unit = { _, _ -> },
) {
    val cores = Doce

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetaDoMes(texto = "‹", aoTocar = { aoTrocarMes(mes.minusMonths(1)) })
            Text(
                text = "${MESES[mes.monthValue - 1]} ${mes.year}",
                style = TipografiaBeazeth.titleMedium.copy(fontSize = 15.sp),
                color = cores.tinta,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            SetaDoMes(texto = "›", aoTocar = { aoTrocarMes(mes.plusMonths(1)) })
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            for (nome in DIAS_DA_SEMANA) {
                Text(
                    text = nome,
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 10.sp),
                    color = cores.tintaSuave,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                )
            }
        }

        // Segunda e a primeira coluna, como no resto do app e no planner.
        // `dayOfWeek.value` ja e 1 para segunda, entao o deslocamento e ele
        // menos um.
        val primeiro = mes.atDay(1)
        val vazias = primeiro.dayOfWeek.value - 1
        val dias = mes.lengthOfMonth()
        val celulas = ((vazias + dias + 6) / 7) * 7

        for (linha in 0 until celulas / 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (coluna in 0 until 7) {
                    val indice = linha * 7 + coluna
                    val numero = indice - vazias + 1
                    if (numero in 1..dias) {
                        val data = mes.atDay(numero)
                        CelulaDoDia(
                            data = data,
                            eventos = porDia[data.toString()].orEmpty(),
                            ehHoje = data == hoje,
                            escolhido = data == selecionado,
                            alvo = data == diaAlvo,
                            aoTocar = { aoEscolherDia(data) },
                            aoMedir = { aoMedirDia(data, it) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(ALTURA_DA_CELULA),
                        )
                    }
                }
            }
        }
    }
}

private val ALTURA_DA_CELULA = 44.dp

@Composable
private fun CelulaDoDia(
    data: LocalDate,
    eventos: List<EventoEntity>,
    ehHoje: Boolean,
    escolhido: Boolean,
    alvo: Boolean,
    aoTocar: () -> Unit,
    aoMedir: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce

    Column(
        modifier = modifier
            .height(ALTURA_DA_CELULA)
            .padding(1.dp)
            .onGloballyPositioned { aoMedir(it.boundsInWindow()) }
            .clip(RoundedCornerShape(Canto.marca))
            .background(
                when {
                    alvo -> cores.destaque.copy(alpha = 0.45f)
                    escolhido -> cores.destaque.copy(alpha = 0.22f)
                    eventos.isNotEmpty() -> cores.fundoCampo
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (alvo || ehHoje) 2.dp else 1.dp,
                color = when {
                    alvo -> cores.destaqueEscuro
                    escolhido -> cores.destaque
                    ehHoje -> cores.destaqueEscuro
                    else -> cores.traco.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(Canto.marca),
            )
            .clickable(onClick = aoTocar)
            .padding(top = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${data.dayOfMonth}",
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
            color = if (ehHoje || escolhido) cores.destaqueEscuro else cores.tinta,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (evento in eventos.take(3)) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(corDoHex(evento.tagColor)),
                )
            }
            if (eventos.size > 3) {
                Text(
                    text = "+${eventos.size - 3}",
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 8.sp),
                    color = cores.tintaSuave,
                )
            }
        }
    }
}

@Composable
private fun SetaDoMes(texto: String, aoTocar: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = texto, fontSize = 20.sp, color = Doce.tintaSuave)
    }
}

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
