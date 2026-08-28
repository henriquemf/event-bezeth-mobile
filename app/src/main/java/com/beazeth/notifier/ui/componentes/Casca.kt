package com.beazeth.notifier.ui.componentes

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * Os destinos do app.
 *
 * Espelham `event-beazeth/app/navigation.py`, na mesma ordem. La existe uma
 * lista so, e nao uma por barra, pelo motivo explicado no proprio arquivo: com
 * duas listas, um destino novo entra numa e falta na outra -- e o que falta e
 * sempre o do celular, que e o menos testado. Aqui vale o mesmo.
 *
 * `curto` existe porque a barra inferior tem uns 60 dp por item: "Weekly
 * Planner" nao cabe, "Planner" cabe. `naBarra` marca quem merece o polegar --
 * Aparencia e tela de ajuste, se usa uma vez por mes e fica no topo.
 */
enum class Destino(
    val rota: String,
    val icone: String,
    val titulo: String,
    val curto: String,
    val naBarra: Boolean,
) {
    POSTITS("postits", "🗒️", "Post-its", "Post-its", true),
    CALENDARIO("calendario", "📅", "Calendário", "Agenda", true),
    PLANNER("planner", "🗓️", "Weekly Planner", "Planner", true),
    TODO("todo", "✅", "To-do", "To-do", true),
    POMODORO("pomodoro", "🍎", "Pomodoro", "Pomodoro", true),
    AGUA("agua", "💧", "Beber água", "Água", true),
    APARENCIA("aparencia", "🎨", "Aparência", "Tema", false);

    companion object {
        val naBarraInferior = entries.filter { it.naBarra }
        fun porRota(rota: String?) = entries.firstOrNull { it.rota == rota }
    }
}

/**
 * A barra inferior, igual a `.bottom-nav` do site.
 *
 * Fixa embaixo, ao alcance do polegar. No site ela existe porque a lateral
 * virava um bloco ACIMA do conteudo no celular, e era preciso rolar sete links
 * antes de chegar na tela.
 *
 * `navigationBarsPadding` faz o papel do `env(safe-area-inset-bottom)`: em
 * aparelho com barra de gestos, sem ele os rotulos ficam por baixo dela.
 */
@Composable
fun BarraInferior(
    atual: Destino?,
    aoTrocar: (Destino) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dentro de `drawBehind` nao ha contexto de composicao, entao a paleta e
    // capturada aqui fora.
    val cores = Doce

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // A sombra sobe, nao desce: o que ela separa esta acima da barra.
                drawRect(
                    color = cores.sombra.copy(alpha = 0.10f),
                    topLeft = Offset(0f, -12.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 12.dp.toPx()),
                )
            }
            .background(cores.superficie)
            .drawBehind {
                drawRect(
                    color = cores.traco,
                    size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
                )
            }
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (destino in Destino.naBarraInferior) {
            ItemDaBarra(
                destino = destino,
                ativo = destino == atual,
                aoTocar = { aoTrocar(destino) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Verdadeiro quando a janela e mais larga que alta.
 *
 * A pergunta e sobre a JANELA e nao sobre o sensor: um app em tela dividida ou
 * numa janela redimensionavel pode estar deitado com o aparelho em pe, e o que
 * decide o desenho e o formato que sobrou.
 *
 * Existe aqui, e nao repetido em cada tela, porque tres delas precisam da mesma
 * resposta -- e duas respostas diferentes para a mesma pergunta e como o app
 * comeca a parecer dois apps.
 */
@Composable
fun janelaDeitada(): Boolean {
    val configuracao = LocalConfiguration.current
    return configuracao.screenWidthDp > configuracao.screenHeightDp
}

@Composable
private fun ItemDaBarra(
    destino: Destino,
    ativo: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toques = remember { MutableInteractionSource() }
    val pressionado by toques.collectIsPressedAsState()

    // Duas animacoes que se somam, e cada uma responde a uma pergunta diferente:
    //
    // - `destaque` diz ONDE VOCE ESTA. O item ativo fica maior e assim continua.
    // - `recuo` diz QUE O TOQUE FOI REGISTRADO. O icone afunda enquanto o dedo
    //   esta em cima e volta com uma molinha ao soltar.
    //
    // A mola tem `dampingRatio` baixo de proposito: o pequeno repique ao soltar
    // e o que da a sensacao de coisa fisica. Sem ele o retorno e uma rampa, e
    // parece que a tela so redesenhou.
    val destaque by animateFloatAsState(
        targetValue = if (ativo) 1.18f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "destaque",
    )
    val recuo by animateFloatAsState(
        targetValue = if (pressionado) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "recuo",
    )
    val subida by animateFloatAsState(
        targetValue = if (ativo) -2f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "subida",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (ativo) Doce.destaque.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(
                interactionSource = toques,
                indication = androidx.compose.material3.ripple(color = Doce.destaque),
                role = Role.Tab,
                onClick = aoTocar,
            )
            // 44 dp e o alvo de toque minimo confortavel; `heightIn` e nao
            // `height` para o rotulo poder crescer com a fonte do sistema.
            .heightIn(min = 48.dp)
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = destino.icone,
            fontSize = 23.sp,
            modifier = Modifier
                .offset(y = subida.dp)
                .scale(destaque * recuo),
        )
        Text(
            text = destino.curto,
            style = TipografiaBeazeth.bodyMedium.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            ),
            color = if (ativo) Doce.destaqueEscuro else Doce.tintaSuave,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * O topo: a marca, o titulo da tela, o modo escuro e o atalho para Aparencia.
 *
 * Compacto de proposito. O site tem uma lateral inteira com widgets; num
 * celular esse espaco e da tela, nao da moldura.
 *
 * O sol/lua fica encostado na paleta porque as duas perguntas sao a mesma --
 * "quero o app com outra cara" -- e porque trocar de claro para escuro e coisa
 * de todo dia, ao contrario de escolher uma paleta. Enterrado numa tela de
 * ajuste, custaria tres toques por anoitecer.
 */
@Composable
fun BarraSuperior(
    titulo: String,
    escuro: Boolean,
    aoAlternarEscuro: (Boolean) -> Unit,
    aoAbrirAparencia: () -> Unit,
    modifier: Modifier = Modifier,
    aoAbrirMenu: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Espaco.e5, vertical = Espaco.e2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        // As tres barrinhas so aparecem quando ha um menu escondido para
        // abrir. Um botao que abre o que ja esta na tela e ruido.
        if (aoAbrirMenu != null) {
            BotaoDoMenu(aoTocar = aoAbrirMenu)
        }
        PontoDaMarca(tamanho = 26.dp)
        Text(
            text = titulo,
            style = TipografiaBeazeth.titleLarge,
            color = Doce.tinta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        InterruptorEscuro(escuro = escuro, aoAlternar = aoAlternarEscuro)

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Doce.fundoCampo)
                .border(1.dp, Doce.traco, RoundedCornerShape(999.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = androidx.compose.material3.ripple(color = Doce.destaque),
                    role = Role.Button,
                    onClick = aoAbrirAparencia,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = Destino.APARENCIA.icone, fontSize = 17.sp)
        }
    }
}

/**
 * O cartao das telas: o `.hero-card` e o `.panel-card` do site, que tem
 * exatamente o mesmo desenho (raio 24, `--space-5` de folga, `--surface-solid`,
 * borda e sombra baixa).
 *
 * Sao duas classes la porque o CSS as usa em papeis diferentes; aqui e um
 * componente so, com um parametro para o titulo.
 */
@Composable
fun CartaoDaTela(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(Canto.cartao),
                ambientColor = Doce.sombra,
                spotColor = Doce.sombra,
            )
            .clip(RoundedCornerShape(Canto.cartao))
            .background(Doce.superficie)
            .border(1.dp, Doce.traco, RoundedCornerShape(Canto.cartao))
            .padding(Espaco.e5),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        if (titulo != null) {
            Text(
                text = titulo,
                style = TipografiaBeazeth.headlineMedium,
                color = Doce.tinta,
            )
        }
        conteudo()
    }
}

/**
 * As tres barrinhas.
 *
 * Desenhadas e nao escritas: o caractere "☰" muda de largura e de peso a cada
 * uma das dez fontes que o app oferece, e numa delas fica fino a ponto de
 * sumir. Tres retangulos sao tres retangulos em qualquer tipografia.
 */
@Composable
private fun BotaoDoMenu(aoTocar: () -> Unit) {
    val cores = Doce
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(cores.fundoCampo)
            .border(1.dp, cores.traco, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(color = cores.destaque),
                role = Role.Button,
                onClick = aoTocar,
            )
            .drawBehind {
                val largura = 16.dp.toPx()
                val espessura = 2.dp.toPx()
                val esquerda = (size.width - largura) / 2f
                val meio = size.height / 2f
                for (deslocamento in listOf(-5.dp.toPx(), 0f, 5.dp.toPx())) {
                    drawRoundRect(
                        color = cores.tinta,
                        topLeft = Offset(esquerda, meio + deslocamento - espessura / 2f),
                        size = androidx.compose.ui.geometry.Size(largura, espessura),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(espessura),
                    )
                }
            },
    )
}
