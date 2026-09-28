package com.beazeth.notifier.ui.telas.postits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.beazeth.notifier.data.MAXIMO_DO_TEXTO
import com.beazeth.notifier.data.TextoRico
import com.beazeth.notifier.data.TipoDeMarca
import com.beazeth.notifier.data.linkAceito
import com.beazeth.notifier.data.lerTextoRico
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * O editor de texto formatado do post-it, e a barra que aparece sobre a selecao.
 *
 * ## O estado mora em dois lugares, de proposito
 *
 * O campo do Compose so sabe de texto puro e cursor ([TextFieldValue]); a
 * formatacao vive ao lado, em [TextoRico] (marcas por intervalo). A cada
 * tecla, [EditorRico.mudou] empurra as marcas para onde o texto foi; a
 * [TransformacaoRica] pinta o resultado por cima do texto puro, sem mudar
 * uma posicao sequer -- e por isso o `OffsetMapping` e a identidade.
 *
 * ## A barra substitui o menu do sistema
 *
 * Ao selecionar, o Compose pede ao [TextToolbar] que mostre copiar/colar. A
 * [BarraDeFormato] e esse [TextToolbar]: mostra B, I, U, S e link, e junto os
 * mesmos copiar, recortar, colar e selecionar tudo que o sistema mostraria. Um
 * menu so, e nao dois disputando o espaco em cima da selecao -- que e como o
 * Notion faz no celular.
 */

/** O texto formatado como o Compose desenha. Com [comLinks], o link abre ao tocar. */
internal fun TextoRico.anotado(corDoLink: Color, comLinks: Boolean): AnnotatedString = buildAnnotatedString {
    for (pedaco in pedacos()) {
        val e = pedaco.estilo
        val decoracoes = listOfNotNull(
            TextDecoration.Underline.takeIf { e.sublinhado || e.link != null },
            TextDecoration.LineThrough.takeIf { e.riscado },
        )
        val estilo = SpanStyle(
            fontWeight = if (e.negrito) FontWeight.Bold else null,
            fontStyle = if (e.italico) FontStyle.Italic else null,
            textDecoration = if (decoracoes.isEmpty()) null else TextDecoration.combine(decoracoes),
            color = if (e.link != null) corDoLink else Color.Unspecified,
        )
        if (e.link != null && comLinks) {
            withLink(LinkAnnotation.Url(e.link, TextLinkStyles(style = estilo))) { append(pedaco.texto) }
        } else {
            withStyle(estilo) { append(pedaco.texto) }
        }
    }
}

/** Pinta a formatacao sobre o texto do campo, sem mudar posicao nenhuma. */
private class TransformacaoRica(
    private val rico: TextoRico,
    private val corDoLink: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        // So pinta se o que o campo tem e o que as marcas descrevem: num quadro
        // de atraso entre os dois, pintar marcas velhas sobre texto novo poria
        // negrito no lugar errado.
        val pintado = if (rico.texto == text.text) rico.anotado(corDoLink, comLinks = false) else text
        return TransformedText(pintado, OffsetMapping.Identity)
    }
}

/** O que esta sendo editado: o campo do Compose e as marcas, sempre juntos. */
@Stable
internal class EditorRico(inicial: TextoRico) {
    var rico by mutableStateOf(inicial)
        private set

    // `TextFieldValue`, e nao String: quando o id provisorio vira definitivo o
    // cartao renasce, e um campo novo comeca com o cursor no zero -- a frase
    // continuaria de tras para a frente. Ver `Postit`.
    var campo by mutableStateOf(TextFieldValue(inicial.texto, TextRange(inicial.texto.length)))
        private set

    val html: String get() = rico.paraHtml()

    fun mudou(novo: TextFieldValue) {
        // O limite e de TEXTO, como no site. Passando dele, a tecla nao entra.
        if (novo.text.length > MAXIMO_DO_TEXTO && novo.text.length > campo.text.length) return
        rico = rico.ajustar(novo.text)
        campo = novo
    }

    /** O banco mudou por fora (sincronizacao) e ninguem esta escrevendo. */
    fun carregar(html: String) {
        val lido = lerTextoRico(html)
        rico = lido
        campo = TextFieldValue(lido.texto, TextRange(lido.texto.length))
    }

    private val selecao: TextRange get() = campo.selection

    fun ligado(tipo: TipoDeMarca): Boolean = rico.tem(tipo, selecao.min, selecao.max)

    fun alternar(tipo: TipoDeMarca) {
        rico = rico.alternar(tipo, selecao.min, selecao.max)
    }

    fun linkDaSelecao(): String? = rico.linkEm(selecao.min)

    fun porLink(intervalo: TextRange, link: String?) {
        rico = rico.comLink(intervalo.min, intervalo.max, link)
    }

    fun intervaloSelecionado(): TextRange = selecao
}

@Composable
internal fun rememberEditorRico(id: Long, html: String): EditorRico =
    remember(id) { EditorRico(lerTextoRico(html)) }

/**
 * "exemplo.com" vira https://exemplo.com e "a@b.com" vira mailto:. Espelha
 * `normalizarLink` em `js/pages/notes/toolbar.js`.
 */
internal fun normalizarLink(valor: String): String {
    val limpo = valor.trim()
    if (limpo.isEmpty()) return ""
    if (Regex("^[^\\s@/]+@[^\\s@/]+\\.[^\\s@/]+$").matches(limpo)) return "mailto:$limpo"
    return if (Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE).containsMatchIn(limpo)) limpo else "https://$limpo"
}

/** O campo de texto do post-it, com a formatacao pintada. */
@Composable
internal fun CampoRico(
    editor: EditorRico,
    corDaTinta: Color,
    corDoLink: Color,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = editor.campo,
        onValueChange = editor::mudou,
        textStyle = TipografiaBeazeth.bodyLarge.copy(color = corDaTinta, fontSize = 15.sp),
        cursorBrush = SolidColor(corDaTinta),
        visualTransformation = TransformacaoRica(editor.rico, corDoLink),
        modifier = modifier,
    )
}

// ------------------------------------------------------------ a barra

/**
 * O [TextToolbar] do post-it: guarda o pedido do Compose e deixa a
 * [BarraFlutuante] desenhar. `status` diz ao Compose se ha menu na tela, e e
 * ele que decide quando esconder (tocar fora, apagar a selecao).
 */
@Stable
internal class BarraDeFormato : TextToolbar {
    class Pedido(
        val area: Rect,
        val copiar: (() -> Unit)?,
        val colar: (() -> Unit)?,
        val recortar: (() -> Unit)?,
        val tudo: (() -> Unit)?,
    )

    var pedido by mutableStateOf<Pedido?>(null)
        private set

    override val status: TextToolbarStatus
        get() = if (pedido != null) TextToolbarStatus.Shown else TextToolbarStatus.Hidden

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        pedido = Pedido(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun hide() {
        pedido = null
    }
}

/**
 * Acima da selecao, e abaixo quando nao cabe acima. `area` chega em
 * coordenadas da janela, que e o que o `Popup` usa -- o ancoramento do pai e
 * ignorado de proposito: quem sabe onde esta a selecao e o Compose, nao o
 * cartao.
 */
private class PosicaoDaBarra(private val area: Rect, private val respiro: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centro = ((area.left + area.right) / 2).toInt()
        val x = (centro - popupContentSize.width / 2).coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val acima = area.top.toInt() - popupContentSize.height - respiro
        val y = if (acima >= 0) acima else area.bottom.toInt() + respiro
        return IntOffset(x, y)
    }
}

/** A barra escura sobre a selecao, como a do site e a do Notion. */
@Composable
internal fun BarraFlutuante(
    barra: BarraDeFormato,
    editor: EditorRico,
    aoPedirLink: () -> Unit,
) {
    val pedido = barra.pedido ?: return
    val fundo = Color(0xFF231B26)
    val tinta = Color(0xFFF7EEF9)

    Popup(
        popupPositionProvider = PosicaoDaBarra(pedido.area, 16),
        // Sem foco: o teclado e o cursor continuam no post-it enquanto se
        // toca nos botoes. Com foco, tocar em B tiraria a selecao que B ia
        // formatar.
        properties = PopupProperties(focusable = false),
    ) {
        Row(
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(fundo)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for ((tipo, rotulo, descricao) in listOf(
                Triple(TipoDeMarca.NEGRITO, "B", "Negrito"),
                Triple(TipoDeMarca.ITALICO, "I", "Itálico"),
                Triple(TipoDeMarca.SUBLINHADO, "U", "Sublinhado"),
                Triple(TipoDeMarca.RISCADO, "S", "Riscado"),
            )) {
                BotaoDaBarra(
                    rotulo = rotulo,
                    descricao = descricao,
                    ligado = editor.ligado(tipo),
                    tinta = tinta,
                    estilo = when (tipo) {
                        TipoDeMarca.NEGRITO -> SpanStyle(fontWeight = FontWeight.Bold)
                        TipoDeMarca.ITALICO -> SpanStyle(fontStyle = FontStyle.Italic)
                        TipoDeMarca.SUBLINHADO -> SpanStyle(textDecoration = TextDecoration.Underline)
                        else -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    },
                    aoTocar = { editor.alternar(tipo) },
                )
            }
            BotaoDaBarra(
                rotulo = "🔗",
                descricao = "Link",
                ligado = editor.linkDaSelecao() != null,
                tinta = tinta,
                aoTocar = aoPedirLink,
            )
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(20.dp).background(tinta.copy(alpha = 0.2f)))
            // Os do sistema, que a barra substituiu. Cada um so aparece quando
            // o Compose o oferece -- colar sem nada copiado, por exemplo, nao.
            pedido.recortar?.let { BotaoDeTexto("Recortar", tinta, it) }
            pedido.copiar?.let { BotaoDeTexto("Copiar", tinta, it) }
            pedido.colar?.let { BotaoDeTexto("Colar", tinta, it) }
            pedido.tudo?.let { BotaoDeTexto("Tudo", tinta, it) }
        }
    }
}

@Composable
private fun BotaoDaBarra(
    rotulo: String,
    descricao: String,
    ligado: Boolean,
    tinta: Color,
    estilo: SpanStyle = SpanStyle(),
    aoTocar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            // Ligado nao e so cor (acessibilidade): o fundo muda E o leitor de
            // tela ouve "ligado".
            .background(if (ligado) Doce.destaque.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = aoTocar)
            .semantics {
                contentDescription = descricao
                stateDescription = if (ligado) "ligado" else "desligado"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buildAnnotatedString { withStyle(estilo) { append(rotulo) } },
            color = tinta,
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 17.sp),
        )
    }
}

@Composable
private fun BotaoDeTexto(texto: String, tinta: Color, aoTocar: () -> Unit) {
    Text(
        text = texto,
        color = tinta,
        style = TipografiaBeazeth.bodyMedium.copy(fontSize = 13.sp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = aoTocar)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

// ------------------------------------------------------------ o link

/**
 * Onde se escreve o endereco. Um `Dialog` e nao um campo dentro da barra: a
 * barra nao tem foco (ver [BarraFlutuante]), e um campo de texto sem foco nao
 * recebe teclado.
 */
@Composable
internal fun DialogoDeLink(
    atual: String?,
    aoAplicar: (String?) -> Unit,
    aoFechar: () -> Unit,
) {
    var valor by remember { mutableStateOf(TextFieldValue(atual.orEmpty(), TextRange(atual.orEmpty().length))) }
    var invalido by remember { mutableStateOf(false) }
    val cores = Doce

    Dialog(onDismissRequest = aoFechar) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cores.superficie)
                .padding(Espaco.e4),
            verticalArrangement = Arrangement.spacedBy(Espaco.e3),
        ) {
            Text("Link", style = TipografiaBeazeth.titleLarge, color = cores.tinta)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(cores.fundoCampo)
                    .border(1.dp, if (invalido) Color(0xFFD64545) else cores.traco, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                if (valor.text.isEmpty()) {
                    Text("Cole ou digite um link", style = TipografiaBeazeth.bodyLarge, color = cores.tintaSuave)
                }
                BasicTextField(
                    value = valor,
                    onValueChange = { valor = it; invalido = false },
                    singleLine = true,
                    textStyle = TipografiaBeazeth.bodyLarge.copy(color = cores.tinta),
                    cursorBrush = SolidColor(cores.destaque),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (invalido) {
                Text(
                    "Só links http, https ou de e-mail.",
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 13.sp),
                    color = Color(0xFFB03030),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Espaco.e2)) {
                if (atual != null) {
                    AcaoDoDialogo("Remover", cores.tintaSuave) { aoAplicar(null) }
                }
                Box(Modifier.weight(1f))
                AcaoDoDialogo("Cancelar", cores.tintaSuave, aoFechar)
                AcaoDoDialogo("Aplicar", cores.destaqueEscuro) {
                    val url = normalizarLink(valor.text)
                    when {
                        url.isEmpty() -> aoAplicar(null)
                        linkAceito(url) == null -> invalido = true
                        else -> aoAplicar(url)
                    }
                }
            }
        }
    }
}

@Composable
private fun AcaoDoDialogo(texto: String, cor: Color, aoTocar: () -> Unit) {
    Text(
        text = texto,
        style = TipografiaBeazeth.titleMedium,
        color = cor,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClick = aoTocar)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
