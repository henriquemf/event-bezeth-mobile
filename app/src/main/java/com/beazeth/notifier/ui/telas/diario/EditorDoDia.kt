package com.beazeth.notifier.ui.telas.diario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.local.DiarioEntity
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CampoDoce
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.delay
import java.time.LocalDate

/**
 * O dia aberto: escolher o humor, escrever, guardar.
 *
 * ## Por que uma folha e nao um cartao na tela
 *
 * A grade tem 31 linhas. Num celular ela passa da altura da tela, entao um
 * editor embaixo dela nasceria fora da vista justamente quando alguem toca numa
 * casa de dezembro. A folha sobe por cima, onde quer que o dedo esteja.
 *
 * ## O que foi digitado desce para o banco enquanto se digita
 *
 * E a licao que o post-it ja tinha ensinado: o que so existe na memoria do
 * composable morre quando ele morre -- e aqui a folha pode ser fechada com um
 * arrasto, sem passar por botao nenhum. Cada pausa na digitacao grava no Room
 * ([aoRascunhar]); a subida para o servidor acontece uma vez, ao fechar
 * ([aoFechar]). Sem isso, um paragrafo escrito e dispensado com o polegar
 * sumiria sem deixar rastro.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorDoDia(
    dia: LocalDate,
    registro: DiarioEntity?,
    aoRascunhar: (Humor?, String) -> Unit,
    aoFechar: (Humor?, String) -> Unit,
) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Semeado uma vez por dia aberto. `remember(dia)` e nao `remember(registro)`:
    // o registro muda a cada rascunho gravado, e ressemear a cada gravacao
    // jogaria o cursor de volta ao inicio a cada pausa.
    var humor by remember(dia) { mutableStateOf(Humor.porChave(registro?.mood)) }
    var texto by remember(dia) { mutableStateOf(registro?.note.orEmpty()) }

    // Grava a pausa, e nao a tecla: uma escrita por caractere redesenharia a
    // grade inteira a cada letra.
    LaunchedEffect(dia, texto) {
        delay(ESPERA_DO_RASCUNHO)
        aoRascunhar(humor, texto)
    }

    ModalBottomSheet(
        onDismissRequest = { aoFechar(humor, texto) },
        sheetState = estado,
        containerColor = Doce.superficie,
        contentColor = Doce.tinta,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Espaco.e5, end = Espaco.e5, bottom = Espaco.e5),
            verticalArrangement = Arrangement.spacedBy(Espaco.e3),
        ) {
            Text(
                text = porExtenso(dia),
                style = TipografiaBeazeth.headlineMedium,
                color = Doce.tinta,
            )

            Text(
                text = "Como foi o dia",
                style = TipografiaBeazeth.labelLarge,
                color = Doce.tintaSuave,
            )

            for (fileira in Humor.entries.chunked(3)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
                ) {
                    for (opcao in fileira) {
                        EscolhaDeHumor(
                            humor = opcao,
                            marcado = humor == opcao,
                            // Tocar no humor ja marcado tira a marca: escolher
                            // por engano teria de ser desfeito limpando o dia.
                            aoTocar = { humor = if (humor == opcao) null else opcao },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            CampoDoce(
                rotulo = "Anotações",
                valor = texto,
                aoMudar = { novo -> texto = novo.take(Repositorio.MAX_TEXTO_DO_DIARIO) },
                dica = "O que aconteceu hoje?",
                linhas = 4,
            )

            BotaoPrimario(texto = "Guardar", aoTocar = { aoFechar(humor, texto) })

            Text(
                text = "Apagar a cor e o texto limpa o dia.",
                style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                color = Doce.tintaSuave,
            )
        }
    }
}

@Composable
private fun EscolhaDeHumor(
    humor: Humor,
    marcado: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val forma = RoundedCornerShape(Canto.botao)

    Row(
        modifier = modifier
            .clip(forma)
            .background(if (marcado) humor.cor.copy(alpha = 0.35f) else cores.fundoCampo)
            .border(1.dp, if (marcado) humor.cor else cores.traco, forma)
            .clickable(onClick = aoTocar)
            .padding(horizontal = Espaco.e2, vertical = Espaco.e2),
        horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Amostra(humor.cor)
        Text(
            text = humor.rotulo,
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 13.sp),
            color = cores.tinta,
        )
    }
}

/** O quadradinho de cor, na legenda e na escolha. */
@Composable
internal fun Amostra(cor: Color) {
    Box(
        modifier = Modifier
            .size(AMOSTRA)
            .clip(RoundedCornerShape(4.dp))
            .background(cor),
    )
}

private val AMOSTRA = 14.dp

/** Pausa que conta como "parou de escrever". */
private const val ESPERA_DO_RASCUNHO = 500L
