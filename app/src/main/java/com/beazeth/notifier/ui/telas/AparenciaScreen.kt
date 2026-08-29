package com.beazeth.notifier.ui.telas

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.FONTES
import com.beazeth.notifier.ui.theme.FONTE_PADRAO
import com.beazeth.notifier.ui.theme.PALETAS_CLARAS
import com.beazeth.notifier.ui.theme.PALETAS_ESCURAS
import com.beazeth.notifier.ui.theme.PALETA_PADRAO
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * O Estudio de Aparencia: dez paletas e dez duplas de fonte, e nada mais.
 *
 * A escolha se ve no proprio botao -- cada preview de tema desenha o degrade
 * daquele tema, e cada preview de fonte escreve na fonte que representa. E o
 * que o site faz: escolher pelo preview, nao por um nome numa lista.
 *
 * **O modo escuro nao tem controle aqui.** Ele e uma chave so, e a lua da barra
 * de cima esta em todas as telas, inclusive nesta -- o cartao que existia neste
 * lugar era um segundo interruptor a dois centimetros do primeiro, e ainda
 * ficava acima dos previews, empurrando para baixo o que a tela veio mostrar.
 * O estado dele continua sendo lido ([escuro]), porque e ele que decide se os
 * previews mostram as paletas claras ou as escuras.
 *
 * A escolha nao sobe para o servidor: no site ela vive no navegador, e aqui no
 * aparelho. Sao a mesma conta com aparencias independentes, e isso e proposital
 * dos dois lados -- o tema do computador nao tem por que mandar no do celular.
 */
class AparenciaViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Preferencias(app)

    val tema = prefs.tema
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PALETA_PADRAO)

    val fonte = prefs.fonte
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FONTE_PADRAO)

    val escuro = prefs.escuro
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun escolherTema(chave: String) = viewModelScope.launch { prefs.definirTema(chave) }

    fun escolherFonte(chave: String) = viewModelScope.launch { prefs.definirFonte(chave) }
}

@Composable
fun AparenciaScreen(vm: AparenciaViewModel = viewModel()) {
    val tema by vm.tema.collectAsState()
    val fonte by vm.fonte.collectAsState()
    val escuro by vm.escuro.collectAsState()
    val cores = Doce

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Espaco.e5, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e5,
        ),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        // ------------------------------------------------------------ temas
        item {
            CartaoDaTela(titulo = "Preview de Temas") {
                // Duas colunas: dez previews numa fileira rolavel esconderiam
                // metade das opcoes atras de um gesto que ninguem descobre.
                val lista = if (escuro) PALETAS_ESCURAS else PALETAS_CLARAS
                lista.chunked(2).forEach { par ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                    ) {
                        for (p in par) {
                            PreviewDeTema(
                                rotulo = p.rotulo,
                                topo = p.fundoTopo,
                                base = p.fundoBase,
                                destaque = p.destaque,
                                tinta = p.tinta,
                                ativo = p.chave == tema,
                                aoTocar = { vm.escolherTema(p.chave) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (par.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ----------------------------------------------------------- fontes
        item {
            CartaoDaTela(titulo = "Preview de Fontes") {
                FONTES.chunked(2).forEach { par ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                    ) {
                        for (f in par) {
                            PreviewDeFonte(
                                dupla = f,
                                ativo = f.chave == fonte,
                                aoTocar = { vm.escolherFonte(f.chave) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (par.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

    }
}

/**
 * Um preview de tema: o degrade daquele tema, com o nome por cima.
 *
 * Pinta com as cores DO TEMA REPRESENTADO, e nao com as do tema em vigor -- e o
 * que faz o botao responder "como vai ficar?" antes do toque.
 */
@Composable
private fun PreviewDeTema(
    rotulo: String,
    topo: androidx.compose.ui.graphics.Color,
    base: androidx.compose.ui.graphics.Color,
    destaque: androidx.compose.ui.graphics.Color,
    tinta: androidx.compose.ui.graphics.Color,
    ativo: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(topo, base)))
            .border(
                width = if (ativo) 2.dp else 1.dp,
                color = if (ativo) Doce.destaqueEscuro else Doce.traco,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = aoTocar)
            .padding(Espaco.e2),
        verticalArrangement = Arrangement.spacedBy(Espaco.e1),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(destaque),
            )
            if (ativo) {
                Text(text = "✓", fontSize = 12.sp, color = destaque)
            }
        }
        Text(
            text = rotulo,
            style = TipografiaBeazeth.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = tinta,
        )
    }
}

/** Um preview de fonte: o nome escrito NA fonte que ele oferece. */
@Composable
private fun PreviewDeFonte(
    dupla: com.beazeth.notifier.ui.theme.DuplaDeFonte,
    ativo: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (ativo) Doce.destaque.copy(alpha = 0.16f) else Doce.fundoCampo)
            .border(
                width = if (ativo) 2.dp else 1.dp,
                color = if (ativo) Doce.destaque else Doce.traco,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = aoTocar)
            .padding(Espaco.e2),
    ) {
        Text(
            text = dupla.rotulo,
            fontFamily = dupla.titulo,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = if (ativo) Doce.destaqueEscuro else Doce.tinta,
        )
        Text(
            text = "Eventos encantadores",
            fontFamily = dupla.corpo,
            fontSize = 12.sp,
            color = Doce.tintaSuave,
        )
    }
}
