@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.beazeth.notifier.ui.telas.postits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.local.NotaEntity
import com.beazeth.notifier.ui.LocalModoLocal
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Um post-it: o papel, o texto, o arrasto e a troca de cor.
 *
 * ## O arrasto
 *
 * Passou por tres consertos, e vale registrar os tres porque cada um some de
 * vista assim que funciona:
 *
 * **1. Voltava ao lugar de origem por um instante.** A versao anterior somava o
 * deslocamento num acumulador e o zerava em `onDragEnd`, antes de o Room ter
 * gravado a posicao nova. Nos quadros entre uma coisa e outra, o cartao era
 * desenhado na posicao ANTIGA com deslocamento zero -- ou seja, pulava de volta
 * para onde estava e so depois ia para onde foi largado. Quando o Room
 * respondia rapido ninguem via; quando nao, era um solavanco. Agora a posicao
 * mostrada e do proprio cartao, e ela so volta a seguir o banco depois que o
 * banco confirma o que foi enviado.
 *
 * **2. Largar perto da borda de cima ou da esquerda jogava o post-it no canto.**
 * `Repositorio.moverNota` limita a `0..MAX`, e a tela nao sabia disso: o dedo
 * levava o cartao para -40, o banco gravava 0, e o cartao aparecia colado no
 * canto. O limite agora e o mesmo dos dois lados, entao o dedo simplesmente
 * para na borda -- o que se ve e o que se grava.
 *
 * **3. Arrastava torto.** O `pointerInput` fica DENTRO da camada girada, entao
 * o deslocamento chega no sistema de coordenadas inclinado do papel. Somado
 * direto, um arrasto reto na tela virava um arrasto na diagonal, com erro de
 * ate 7% do lado nos 4 graus de inclinacao. A conta com seno e cosseno devolve
 * o vetor para o espaco do quadro.
 *
 * `Modifier.offset { }` -- a versao com lambda -- e nao `offset(Dp)`: a de dp
 * roda na composicao e refaz medida a cada quadro do arrasto; a de lambda so
 * reposiciona. E o equivalente possivel da regra "arrastar post-it e transform,
 * nao position" (`CONSTITUTION.md`, secao 4) sem tirar o cartao do lugar onde o
 * Compose calcula toque e ancoragem de menu.
 *
 * ## A edicao
 *
 * **Quem abre a edicao e a tela, nao o cartao.** O estado morava aqui dentro,
 * um `editando` por post-it, e o resultado era que dois papeis ficavam abertos
 * ao mesmo tempo: nada fechava o primeiro. Em modo de toque o Compose nao passa
 * o foco para um `clickable`, entao tocar no segundo cartao nao tirava o foco do
 * primeiro. Agora quem esta aberto e UM id na [PostitsScreen] -- abrir um fecha
 * o outro pela propria definicao de estado.
 *
 * **Sair da edicao salva, venha de onde vier.** Tocar fora, tocar em outro
 * post-it ou tocar em "Pronto" sao a mesma coisa: `editando` vira `false` e o
 * `LaunchedEffect` grava. Exigir o botao era transformar "parei de escrever" em
 * um passo a mais, e quem toca fora acha, com razao, que ja salvou.
 *
 * **Mas fechar nao pode ser o UNICO momento em que se grava**, e foi essa a
 * causa do defeito mais feio que este arquivo ja teve: escrever num post-it
 * recem-criado e o texto simplesmente sumir. O cartao vive enquanto a linha do
 * Room existir, e a linha de um post-it novo tem vida curta -- nasce com id
 * negativo e, quando a criacao sobe, e APAGADA para dar lugar a uma linha com o
 * id do servidor. Para o Compose isso e a morte do item: o cartao sai da
 * composicao, o `LaunchedEffect` que gravaria e cancelado antes de rodar, e o
 * que a pessoa digitou existia so na memoria dele.
 *
 * Por isso o texto agora desce sozinho enquanto se escreve -- ver
 * [PAUSA_DO_RASCUNHO] e [PAUSA_DO_ENVIO]. E por isso a tela acompanha a troca
 * de id (ver [PostitsScreen]), senao o papel fecharia sozinho no meio da frase.
 *
 * ## A cor
 *
 * **Segurar abre o menu; tocar numa cor aplica na hora.** Antes as seis cores
 * eram bolinhas de 18 dp dentro do modo de edicao, e quase nunca pegavam: o
 * cartao inteiro escuta arrasto, e um dedo em cima de um alvo de 18 dp desliza
 * uns poucos pixels sem querer. Bastava isso para o arrasto vencer o toque e a
 * cor nao mudar. Com `input tap` -- que nao move um pixel -- funcionava sempre,
 * e foi por isso que nenhum teste automatico pegou.
 *
 * Segurar e um gesto que o arrasto nao disputa (um exige parar, o outro exige
 * mover), o menu fica FORA do cartao com alvos de 40 dp, e escolher ja aplica:
 * confirmar uma cor que se ve seria pedir duas vezes a mesma coisa.
 *
 * **Segurar so vale fora da edicao, e isso e proposital.** Dentro do campo,
 * segurar e "selecionar texto" -- convencao do sistema, nao nossa para
 * redefinir. Enquanto os cartoes ficavam presos em modo de edicao, quase todo
 * toque longo caia num campo de texto: abria a alca do cursor em vez do menu, e
 * dava a impressao de que segurar nao fazia nada. O conserto do paragrafo
 * anterior e o que faz este funcionar.
 */
@Composable
internal fun Postit(
    nota: NotaEntity,
    editando: Boolean,
    aoAbrirEdicao: () -> Unit,
    aoFecharEdicao: () -> Unit,
    aoEditar: (String) -> Unit,
    aoRascunhar: (String) -> Unit,
    aoRecolorir: (String) -> Unit,
    aoMover: (Int, Int) -> Unit,
    aoApagar: () -> Unit,
) {
    val tom = tomDe(nota.color)
    val densidade = LocalDensity.current
    val tato = LocalHapticFeedback.current
    val gerenteDeFoco = LocalFocusManager.current
    val foco = remember { FocusRequester() }

    // `TextFieldValue` e nao `String` porque aqui a POSICAO DO CURSOR importa:
    // quando o id provisorio vira definitivo, este cartao e recriado do zero, e
    // um campo de texto recem-nascido comeca com o cursor no inicio. O efeito
    // era escrever "MEIODAFRASE", a nota trocar de id, e a continuacao entrar de
    // tras para a frente -- "-FORAMEIODAFRASE". Guardando o intervalo junto do
    // texto, retomar uma edicao poe o cursor onde ela parou: no fim.
    var campo by remember(nota.id) {
        mutableStateOf(TextFieldValue(nota.content, TextRange(nota.content.length)))
    }
    val texto = campo.text
    var menuDeCor by remember(nota.id) { mutableStateOf(false) }

    // A ultima versao que foi para a fila de envio. Sem isto, o rascunho abaixo
    // faria `nota.content` alcancar `texto` antes de fechar, e a comparacao de
    // fechamento nunca acharia nada para mandar.
    var naFila by remember(nota.id) { mutableStateOf(nota.content) }

    // Abrir leva o teclado junto; fechar grava. Os tres caminhos de saida
    // (tocar fora, tocar em outro post-it, tocar em "Pronto") sao um so daqui
    // de dentro: `editando` virou `false`.
    LaunchedEffect(editando) {
        if (editando) {
            naFila = texto
            runCatching { foco.requestFocus() }
        } else if (texto != naFila) {
            aoEditar(texto)
            naFila = texto
        }
    }

    // Fora da edicao, o que se mostra segue o banco -- e assim que uma alteracao
    // feita no site aparece no cartao. DENTRO dela, nao: puxar o texto por baixo
    // de quem esta escrevendo perderia a frase e ainda jogaria o cursor para o
    // fim. Era esta a chave que faltava sair do `remember` acima.
    LaunchedEffect(nota.id, nota.content) {
        if (!editando && nota.content != texto) {
            campo = TextFieldValue(nota.content, TextRange(nota.content.length))
        }
    }

    // Enquanto se escreve, o texto desce sozinho: ao Room quase de imediato, a
    // fila de envio so depois de uma pausa de verdade.
    //
    // As duas esperas tem motivos diferentes. A curta existe porque a memoria
    // desta funcao e a coisa mais fragil do sistema -- o cartao sai da
    // composicao quando o id provisorio vira definitivo, e o que estava so aqui
    // dentro morre com ele. A longa existe para nao encher a fila de PATCHes
    // que so se sobrescrevem: uma frase inteira sobe uma vez.
    LaunchedEffect(editando, texto) {
        if (!editando) return@LaunchedEffect
        delay(PAUSA_DO_RASCUNHO)
        if (texto != nota.content) aoRascunhar(texto)
        delay(PAUSA_DO_ENVIO - PAUSA_DO_RASCUNHO)
        if (texto != naFila) {
            aoEditar(texto)
            naFila = texto
        }
    }

    // A posicao mostrada, em dp. Durante o arrasto quem manda e o dedo; fora
    // dele, o Room.
    var x by remember(nota.id) { mutableFloatStateOf(nota.x.toFloat()) }
    var y by remember(nota.id) { mutableFloatStateOf(nota.y.toFloat()) }
    var arrastando by remember(nota.id) { mutableStateOf(false) }

    // O que foi mandado para o banco e ainda nao voltou. Enquanto houver algo
    // aqui, a linha do Room esta atrasada em relacao ao que a pessoa ve, e
    // seguir a linha seria justamente o pulo de volta descrito acima.
    var enviado by remember(nota.id) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(nota.x, nota.y, arrastando) {
        if (arrastando) return@LaunchedEffect
        val alvo = enviado
        if (alvo == null) {
            x = nota.x.toFloat()
            y = nota.y.toFloat()
        } else if (nota.x == alvo.first && nota.y == alvo.second) {
            // Chegou. A partir daqui o banco volta a mandar -- e assim uma
            // mudanca vinda do site, pela sincronizacao, move o cartao.
            enviado = null
        }
    }

    val inclinacao = inclinacaoDe(nota.id)
    val radianos = Math.toRadians(inclinacao.toDouble())
    val cosseno = cos(radianos).toFloat()
    val seno = sin(radianos).toFloat()
    val porDp = densidade.density

    Box(
        modifier = Modifier
            // Quem esta na mao passa por cima dos outros; sem isto o cartao
            // arrastado desliza POR BAIXO dos vizinhos, e parece travamento.
            .zIndex(if (arrastando || menuDeCor) 1f else 0f)
            .offset { IntOffset((x * porDp).roundToInt(), (y * porDp).roundToInt()) }
            .size(width = nota.width.dp, height = nota.height.dp),
    ) {
        Column(
            modifier = Modifier
                .size(width = nota.width.dp, height = nota.height.dp)
                .rotate(inclinacao)
                .shadow(if (arrastando) 12.dp else 6.dp, RoundedCornerShape(Canto.cartao))
                .clip(RoundedCornerShape(Canto.cartao))
                // O papel: mais claro no topo, a cor cheia embaixo. Mesma conta
                // do `--note-tint` no CSS.
                .background(
                    Brush.verticalGradient(
                        0f to misturarComBranco(tom, 0.74f),
                        0.58f to misturarComBranco(tom, 0.90f),
                        1f to tom,
                    )
                )
                // Segurar abre o menu de cor. Fica num `pointerInput` proprio,
                // separado do arrasto: sao dois detectores que rodam juntos e
                // decidem por criterios opostos -- um espera meio segundo
                // parado, o outro espera o dedo andar. No mesmo bloco, um
                // cancelaria o outro.
                .pointerInput(nota.id) {
                    detectTapGestures(
                        onLongPress = {
                            tato.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuDeCor = true
                        },
                    )
                }
                .pointerInput(nota.id) {
                    detectDragGestures(
                        onDragStart = { arrastando = true },
                        onDragEnd = {
                            arrastando = false
                            val destino = x.roundToInt() to y.roundToInt()
                            if (destino.first != nota.x || destino.second != nota.y) {
                                enviado = destino
                                aoMover(destino.first, destino.second)
                            }
                        },
                        // Gesto interrompido (uma ligacao entrando, o sistema
                        // roubando o toque): volta para onde o banco diz.
                        // Gravar aqui seria gravar um arrasto que a pessoa nao
                        // terminou.
                        onDragCancel = {
                            arrastando = false
                            x = nota.x.toFloat()
                            y = nota.y.toFloat()
                        },
                    ) { mudanca, deslocamento ->
                        mudanca.consume()
                        val dx = (deslocamento.x * cosseno - deslocamento.y * seno) / porDp
                        val dy = (deslocamento.x * seno + deslocamento.y * cosseno) / porDp
                        x = (x + dx).coerceIn(0f, Repositorio.MAX_X.toFloat())
                        y = (y + dy).coerceIn(0f, Repositorio.MAX_Y.toFloat())
                    }
                }
                .padding(Espaco.e2),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (editando) {
                BasicTextField(
                    value = campo,
                    onValueChange = { campo = it },
                    textStyle = TipografiaBeazeth.bodyLarge.copy(
                        color = TINTA_DO_PAPEL,
                        fontSize = 15.sp,
                    ),
                    cursorBrush = SolidColor(TINTA_DO_PAPEL),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(foco),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
                ) {
                    // "Pronto" nao salva: quem salva e sair da edicao. Ele so
                    // fecha e baixa o teclado -- e continua existindo porque
                    // com o teclado aberto sobra pouca tela para "tocar fora".
                    AcaoDoPostit(texto = "Pronto", cor = TINTA_DO_PAPEL, forte = true) {
                        gerenteDeFoco.clearFocus()
                        aoFecharEdicao()
                    }
                    AcaoDoPostit(texto = "Apagar", cor = Color(0xFF7A3A3A), aoTocar = aoApagar)
                }
            } else {
                Text(
                    text = nota.content.ifBlank { "Toque para escrever…" },
                    color = if (nota.content.isBlank()) {
                        TINTA_DO_PAPEL.copy(alpha = 0.53f)
                    } else {
                        TINTA_DO_PAPEL
                    },
                    style = TipografiaBeazeth.bodyLarge.copy(fontSize = 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Os dois gestos no MESMO tratador, e nao um dentro do
                        // outro. O `detectTapGestures` do cartao fica por
                        // fora deste texto, entao o toque chegava aqui
                        // primeiro e um `clickable` comum devolve clique
                        // mesmo depois de meio segundo segurando: segurar
                        // sobre o texto abria a edicao em vez do menu de cor.
                        .combinedClickable(
                            onClick = aoAbrirEdicao,
                            onLongClick = {
                                tato.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuDeCor = true
                            },
                        ),
                )
                // Sem conta o id negativo e a regra, e nao uma pendencia: o selo
                // avisaria para sempre sobre um envio que ninguem pediu.
                if (nota.id < 0 && !LocalModoLocal.current) {
                    Text(
                        text = "não enviado",
                        style = TipografiaBeazeth.bodyMedium.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TINTA_DO_PAPEL.copy(alpha = 0.6f),
                    )
                }
            }
        }

        if (menuDeCor) {
            MenuDeCor(
                atual = nota.color,
                aoEscolher = {
                    aoRecolorir(it)
                    menuDeCor = false
                },
                aoFechar = { menuDeCor = false },
            )
        }
    }
}

/**
 * As seis cores, num balao acima do post-it.
 *
 * `Popup` e nao um `Box` dentro do cartao: o menu precisa passar POR CIMA dos
 * vizinhos e pode sair da area do proprio post-it -- dentro do cartao ele seria
 * cortado pelo `clip` do papel.
 */
@Composable
private fun MenuDeCor(
    atual: String,
    aoEscolher: (String) -> Unit,
    aoFechar: () -> Unit,
) {
    val cores = Doce
    val densidade = LocalDensity.current
    val respiro = with(densidade) { 8.dp.roundToPx() }

    Popup(
        popupPositionProvider = remember(respiro) { PosicaoDoBalao(respiro) },
        onDismissRequest = aoFechar,
        properties = PopupProperties(focusable = true),
    ) {
        Row(
            modifier = Modifier
                .shadow(10.dp, RoundedCornerShape(999.dp))
                .clip(RoundedCornerShape(999.dp))
                .background(cores.superficie)
                .border(1.dp, cores.traco, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((nome, tom) in CORES) {
                Box(
                    // 40 dp de alvo com uma bolinha de 26 dentro: o alvo e para
                    // o dedo, o desenho e para o olho.
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { aoEscolher(nome) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (nome == atual) 30.dp else 26.dp)
                            .clip(CircleShape)
                            .background(tom)
                            .border(
                                width = if (nome == atual) 3.dp else 1.dp,
                                color = if (nome == atual) cores.destaqueEscuro else cores.traco,
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Acima do papel, e abaixo dele quando nao cabe acima.
 *
 * Um `alignment` fixo nao serve: o balao tem quase 280 dp e o quadro rola nos
 * dois eixos, entao um post-it encostado no alto da tela abriria o menu para
 * fora da janela e um encostado na direita o cortaria pela metade. Aqui as duas
 * coisas se resolvem com a conta que o `Popup` ja oferece -- prender no eixo x,
 * virar de lado no eixo y.
 */
private class PosicaoDoBalao(private val respiro: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centralizado = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = centralizado.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val acima = anchorBounds.top - popupContentSize.height - respiro
        val y = if (acima >= 0) acima else anchorBounds.bottom + respiro
        return IntOffset(x, y)
    }
}

@Composable
private fun AcaoDoPostit(
    texto: String,
    cor: Color,
    forte: Boolean = false,
    aoTocar: () -> Unit,
) {
    Text(
        text = texto,
        style = if (forte) {
            TipografiaBeazeth.titleMedium.copy(fontSize = 13.sp)
        } else {
            TipografiaBeazeth.bodyMedium.copy(fontSize = 13.sp)
        },
        color = cor,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = aoTocar)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
