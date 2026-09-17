package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.Perfil
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.ui.LocalModoLocal
import com.beazeth.notifier.ui.telas.EstadoPomodoro
import com.beazeth.notifier.ui.telas.alternarPomodoro
import com.beazeth.notifier.ui.telas.estadoDoPomodoro
import com.beazeth.notifier.ui.telas.zerarPomodoro
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.launch

/** A largura da lateral. O `--sidebar-w` do site e 260 px; aqui e o mesmo
 *  numero em dp, que num tablet de 11 polegadas da a mesma proporcao. */
internal val LARGURA_DA_LATERAL = 260.dp

/**
 * A barra lateral, no formato do site.
 *
 * O app roda num tablet de 11 polegadas, e num tablet nao ha o aperto que
 * justificava a barra inferior do celular. Entao a lateral segue a do site
 * (`partials/sidebar.html`), com uma diferenca deliberada no comeco e outra no
 * fim: perfil, menu, pomodoro, agua.
 *
 * **Nao ha marca no alto.** O site precisa dizer o proprio nome porque a pessoa
 * chega nele por um link, no meio de outras abas. Um app ja foi aberto pelo
 * icone com o nome embaixo -- repeti-lo dentro custava 50 dp do lugar mais
 * nobre da tela para informar o que ninguem perguntou.
 *
 * **Nem interruptor de modo escuro no fim.** Ele ficava no rodape da lateral e
 * tambem na barra de cima e tambem na tela de Aparencia: tres controles para a
 * mesma chave. Ficaram os dois que estao no caminho de quem os procura.
 *
 * **Os dois widgets sao os do site, e nao atalhos.** O do pomodoro conta de
 * verdade, com a ampulheta escorrendo; o da agua soma copo de verdade. Foi o
 * pedido explicito la e vale aqui: um widget que so leva para a tela seria um
 * link com desenho bonito.
 *
 * **Eles leem a MESMA fonte que as telas.** O pomodoro vem de
 * [estadoDoPomodoro], derivado do DataStore, e a agua vem do Room -- as duas
 * coisas que ja mandavam nas telas. Nao ha copia de estado aqui, entao nao ha
 * como o widget e a tela discordarem: apertar "Pausar" na lateral para o
 * relogio da tela do pomodoro, e beber um copo aqui aparece la.
 *
 * O widget de agua so existe com o lembrete ligado -- igual ao `{% if
 * current_user['water_enabled'] %}` do site.
 */
@Composable
fun BarraLateral(
    nome: String,
    atual: Destino,
    aoTrocar: (Destino) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val contexto = LocalContext.current.applicationContext
    val prefs = remember { Preferencias(contexto) }
    val repo = remember { Repositorio(contexto) }
    val banco = remember { BancoLocal.obter(contexto) }
    val perfil = remember { Perfil(contexto) }
    val escopo = rememberCoroutineScope()

    val fluxoDoPomodoro = remember { estadoDoPomodoro(prefs) }
    val pomodoro by fluxoDoPomodoro.collectAsState(EstadoPomodoro())
    val diaDeAgua by remember { repo.aguaDeHoje() }.collectAsState(null)
    val configDeAgua by remember { repo.configDeAgua() }.collectAsState(null)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(LARGURA_DA_LATERAL)
            .background(cores.superficie)
            .drawBehind {
                drawRect(
                    color = cores.traco,
                    topLeft = Offset(size.width - 1.dp.toPx(), 0f),
                    size = Size(1.dp.toPx(), size.height),
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Espaco.e4, vertical = Espaco.e3),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        // ------------------------------------------------------------ perfil
        //
        // A pessoa, e mais nada: foto e nome. A caixa INTEIRA leva ao perfil --
        // e la que ficam trocar foto, trocar senha, os numeros e o botao de
        // sair, que antes ocupava um lugar fixo aqui em toda tela.
        //
        // Sem conta a caixa continua existindo e continua clicavel: o nome
        // local tambem se escolhe la, e "entrar numa conta" e o botao do fim
        // daquela tela. Esconde-la deixaria quem esta sem conta sem porta.
        val local = LocalModoLocal.current
        val nomeLocal by perfil.nomeLocal.collectAsState("")
        val mostrado = if (local) nomeLocal.ifBlank { "Sem conta" } else nome

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Canto.caixa))
                .background(cores.fundoCampo)
                .border(1.dp, cores.traco, RoundedCornerShape(Canto.caixa))
                .clickable { aoTrocar(Destino.PERFIL) }
                .padding(Espaco.e2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            FotoDePerfil(nome = mostrado, tamanho = 44.dp)
            Text(
                text = mostrado,
                style = TipografiaBeazeth.titleMedium.copy(fontSize = 15.sp),
                color = cores.tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // -------------------------------------------------------------- menu
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (destino in Destino.noMenuLateral) {
                LinkDoMenu(
                    destino = destino,
                    ativo = destino == atual,
                    aoTocar = { aoTrocar(destino) },
                )
            }
        }

        // ---------------------------------------------------------- pomodoro
        Caixa {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Canto.caixa))
                    .clickable { aoTrocar(Destino.POMODORO) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
            ) {
                Ampulheta(
                    progresso = pomodoro.progresso,
                    correndo = pomodoro.correndo,
                    largura = 26.dp,
                )
                Column {
                    Text(
                        text = "%02d:%02d".format(
                            pomodoro.restante / 60, pomodoro.restante % 60,
                        ),
                        style = TipografiaBeazeth.titleMedium.copy(fontSize = 17.sp),
                        color = cores.tinta,
                    )
                    Text(
                        text = "Pomodoro",
                        style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
                        color = cores.tintaSuave,
                    )
                }
            }

            // A barrinha de progresso do `.pomo-widget-track` do site.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(cores.destaque.copy(alpha = 0.18f))
                    .drawBehind {
                        drawRect(
                            color = cores.destaque,
                            size = Size(size.width * pomodoro.progresso, size.height),
                        )
                    },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Espaco.e1)) {
                BotaoDaLateral(
                    texto = if (pomodoro.correndo) "Pausar" else "Começar",
                    destacado = !pomodoro.correndo,
                    aoTocar = { escopo.launch { alternarPomodoro(contexto, prefs, banco) } },
                    modifier = Modifier.weight(1f),
                )
                BotaoDaLateral(
                    texto = "Parar",
                    aoTocar = { escopo.launch { zerarPomodoro(contexto, prefs, banco) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // -------------------------------------------------------------- agua
        if (configDeAgua?.enabled == true) {
            val copos = diaDeAgua?.glasses ?: 0
            val meta = configDeAgua?.dailyGoal ?: 8

            Caixa {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Canto.caixa))
                        .clickable { aoTrocar(Destino.AGUA) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                ) {
                    // O copo do site (`glass('sm')`), e nao o emoji do menu: o
                    // widget vizinho ja mostra a ampulheta de verdade, e a agua
                    // era a unica que anunciava um desenho e entregava um
                    // caractere.
                    Copo(
                        nivel = if (meta > 0) {
                            (copos.toFloat() / meta).coerceAtMost(1f)
                        } else {
                            0f
                        },
                        largura = 26.dp,
                    )
                    Column {
                        Text(
                            text = "$copos de $meta",
                            style = TipografiaBeazeth.titleMedium.copy(fontSize = 17.sp),
                            color = cores.tinta,
                        )
                        Text(
                            text = "copos hoje",
                            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
                            color = cores.tintaSuave,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Espaco.e1)) {
                    BotaoDaLateral(
                        texto = "Bebi",
                        destacado = true,
                        aoTocar = { escopo.launch { repo.beberAgua(1) } },
                        modifier = Modifier.weight(1f),
                    )
                    BotaoDaLateral(
                        texto = "−",
                        aoTocar = { escopo.launch { repo.beberAgua(-1) } },
                        modifier = Modifier.width(46.dp),
                    )
                }
            }
        }

    }
}

/** O `.mode-box` do site: caixa clara com borda, para agrupar um widget. */
@Composable
private fun Caixa(conteudo: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Canto.caixa))
            .background(Doce.fundoCampo)
            .border(1.dp, Doce.traco, RoundedCornerShape(Canto.caixa))
            .padding(Espaco.e3),
        verticalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        conteudo()
    }
}

/** Um `.menu-link`: icone, rotulo, e a faixa de destaque de quem esta ativo. */
@Composable
private fun LinkDoMenu(destino: Destino, ativo: Boolean, aoTocar: () -> Unit) {
    val cores = Doce
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Canto.caixa))
            .background(if (ativo) cores.destaque.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = aoTocar)
            .padding(horizontal = Espaco.e3, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        Text(text = destino.icone, fontSize = 17.sp)
        Text(
            text = destino.titulo,
            style = TipografiaBeazeth.bodyLarge.copy(
                fontSize = 14.sp,
                fontWeight = if (ativo) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (ativo) cores.destaqueEscuro else cores.tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BotaoDaLateral(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    destacado: Boolean = false,
) {
    val cores = Doce
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (destacado) cores.destaque else cores.superficie)
            .border(
                1.dp,
                if (destacado) Color.Transparent else cores.traco,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = aoTocar)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = texto,
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 12.sp),
            color = if (destacado) Color.White else cores.tinta,
            maxLines = 1,
        )
    }
}
