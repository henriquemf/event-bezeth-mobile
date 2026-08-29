package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.data.Repositorio
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
import java.time.LocalDate

/** A largura da lateral. O `--sidebar-w` do site e 260 px; aqui e o mesmo
 *  numero em dp, que num tablet de 11 polegadas da a mesma proporcao. */
internal val LARGURA_DA_LATERAL = 260.dp

/**
 * A barra lateral, no formato do site.
 *
 * O app roda num tablet de 11 polegadas, e num tablet nao ha o aperto que
 * justificava a barra inferior do celular. Entao a lateral e a mesma do site,
 * com as mesmas partes e na mesma ordem (`partials/sidebar.html`): marca,
 * conta, menu, pomodoro, agua, modo escuro.
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
    escuro: Boolean,
    aoAlternarEscuro: (Boolean) -> Unit,
    aoTrocar: (Destino) -> Unit,
    aoSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val contexto = LocalContext.current.applicationContext
    val prefs = remember { Preferencias(contexto) }
    val repo = remember { Repositorio(contexto) }
    val escopo = rememberCoroutineScope()

    val fluxoDoPomodoro = remember { estadoDoPomodoro(prefs) }
    val pomodoro by fluxoDoPomodoro.collectAsState(EstadoPomodoro())
    val diaDeAgua by remember { repo.aguaCorrente() }.collectAsState(null)
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
        // ------------------------------------------------------------- marca
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            PontoDaMarca(tamanho = 34.dp)
            Column {
                Text(
                    text = "Event Notifier",
                    style = TipografiaBeazeth.titleLarge.copy(fontSize = 17.sp),
                    color = cores.tinta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Organize seus momentos",
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
                    color = cores.tintaSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ------------------------------------------------------------- conta
        //
        // Sem conta a caixa continua existindo, e diz a verdade: onde as coisas
        // estao. Esconde-la deixaria a lateral com um buraco e sem porta de
        // saida -- e "entrar numa conta" precisa ficar a mao de quem mudar de
        // ideia, no mesmo lugar em que "sair" fica para quem tem conta.
        val local = LocalModoLocal.current
        Caixa {
            Text(
                text = if (local) "Sem conta" else nome,
                style = TipografiaBeazeth.titleMedium.copy(fontSize = 14.sp),
                color = cores.tinta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (local) {
                Text(
                    text = "Tudo neste aparelho",
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 11.sp),
                    color = cores.tintaSuave,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BotaoDaLateral(
                texto = if (local) "Entrar numa conta" else "Sair",
                aoTocar = aoSair,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // -------------------------------------------------------------- menu
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (destino in Destino.entries) {
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
                    aoTocar = { escopo.launch { alternarPomodoro(prefs) } },
                    modifier = Modifier.weight(1f),
                )
                BotaoDaLateral(
                    texto = "Parar",
                    aoTocar = { escopo.launch { zerarPomodoro(prefs) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // -------------------------------------------------------------- agua
        if (configDeAgua?.enabled == true) {
            val copos = diaDeAgua?.glasses ?: 0
            val meta = configDeAgua?.dailyGoal ?: 8
            // Qual linha incrementar: a do dia que o SERVIDOR considera
            // corrente, e nao a do aparelho -- mesmo motivo escrito no
            // `AguaViewModel`.
            val dia = diaDeAgua?.day ?: LocalDate.now().toString()

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
                        aoTocar = { escopo.launch { repo.beberAgua(dia, 1) } },
                        modifier = Modifier.weight(1f),
                    )
                    BotaoDaLateral(
                        texto = "−",
                        aoTocar = { escopo.launch { repo.beberAgua(dia, -1) } },
                        modifier = Modifier.width(46.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ------------------------------------------------------- modo escuro
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            InterruptorEscuro(escuro = escuro, aoAlternar = aoAlternarEscuro)
            Text(
                text = "Dark mode",
                style = TipografiaBeazeth.bodyLarge.copy(fontSize = 14.sp),
                color = cores.tintaSuave,
            )
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
