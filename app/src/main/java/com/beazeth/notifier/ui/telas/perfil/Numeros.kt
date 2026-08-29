package com.beazeth.notifier.ui.telas.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.Estatisticas
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Os numeros do app, e as frases que eles permitem.
 *
 * ## Por que numero E frase
 *
 * "412" sozinho nao diz nada; "412 copos" ja diz; "412 copos, uns 103 litros"
 * e o que faz alguem parar para ler. Os quadradinhos dao a olhada rapida, as
 * frases embaixo dao o motivo de voltar aqui.
 *
 * ## O que nao tem dado nao aparece
 *
 * Cada frase so entra se tiver o que dizer. Uma tela de "0 pomodoros, 0
 * litros, 0 tarefas" e uma tela dizendo que o app nao serviu para nada -- e no
 * primeiro dia isso nem seria verdade, so seria cedo.
 */
@Composable
internal fun Numeros(e: Estatisticas) {
    val cores = Doce

    val quadros = buildList {
        conta(e.coposAoTodo, "copo de água", "copos de água", "💧")
        conta(e.tarefasFeitas, "tarefa riscada", "tarefas riscadas", "✅")
        conta(e.pomodoros, "pomodoro", "pomodoros", "🍎")
        conta(e.postits, "post-it", "post-its", "🗒️")
        conta(e.eventos, "evento", "eventos", "📅")
        conta(e.blocos, "bloco", "blocos", "🗓️")
    }

    val frases = buildList {
        if (e.minutosFocados > 0) {
            add(
                "Você passou ${duracao(e.minutosFocados)} focando" +
                    (e.focandoDesde?.let { ", desde ${dia(it)}" } ?: "") + "."
            )
        }
        if (e.coposAoTodo > 0 && e.mlPorCopo > 0) {
            add(
                "Isso dá uns ${litros(e.litros)} de água — em " +
                    plural(e.diasBebendo, "dia", "dias") + "."
            )
        }
        if (e.diasNaMeta > 0) {
            add(
                "Você bateu a meta de ${e.metaDeCopos} copos em " +
                    (if (e.diasNaMeta == 1) "um deles" else "${e.diasNaMeta} deles") + "."
            )
        }
        if (e.recordeDeCopos > 0) {
            add("Seu recorde num dia só: " + plural(e.recordeDeCopos, "copo", "copos") + ".")
        }
        if (e.tarefasAoTodo > 0) {
            val porcento = e.tarefasFeitas * 100 / e.tarefasAoTodo
            add("De ${e.tarefasAoTodo} tarefas escritas, $porcento% foram riscadas.")
        }
        if (e.minutosDaSemana > 0) {
            add("Sua semana no planner tem ${duracao(e.minutosDaSemana)} marcados.")
        }
    }

    if (!e.temAlgumaCoisa) {
        Text(
            text = "Ainda não há o que contar. Beba um copo de água, risque uma " +
                "tarefa, deixe um pomodoro terminar — os números aparecem aqui.",
            style = TipografiaBeazeth.bodyMedium,
            color = cores.tintaSuave,
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val colunas = (maxWidth / LARGURA_MINIMA).toInt().coerceIn(2, 4)

        Column(verticalArrangement = Arrangement.spacedBy(Espaco.e2)) {
            quadros.chunked(colunas).forEach { linha ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                ) {
                    for (q in linha) {
                        QuadroDeNumero(quadro = q, modifier = Modifier.weight(1f))
                    }
                    // Completa a ultima fileira: sem isto, dois quadros numa
                    // fileira de quatro ficariam esticados ate a metade da tela
                    // cada um, e a grade perderia o alinhamento com a de cima.
                    repeat(colunas - linha.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (frases.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Espaco.e1)) {
            for (frase in frases) {
                Text(
                    text = frase,
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                )
            }
        }
    }
}

private data class Quadro(val valor: String, val rotulo: String, val icone: String)

/** Acrescenta o quadrinho se houver o que contar, ja com o rotulo no numero certo. */
private fun MutableList<Quadro>.conta(
    quantos: Int,
    singular: String,
    plural: String,
    icone: String,
) {
    if (quantos > 0) {
        add(Quadro("$quantos", if (quantos == 1) singular else plural, icone))
    }
}

/** "1 dia", "5 dias" -- o numero e a palavra combinando. */
private fun plural(quantos: Int, singular: String, plural: String): String =
    "$quantos " + if (quantos == 1) singular else plural

@Composable
private fun QuadroDeNumero(quadro: Quadro, modifier: Modifier = Modifier) {
    val cores = Doce
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Canto.caixa))
            .background(cores.fundoCampo)
            .border(1.dp, cores.traco, RoundedCornerShape(Canto.caixa))
            .padding(vertical = Espaco.e3, horizontal = Espaco.e2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = quadro.icone, fontSize = 15.sp)
        Text(
            text = quadro.valor,
            style = TipografiaBeazeth.titleLarge.copy(fontSize = 24.sp),
            color = cores.tinta,
            maxLines = 1,
        )
        Text(
            text = quadro.rotulo,
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
            color = cores.tintaSuave,
            textAlign = TextAlign.Center,
        )
    }
}

/** "3 h 40 min", "40 min", "2 h" -- sem o zero que ninguem fala. */
private fun duracao(minutos: Int): String {
    val horas = minutos / 60
    val resto = minutos % 60
    return when {
        horas == 0 -> "$resto min"
        resto == 0 -> "$horas h"
        else -> "$horas h $resto min"
    }
}

/**
 * Um decimal so ate 10 litros; dai em diante o decimal e ruido.
 *
 * `Locale` explicito porque o app e em portugues em qualquer aparelho: sem ele
 * o separador sai do idioma do SISTEMA, e um tablet em ingles mostraria "1.3 L"
 * no meio de uma frase em portugues.
 */
private fun litros(valor: Float): String =
    if (valor < 10f) "%.1f L".format(BRASIL, valor) else "%.0f L".format(BRASIL, valor)

private val BRASIL = Locale("pt", "BR")

private fun dia(instante: Long): String =
    Instant.ofEpochMilli(instante).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

/** Abaixo disto o numero e o rotulo nao cabem lado a lado sem quebrar feio. */
private val LARGURA_MINIMA: Dp = 150.dp
