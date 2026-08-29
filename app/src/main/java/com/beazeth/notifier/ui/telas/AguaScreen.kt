package com.beazeth.notifier.ui.telas

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.componentes.Copo
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Beber agua: quantos copos hoje, e a meta.
 *
 * O toque no copo grava no aparelho e acende na hora. A subida acontece depois
 * -- e por isso o botao nunca fica esperando resposta, mesmo com o servidor
 * dormindo no plano gratuito do Render.
 */
class AguaViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repositorio(app)

    /**
     * O dia corrente e o do SERVIDOR, nao o do aparelho.
     *
     * `app/db/hydration.py` diz, e de proposito, que o dia do consumo e o dia
     * local do servidor -- para bater com a janela dos lembretes. Um celular
     * num fuso a frente perguntaria por uma data que o servidor ainda nao tem,
     * e mostraria zero copo depois de beber tres. Aconteceu no emulador, que
     * roda em GMT enquanto o servidor esta em GMT-3.
     */
    val dia = repo.aguaCorrente()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val config = repo.configDeAgua()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun beber() = viewModelScope.launch { repo.beberAgua(diaAlvo(), 1) }

    fun desfazer() = viewModelScope.launch { repo.beberAgua(diaAlvo(), -1) }

    /**
     * Qual linha incrementar no banco do aparelho.
     *
     * O `delta` que sobe e relativo, entao o servidor aplica no dia DELE de
     * qualquer forma -- isto aqui so decide o que a tela mostra enquanto a
     * resposta nao chega. Sem nenhum dia conhecido ainda, o do aparelho e o
     * melhor chute, e a proxima sincronizacao corrige.
     */
    private fun diaAlvo(): String = dia.value?.day ?: LocalDate.now().toString()
}

/**
 * A tela, em dois cartoes: o mostrador e o dia.
 *
 * **Lado a lado quando ha largura.** E o `.water-layout` do site, que e
 * `repeat(auto-fit, minmax(min(100%, 330px), 1fr))`. Empilhados num tablet, os
 * dois cartoes deixavam a metade de baixo da tela vazia e o mostrador espremido
 * numa faixa de 1000 dp de largura por um palmo de altura. A conta aqui e a
 * mesma do site: [LARGURA_MINIMA_DA_COLUNA] por coluna, e duas colunas so
 * quando as duas cabem.
 *
 * **O copo no lugar da barra de progresso.** A barra dizia a mesma coisa que o
 * copo diz, e duas reguas do mesmo numero no mesmo cartao so dividem a atencao.
 * O que faltava era o copo: o site tem o desenho na tela e na barra lateral, e
 * aqui os dois lugares mostravam uma gota de emoji.
 */
@Composable
fun AguaScreen(vm: AguaViewModel = viewModel()) {
    val dia by vm.dia.collectAsState()
    val config by vm.config.collectAsState()

    val copos = dia?.glasses ?: 0
    // Os padroes batem com os do servidor (`app/db/hydration.py`): enquanto a
    // primeira sincronizacao nao chega, a tela mostra a mesma meta que o site
    // mostraria, e nao um numero inventado.
    val meta = config?.dailyGoal ?: 8
    val ml = config?.glassMl ?: 250
    val nivel = if (meta > 0) (copos.toFloat() / meta).coerceAtMost(1f) else 0f

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val util = maxWidth - Espaco.e5 * 2
        val emDuasColunas = util >= LARGURA_MINIMA_DA_COLUNA * 2 + Espaco.e3
        val larguraDoCartao = if (emDuasColunas) (util - Espaco.e3) / 2 else util

        val mostrador: @Composable (Modifier) -> Unit = { modificador ->
            Mostrador(
                copos = copos,
                meta = meta,
                ml = ml,
                nivel = nivel,
                larguraDoCartao = larguraDoCartao,
                aoBeber = vm::beber,
                aoDesfazer = vm::desfazer,
                modifier = modificador,
            )
        }
        val doDia: @Composable (Modifier) -> Unit = { modificador ->
            CartaoDoDia(
                titulo = rotuloDoDia(dia?.day),
                copos = copos,
                meta = meta,
                larguraDoCartao = larguraDoCartao,
                modifier = modificador,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Espaco.e5, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e5,
            ),
            verticalArrangement = Arrangement.spacedBy(Espaco.e3),
        ) {
            if (emDuasColunas) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e3),
                    ) {
                        mostrador(Modifier.weight(1f))
                        doDia(Modifier.weight(1f))
                    }
                }
            } else {
                item { mostrador(Modifier.fillMaxWidth()) }
                item { doDia(Modifier.fillMaxWidth()) }
            }
        }
    }
}

/** O `.water-stage`: o copo, a conta e os dois botoes, tudo centrado. */
@Composable
private fun Mostrador(
    copos: Int,
    meta: Int,
    ml: Int,
    nivel: Float,
    larguraDoCartao: Dp,
    aoBeber: () -> Unit,
    aoDesfazer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CartaoDaTela(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Espaco.e1),
        ) {
            // `clamp(74px, 30cqi, 116px)` do site, medido no CARTAO e nao na
            // janela: numa tela larga o cartao fica com metade do espaco, e o
            // copo tem que saber disso.
            Copo(
                nivel = nivel,
                largura = (larguraDoCartao * 0.30f).coerceIn(74.dp, 116.dp),
                modifier = Modifier.padding(vertical = Espaco.e2),
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
            ) {
                Text(
                    text = "$copos",
                    style = TipografiaBeazeth.headlineMedium.copy(fontSize = 52.sp),
                    color = Doce.tinta,
                )
                Text(
                    text = "de $meta copos",
                    style = TipografiaBeazeth.bodyLarge,
                    color = Doce.tintaSuave,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            Text(
                text = "${copos * ml} ml de ${meta * ml} ml",
                style = TipografiaBeazeth.bodyMedium,
                color = Doce.tintaSuave,
            )

            // A linha do recado existe cheia ou vazia, como o `min-height` do
            // `.water-note`: se ela so aparecesse ao bater a meta, os botoes
            // desceriam debaixo do dedo que acabou de toca-los.
            Text(
                text = if (copos >= meta) "Meta do dia batida. 💗" else "",
                style = TipografiaBeazeth.titleMedium,
                color = Doce.destaqueEscuro,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Espaco.e1),
            )

            Row(
                modifier = Modifier.padding(top = Espaco.e2),
                horizontalArrangement = Arrangement.spacedBy(Espaco.e3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotaoRedondo(rotulo = "−", aoTocar = aoDesfazer, ativo = copos > 0)
                BotaoDeBeber(aoTocar = aoBeber)
            }
        }
    }
}

/**
 * O dia em copos, um por copo da meta.
 *
 * Sao os mesmos copos do mostrador, em miniatura -- antes eram gotas de emoji,
 * o que desenhava "gota" onde a conta e de copos. O tamanho sai da largura
 * disponivel dividida pela meta, e o que nao couber numa fileira desce para a
 * seguinte: uma meta de vinte copos nao pode vazar para fora do cartao.
 */
@Composable
private fun CartaoDoDia(
    titulo: String,
    copos: Int,
    meta: Int,
    larguraDoCartao: Dp,
    modifier: Modifier = Modifier,
) {
    CartaoDaTela(modifier = modifier, titulo = titulo) {
        val dentro = larguraDoCartao - Espaco.e5 * 2
        val quantos = meta.coerceAtLeast(1)
        val tamanho = ((dentro - Espaco.e1 * (quantos - 1)) / quantos)
            .coerceIn(LARGURA_MINIMA_DO_COPINHO, LARGURA_MAXIMA_DO_COPINHO)
        val porFileira = ((dentro + Espaco.e1) / (tamanho + Espaco.e1)).toInt().coerceAtLeast(1)

        Column(verticalArrangement = Arrangement.spacedBy(Espaco.e2)) {
            for (fileira in (1..quantos).chunked(porFileira)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Espaco.e1)) {
                    for (i in fileira) {
                        Copo(nivel = if (i <= copos) 1f else 0f, largura = tamanho)
                    }
                }
            }
        }
    }
}

@Composable
private fun BotaoDeBeber(aoTocar: () -> Unit) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(listOf(Doce.azulSuave, Doce.destaque))
            )
            .clickable(onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "＋", fontSize = 34.sp, color = Color.White)
    }
}

@Composable
private fun BotaoRedondo(rotulo: String, aoTocar: () -> Unit, ativo: Boolean) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Doce.fundoCampo)
            .border(1.dp, Doce.traco, CircleShape)
            .clickable(enabled = ativo, onClick = aoTocar),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rotulo,
            fontSize = 22.sp,
            color = if (ativo) Doce.tinta else Doce.tintaSuave.copy(alpha = 0.4f),
        )
    }
}

/**
 * O titulo do cartao de consumo.
 *
 * Diz a data quando ela nao e a de hoje no aparelho. Parece detalhe, mas e o
 * que impede a tela de mentir: offline por um dia, ou num fuso a frente do
 * servidor, "Hoje" estaria errado -- e um contador de agua errado e pior que
 * um contador que se explica.
 */
@Composable
private fun rotuloDoDia(dia: String?): String {
    if (dia == null) return "Hoje"
    val data = runCatching { LocalDate.parse(dia) }.getOrNull() ?: return "Hoje"
    if (data == LocalDate.now()) return "Hoje"
    return "%02d/%02d".format(data.dayOfMonth, data.monthValue)
}

/** O `minmax(min(100%, 330px), 1fr)` do `.water-layout`. */
private val LARGURA_MINIMA_DA_COLUNA = 330.dp

private val LARGURA_MINIMA_DO_COPINHO = 18.dp
private val LARGURA_MAXIMA_DO_COPINHO = 34.dp
