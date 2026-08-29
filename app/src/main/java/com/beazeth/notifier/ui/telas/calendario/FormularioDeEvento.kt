// Os seletores de data e hora do Material 3 ainda sao experimentais. Optar
// por eles e deliberado: escrever um calendario e um relogio a mao custaria
// centenas de linhas e perderia o que vem de graca -- teclado numerico,
// leitor de tela, e o gesto que a pessoa ja conhece de outros apps.
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.beazeth.notifier.ui.telas.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beazeth.notifier.data.local.EventoEntity
import com.beazeth.notifier.data.local.TagEntity
import com.beazeth.notifier.ui.componentes.AvisoDeErro
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CampoDoce
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.componentes.EscolhaDeHora
import com.beazeth.notifier.ui.componentes.SeletorEmCaixa
import com.beazeth.notifier.ui.telas.corDoHex
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** O formato que o servidor espera em `event_datetime`. */
internal val FORMATO_ENVIO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/**
 * O formulario de evento, em janela.
 *
 * Janela e nao tela propria porque criar evento e um desvio curto de quem esta
 * olhando a agenda: uma rota nova empilharia historico e faria o botao de
 * voltar do Android significar "cancelar", que nao e obvio.
 *
 * **[dataInicial] e o `dateClick` do site.** Tocar num dia da grade abre este
 * formulario JA naquela data -- o caminho que antes passava por escolher a data
 * de novo no seletor, depois de ja ter apontado para ela.
 */
@Composable
internal fun FormularioDeEvento(
    evento: EventoEntity?,
    dataInicial: LocalDate?,
    tags: List<TagEntity>,
    aoFechar: () -> Unit,
    aoSalvar: (String, String, String, String) -> Unit,
) {
    val cores = Doce
    val agora = LocalDateTime.now()
    val inicial = evento?.let {
        runCatching { LocalDateTime.parse(it.eventDatetime) }.getOrNull()
    } ?: dataInicial?.atTime(agora.hour, 0)?.plusHours(1)
    ?: agora.plusHours(1).withMinute(0)

    var titulo by remember { mutableStateOf(evento?.title.orEmpty()) }
    var descricao by remember { mutableStateOf(evento?.description.orEmpty()) }
    var data by remember { mutableStateOf(inicial.toLocalDate()) }
    var hora by remember { mutableStateOf(inicial.hour) }
    var minuto by remember { mutableStateOf(inicial.minute) }
    var tag by remember { mutableStateOf(evento?.tagType ?: tags.firstOrNull()?.slug ?: "evento") }
    var aviso by remember { mutableStateOf<String?>(null) }

    var escolhendoData by remember { mutableStateOf(false) }
    var escolhendoHora by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = aoFechar) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            item {
                CartaoDaTela(titulo = if (evento == null) "Novo evento" else "Editar evento") {
                    CampoDoce(
                        rotulo = "Título",
                        valor = titulo,
                        aoMudar = { titulo = it; aviso = null },
                    )
                    CampoDoce(
                        rotulo = "Descrição",
                        valor = descricao,
                        aoMudar = { descricao = it },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                    ) {
                        SeletorEmCaixa(
                            rotulo = "Data",
                            valor = "%02d/%02d/%d".format(
                                data.dayOfMonth, data.monthValue, data.year,
                            ),
                            aoTocar = { escolhendoData = true },
                            modifier = Modifier.weight(1f),
                        )
                        SeletorEmCaixa(
                            rotulo = "Hora",
                            valor = "%02d:%02d".format(hora, minuto),
                            aoTocar = { escolhendoHora = true },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (tags.isNotEmpty()) {
                        Text(
                            text = "Tag",
                            style = TipografiaBeazeth.labelLarge,
                            color = cores.tintaSuave,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
                        ) {
                            for (t in tags) {
                                PilulaDeTag(
                                    tag = t,
                                    ativa = t.slug == tag,
                                    aoTocar = { tag = t.slug },
                                )
                            }
                        }
                    }

                    if (aviso != null) {
                        AvisoDeErro(aviso!!)
                    }

                    BotaoPrimario(
                        texto = if (evento == null) "Criar evento" else "Salvar",
                        aoTocar = {
                            val quando = LocalDateTime.of(data, LocalTime.of(hora, minuto))
                            when {
                                titulo.isBlank() -> aviso = "Informe o título do evento."
                                // O servidor recusa data no passado; barrar aqui
                                // evita enfileirar uma escrita que voltaria
                                // recusada e sumiria da tela sem explicacao.
                                quando.isBefore(LocalDateTime.now()) ->
                                    aviso = "A data precisa estar no futuro."
                                else -> aoSalvar(
                                    titulo.trim(),
                                    descricao.trim(),
                                    quando.format(FORMATO_ENVIO),
                                    tag,
                                )
                            }
                        },
                    )

                    Text(
                        text = "Cancelar",
                        style = TipografiaBeazeth.titleMedium,
                        color = cores.tintaSuave,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = aoFechar)
                            .padding(horizontal = Espaco.e4, vertical = Espaco.e2),
                    )
                }
            }
        }
    }

    if (escolhendoData) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = data.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { escolhendoData = false },
            confirmButton = {
                Text(
                    text = "Pronto",
                    style = TipografiaBeazeth.titleMedium,
                    color = cores.destaqueEscuro,
                    modifier = Modifier
                        .clickable {
                            estado.selectedDateMillis?.let {
                                // O seletor devolve meia-noite UTC; converter
                                // pelo fuso local moveria a data um dia em
                                // metade do planeta.
                                data = Instant.ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC).toLocalDate()
                            }
                            escolhendoData = false
                        }
                        .padding(Espaco.e3),
                )
            },
            colors = DatePickerDefaults.colors(containerColor = cores.superficie),
        ) {
            DatePicker(
                state = estado,
                colors = DatePickerDefaults.colors(
                    containerColor = cores.superficie,
                    selectedDayContainerColor = cores.destaque,
                    todayDateBorderColor = cores.destaque,
                ),
            )
        }
    }

    if (escolhendoHora) {
        val estado = rememberTimePickerState(
            initialHour = hora,
            initialMinute = minuto,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { escolhendoHora = false }) {
            CartaoDaTela(titulo = "Hora") {
                EscolhaDeHora(estado = estado)
                BotaoPrimario(
                    texto = "Pronto",
                    aoTocar = {
                        hora = estado.hour
                        minuto = estado.minute
                        escolhendoHora = false
                    },
                )
            }
        }
    }
}

@Composable
private fun PilulaDeTag(tag: TagEntity, ativa: Boolean, aoTocar: () -> Unit) {
    val cor = corDoHex(tag.color)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (ativa) cor.copy(alpha = 0.28f) else Doce.fundoCampo)
            .border(
                1.dp,
                if (ativa) cor else Doce.traco,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = aoTocar)
            .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(cor),
        )
        Text(
            text = tag.label,
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 13.sp),
            color = Doce.tinta,
        )
    }
}
