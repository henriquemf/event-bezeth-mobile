package com.beazeth.notifier.ui.telas

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.local.ConfigAguaEntity
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.componentes.LinhaComChave
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * Onde o lembrete de agua se liga e se ajusta.
 *
 * ## Por que esta tela precisou existir
 *
 * Ate a 1.7.1 a configuracao da agua chegava ao app **so pela sincronizacao**,
 * e o padrao do banco do servidor e `enabled = FALSE`. Na pratica: quem
 * instalava o app e nao abria o site num computador nunca recebia lembrete de
 * agua nenhum -- e nao havia nada na tela dizendo por que. Quem usa sem conta
 * nao tinha nem essa saida.
 *
 * Os cinco campos sao os mesmos do formulario do site (`/hydration`), com os
 * mesmos limites, e o que se grava aqui sobe pela fila como qualquer outra
 * escrita. Mudar no celular muda no site na proxima sincronizacao, e vice-versa.
 *
 * ## Os sliders so gravam ao soltar
 *
 * `onValueChange` dispara a cada pixel de arrasto. Gravando ali, um unico
 * arrasto do intervalo daria umas cem gravacoes no Room, cem pedidos na fila e
 * cem recalculos de alarme. O valor em transito vive num `remember` local e o
 * commit acontece em `onValueChangeFinished`, quando o dedo sai.
 */

/** Espelham `MIN_INTERVAL`/`MAX_INTERVAL` e os limites de `app/db/hydration.py`. */
private const val INTERVALO_MINIMO = 5
private const val INTERVALO_MAXIMO = 180
private const val META_MINIMA = 1
private const val META_MAXIMA = 20
private const val COPO_MINIMO = 50
private const val COPO_MAXIMO = 1000
private const val PASSO_DO_COPO = 50

/**
 * A configuracao que vale quando ainda nao ha nenhuma gravada.
 *
 * Os numeros sao os do `CREATE TABLE` do servidor (`hydration_settings`), para
 * quem liga o lembrete pelo celular comecar exatamente onde o site comecaria.
 */
internal val CONFIG_DE_AGUA_PADRAO = ConfigAguaEntity(
    enabled = false,
    dailyGoal = 8,
    glassMl = 250,
    intervalMinutes = 60,
    startTime = "08:00",
    endTime = "22:00",
)

@Composable
internal fun CartaoDoLembrete(
    config: ConfigAguaEntity?,
    aoMudar: (ConfigAguaEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val atual = config ?: CONFIG_DE_AGUA_PADRAO

    CartaoDaTela(modifier = modifier, titulo = "Lembrete") {
        LinhaComChave(
            titulo = "Lembrar de beber",
            descricao = if (atual.enabled) {
                "Chega na barra de notificação, com o app fechado."
            } else {
                "Desligado. Ligue para ser lembrada de beber água."
            },
            marcado = atual.enabled,
            aoMudar = { aoMudar(atual.copy(enabled = it)) },
        )

        // Desligado, o resto some: sao ajustes de uma coisa que nao esta
        // acontecendo, e mante-los na tela e convidar a mexer neles achando que
        // ligam alguma coisa.
        if (!atual.enabled) return@CartaoDaTela

        Regua(
            rotulo = "A cada",
            valor = "${atual.intervalMinutes} min",
            posicao = atual.intervalMinutes.coerceIn(INTERVALO_MINIMO, INTERVALO_MAXIMO).toFloat(),
            faixa = INTERVALO_MINIMO.toFloat()..INTERVALO_MAXIMO.toFloat(),
            passos = (INTERVALO_MAXIMO - INTERVALO_MINIMO) / 5 - 1,
            aoSoltar = { aoMudar(atual.copy(intervalMinutes = it.toInt())) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            Horario(
                rotulo = "Das",
                hora = atual.startTime,
                aoEscolher = { aoMudar(atual.copy(startTime = it)) },
                modifier = Modifier.weight(1f),
            )
            Horario(
                rotulo = "Até",
                hora = atual.endTime,
                aoEscolher = { aoMudar(atual.copy(endTime = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "Fora dessa faixa o app fica quieto. Depois da meta do dia " +
                "também: o próximo lembrete só volta quando a janela reabrir.",
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
            color = cores.tintaSuave,
        )

        Regua(
            rotulo = "Meta do dia",
            valor = "${atual.dailyGoal} copos",
            posicao = atual.dailyGoal.coerceIn(META_MINIMA, META_MAXIMA).toFloat(),
            faixa = META_MINIMA.toFloat()..META_MAXIMA.toFloat(),
            passos = META_MAXIMA - META_MINIMA - 1,
            aoSoltar = { aoMudar(atual.copy(dailyGoal = it.toInt())) },
        )

        Regua(
            rotulo = "Tamanho do copo",
            valor = "${atual.glassMl} ml",
            posicao = atual.glassMl.coerceIn(COPO_MINIMO, COPO_MAXIMO).toFloat(),
            faixa = COPO_MINIMO.toFloat()..COPO_MAXIMO.toFloat(),
            passos = (COPO_MAXIMO - COPO_MINIMO) / PASSO_DO_COPO - 1,
            aoSoltar = { aoMudar(atual.copy(glassMl = it.toInt())) },
        )
    }
}

/** Um rotulo, o valor por extenso e a regua embaixo. */
@Composable
private fun Regua(
    rotulo: String,
    valor: String,
    posicao: Float,
    faixa: ClosedFloatingPointRange<Float>,
    passos: Int,
    aoSoltar: (Float) -> Unit,
) {
    val cores = Doce

    // Enquanto o dedo arrasta, quem manda e este valor local; ao soltar, o
    // commit devolve a palavra para o que esta gravado. A chave do `remember` e
    // a posicao vinda de fora para o slider acompanhar uma mudanca que veio da
    // sincronizacao.
    var emTransito by remember(posicao) { mutableFloatStateOf(posicao) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = rotulo,
                style = TipografiaBeazeth.labelLarge,
                color = cores.tintaSuave,
            )
            Text(
                text = valor,
                style = TipografiaBeazeth.titleMedium,
                color = cores.tinta,
            )
        }
        Slider(
            value = emTransito,
            onValueChange = { emTransito = it },
            onValueChangeFinished = { aoSoltar(emTransito) },
            valueRange = faixa,
            steps = passos.coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = cores.destaque,
                activeTrackColor = cores.destaque,
                inactiveTrackColor = cores.destaque.copy(alpha = 0.22f),
            ),
        )
    }
}

/**
 * Uma ponta da janela, que abre o relogio do sistema ao ser tocada.
 *
 * `TimePickerDialog` do proprio Android, e nao um seletor desenhado aqui: e o
 * mesmo relogio que a pessoa ja usa para pôr um despertador, respeita o formato
 * de 12 ou 24 horas do aparelho e nao custa nenhuma linha de layout.
 */
@Composable
private fun Horario(
    rotulo: String,
    hora: String,
    aoEscolher: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val contexto = LocalContext.current
    val partes = hora.split(":")
    val h = partes.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
    val m = partes.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Canto.caixa))
            .clickable {
                TimePickerDialog(
                    contexto,
                    { _, hEscolhida, mEscolhido ->
                        aoEscolher("%02d:%02d".format(hEscolhida, mEscolhido))
                    },
                    h,
                    m,
                    android.text.format.DateFormat.is24HourFormat(contexto),
                ).show()
            }
            .padding(Espaco.e2),
    ) {
        Text(
            text = rotulo,
            style = TipografiaBeazeth.labelLarge,
            color = cores.tintaSuave,
        )
        Box(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = hora,
                style = TipografiaBeazeth.headlineMedium.copy(fontSize = 22.sp),
                color = cores.tinta,
            )
        }
    }
}
