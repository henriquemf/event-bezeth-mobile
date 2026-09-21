package com.beazeth.notifier.ui.telas.diario

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.local.DiarioEntity
import com.beazeth.notifier.ui.componentes.BotaoPilula
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.telas.calendario.DIAS_LONGOS
import com.beazeth.notifier.ui.telas.calendario.MESES
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Diario: uma cor por dia e o espaco para escrever.
 *
 * O ano e um estado da tela, e nao da conta: trocar de ano e trocar a consulta,
 * como as semanas do to-do. Um ano por vez porque a grade mostra um ano por vez
 * -- carregar dez para desenhar um seria leitura jogada fora a cada abertura.
 */
class DiarioViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)

    private val _ano = MutableStateFlow(LocalDate.now().year)
    val ano = _ano

    @OptIn(ExperimentalCoroutinesApi::class)
    val dias = _ano
        .flatMapLatest { repo.diarioDoAno(it) }
        .map { lista ->
            // Data malformada nao derruba a grade: some da grade, so.
            lista.mapNotNull { linha ->
                runCatching { LocalDate.parse(linha.day) }.getOrNull()?.let { it to linha }
            }.toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun trocarAno(passo: Int) {
        _ano.value = (_ano.value + passo).coerceIn(PRIMEIRO_ANO, LocalDate.now().year + 1)
    }

    /** Enquanto a pessoa escreve: grava no aparelho e nao toca na fila. */
    internal fun rascunhar(dia: LocalDate, humor: Humor?, texto: String) = viewModelScope.launch {
        repo.rascunharDiaDoDiario(dia.toString(), humor?.chave.orEmpty(), texto)
    }

    /** Ao fechar: a mesma escrita, agora com a subida. */
    internal fun guardar(dia: LocalDate, humor: Humor?, texto: String) = viewModelScope.launch {
        repo.gravarDiaDoDiario(dia.toString(), humor?.chave.orEmpty(), texto)
    }
}

@Composable
fun DiarioScreen(vm: DiarioViewModel = viewModel()) {
    val ano by vm.ano.collectAsState()
    val dias by vm.dias.collectAsState()
    var escolhido by remember { mutableStateOf<LocalDate?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Espaco.e5, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e5,
        ),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        item {
            CartaoDaTela {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Diário",
                            style = TipografiaBeazeth.headlineMedium,
                            color = Doce.tinta,
                        )
                        Text(
                            text = quantosEscritos(dias.size),
                            style = TipografiaBeazeth.bodyMedium,
                            color = Doce.tintaSuave,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BotaoPilula(texto = "‹", aoTocar = { vm.trocarAno(-1) })
                        Text(
                            text = ano.toString(),
                            style = TipografiaBeazeth.titleMedium,
                            color = Doce.tinta,
                        )
                        BotaoPilula(texto = "›", aoTocar = { vm.trocarAno(1) })
                    }
                }
            }
        }

        item {
            CartaoDaTela(titulo = "O ano inteiro") {
                GradeDoAno(
                    ano = ano,
                    dias = dias,
                    escolhido = escolhido,
                    aoEscolher = { escolhido = it },
                )
                Legenda()
                Text(
                    text = "Toque num dia para escolher o humor e escrever.",
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                    color = Doce.tintaSuave,
                )
            }
        }
    }

    val dia = escolhido
    if (dia != null) {
        EditorDoDia(
            dia = dia,
            registro = dias[dia],
            aoRascunhar = { humor, texto -> vm.rascunhar(dia, humor, texto) },
            aoFechar = { humor, texto ->
                vm.guardar(dia, humor, texto)
                escolhido = null
            },
        )
    }

    // Trocar de ano com um dia aberto deixaria o editor falando de um dia que a
    // grade nao mostra mais.
    LaunchedEffect(ano) { escolhido = null }
}

@Composable
private fun Legenda() {
    Column(verticalArrangement = Arrangement.spacedBy(Espaco.e1)) {
        for (linha in Humor.entries.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Espaco.e3)) {
                for (humor in linha) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Amostra(humor.cor)
                        Text(
                            text = humor.rotulo,
                            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
                            color = Doce.tintaSuave,
                        )
                    }
                }
            }
        }
    }
}

/** "3 dias escritos", e "1 dia escrito" no singular. */
private fun quantosEscritos(quantos: Int): String = when (quantos) {
    0 -> "Nenhum dia escrito ainda"
    1 -> "1 dia escrito"
    else -> "$quantos dias escritos"
}

/** "Segunda, 21 de setembro" — o dia por extenso, no titulo do editor. */
internal fun porExtenso(dia: LocalDate): String =
    "${DIAS_LONGOS[dia.dayOfWeek.value - 1]}, ${dia.dayOfMonth} de ${MESES[dia.monthValue - 1]}"

/** O mesmo piso da navegacao do site (`PRIMEIRO_ANO`, em blueprints/diary.py). */
private const val PRIMEIRO_ANO = 2020
