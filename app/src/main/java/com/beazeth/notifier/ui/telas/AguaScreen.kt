package com.beazeth.notifier.ui.telas

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.ui.componentes.CartaoDaTela
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

    val progresso by animateFloatAsState(
        targetValue = if (meta > 0) (copos.toFloat() / meta).coerceAtMost(1f) else 0f,
        label = "progresso",
    )

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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Doce.azulSuave.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .graphicsLayer {
                                scaleX = progresso
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Doce.azulSuave, Doce.destaque)
                                )
                            ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.e3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BotaoRedondo(rotulo = "−", aoTocar = vm::desfazer, ativo = copos > 0)
                    BotaoDeBeber(aoTocar = vm::beber)
                }
            }
        }

        item {
            CartaoDaTela(titulo = rotuloDoDia(dia?.day)) {
                // Uma fileira de copos: cheio ate a conta de hoje, vazio depois.
                // E a leitura de relance que um numero sozinho nao da.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
                ) {
                    for (i in 1..meta) {
                        Text(
                            text = if (i <= copos) "💧" else "○",
                            fontSize = 20.sp,
                            color = Doce.tintaSuave,
                        )
                    }
                }

                if (copos >= meta) {
                    Text(
                        text = "Meta do dia batida. 💗",
                        style = TipografiaBeazeth.titleMedium,
                        color = Doce.destaqueEscuro,
                    )
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
