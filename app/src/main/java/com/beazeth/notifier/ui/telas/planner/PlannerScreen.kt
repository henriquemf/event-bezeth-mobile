package com.beazeth.notifier.ui.telas.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.local.BlocoEntity
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * O planner da semana: barra de controles em cima, [GradeDaSemana] embaixo.
 *
 * Os controles sao os mesmos do site (fins de semana, horario util, zoom, novo
 * bloco), com dois ajustes de aparelho: o zoom e de botao e nao de barra
 * deslizante, e a barra rola de lado em vez de quebrar em duas linhas -- cada
 * linha de controle e uma linha a menos de grade, que e o que se veio ver.
 *
 * **Os padroes sao outros, de proposito.** No site o planner abre com os fins
 * de semana escondidos e as 24 horas a mostra. Aqui abre com a semana inteira
 * (e o que se pede a um planner semanal no celular) e na faixa util de 6h as
 * 23h -- com 24 horas na tela, o zoom padrao poe as 09:00 a mais de uma tela de
 * distancia do topo, e o planner abre mostrando a madrugada.
 */
@Composable
fun PlannerScreen(vm: PlannerViewModel = viewModel()) {
    val blocos by vm.blocos.collectAsState()
    val cores = Doce

    var fimDeSemana by rememberSaveable { mutableStateOf(true) }
    var horarioUtil by rememberSaveable { mutableStateOf(true) }
    // Guardado como Float e nao Dp: `rememberSaveable` grava no Bundle, e Dp
    // nao e um tipo que o Bundle conheca.
    var zoom by rememberSaveable { mutableFloatStateOf(ZOOM_PADRAO.value) }

    // `null` = editor fechado.
    var editando by remember { mutableStateOf<BlocoEntity?>(null) }

    val dias = remember(fimDeSemana) { if (fimDeSemana) (0..6).toList() else (0..4).toList() }
    val faixa = if (horarioUtil) FAIXA_UTIL else FAIXA_COMPLETA
    val alturaDaHora = zoom.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // As colunas se abrem para ocupar a tela quando cabem, e param na
        // largura minima quando nao cabem -- e ai a grade rola de lado. E a
        // mesma regra do `1fr` do site, com um piso: abaixo de
        // [COLUNA_MINIMA] o titulo de um bloco nao cabe em coluna nenhuma.
        val larguraDaColuna = maxOf(COLUNA_MINIMA, (maxWidth - CALHA) / dias.size)

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
                horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chave(
                    texto = "Fins de semana",
                    ligado = fimDeSemana,
                    aoTocar = { fimDeSemana = !fimDeSemana },
                )
                Chave(
                    texto = "Horário útil",
                    ligado = horarioUtil,
                    aoTocar = { horarioUtil = !horarioUtil },
                )
                BotaoDeZoom(
                    texto = "−",
                    habilitado = alturaDaHora > ZOOM_MINIMO,
                    aoTocar = { zoom = (alturaDaHora - ZOOM_PASSO).coerceAtLeast(ZOOM_MINIMO).value },
                )
                BotaoDeZoom(
                    texto = "+",
                    habilitado = alturaDaHora < ZOOM_MAXIMO,
                    aoTocar = { zoom = (alturaDaHora + ZOOM_PASSO).coerceAtMost(ZOOM_MAXIMO).value },
                )
                BotaoDaBarra(
                    texto = "Novo bloco",
                    destacado = true,
                    aoTocar = { editando = blocoEmBranco(diaDeHoje()) },
                )
            }

            Text(
                text = "Toque na grade para marcar · toque num bloco para editar · " +
                    "segure e arraste para mover.",
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
                color = cores.tintaSuave,
                modifier = Modifier.padding(horizontal = Espaco.e3, vertical = 2.dp),
            )

            GradeDaSemana(
                blocos = blocos,
                dias = dias,
                faixa = faixa,
                alturaDaHora = alturaDaHora,
                larguraDaColuna = larguraDaColuna,
                aoTocarBloco = { editando = it },
                aoTocarVazio = { dia, minuto ->
                    editando = blocoEmBranco(dia).copy(
                        startMinute = minuto,
                        endMinute = (minuto + 60).coerceAtMost(MINUTOS_DO_DIA),
                    )
                },
                aoMover = { bloco, dia, inicio -> vm.mover(bloco, dia, inicio) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }

    editando?.let { alvo ->
        EditorDeBloco(
            rascunho = alvo,
            novo = alvo.id == 0L,
            aoFechar = { editando = null },
            aoSalvar = { vm.salvar(it) },
            aoApagar = {
                vm.apagar(alvo)
                editando = null
            },
        )
    }
}

/** O bloco que ainda nao existe. Id zero; quem grava troca por um provisorio
 *  negativo. Nove da manha as dez e a faixa em que a maior parte cai. */
private fun blocoEmBranco(dia: Int) = BlocoEntity(
    id = 0L, title = "", notes = "", dayOfWeek = dia,
    startMinute = 9 * 60, endMinute = 10 * 60,
    color = CORES_DO_BLOCO.first(), isRoutine = false,
)

@Composable
private fun Chave(texto: String, ligado: Boolean, aoTocar: () -> Unit) {
    BotaoDaBarra(texto = (if (ligado) "✓ " else "") + texto, ativo = ligado, aoTocar = aoTocar)
}

@Composable
private fun BotaoDeZoom(texto: String, habilitado: Boolean, aoTocar: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Doce.fundoCampo)
            .border(1.dp, Doce.traco, RoundedCornerShape(999.dp))
            .clickable(enabled = habilitado, onClick = aoTocar)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = texto,
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 14.sp),
            color = if (habilitado) Doce.tinta else Doce.tintaSuave.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun BotaoDaBarra(
    texto: String,
    destacado: Boolean = false,
    ativo: Boolean = false,
    aoTocar: () -> Unit,
) {
    val cores = Doce
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    destacado -> Brush.linearGradient(listOf(cores.destaque, cores.destaqueClaro))
                    ativo -> SolidColor(cores.destaque.copy(alpha = 0.20f))
                    else -> SolidColor(cores.fundoCampo)
                }
            )
            .border(
                1.dp,
                when {
                    destacado -> Color.Transparent
                    ativo -> cores.destaque
                    else -> cores.traco
                },
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = aoTocar)
            .padding(horizontal = Espaco.e3, vertical = 7.dp),
    ) {
        Text(
            text = texto,
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 12.sp),
            color = when {
                destacado -> Color.White
                ativo -> cores.destaqueEscuro
                else -> cores.tinta
            },
            maxLines = 1,
        )
    }
}
