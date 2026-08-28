package com.beazeth.notifier.ui.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * Os pedacos visuais do site, um a um.
 *
 * Estao aqui e nao dentro da tela de login porque as sete telas da Fase 3 vao
 * usar os mesmos: o cartao, o campo com rotulo em cima e o botao em degrade
 * aparecem em todas. Deixa-los na tela de login significaria copia-los sete
 * vezes -- e sete lugares para uma cor sair de sincronia.
 *
 * Nao sao os componentes do Material 3. O `OutlinedTextField` tem rotulo
 * flutuante que sobe para dentro da borda, e o site tem rotulo fixo em cima do
 * campo. Sao gestos diferentes; usar o pronto entregaria outro produto.
 */

/** O `.auth-card`: cartao translucido, centrado, com sombra baixa e larga. */
@Composable
fun CartaoDoce(
    modifier: Modifier = Modifier,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Doce.sombra,
                spotColor = Doce.sombra,
            )
            .clip(RoundedCornerShape(26.dp))
            .background(Doce.superficie)
            .border(1.dp, Doce.traco, RoundedCornerShape(26.dp))
            .padding(Espaco.e5),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
        content = conteudo,
    )
}

/**
 * O `.brand-dot`: circulo em degrade conico, com um halo rosa em volta.
 *
 * O `sweepGradient` do Compose comeca as 3 horas e o `conic-gradient` do CSS as
 * 12, dai a rotacao de -80 graus (o site pede `from 10deg`). Sem ela as cores
 * ficam giradas um quarto de volta.
 *
 * O halo ocupa 6/32 do raio, como no site -- por isso ele escala junto quando
 * o ponto aparece menor na barra superior.
 */
@Composable
fun PontoDaMarca(tamanho: Dp = 32.dp) {
    // Capturada fora do `drawBehind`, que nao e contexto de composicao.
    val cores = Doce

    Box(
        modifier = Modifier
            .size(tamanho)
            .drawBehind {
                val centro = Offset(size.width / 2f, size.height / 2f)
                val raio = size.minDimension / 2f

                drawCircle(
                    color = cores.destaque.copy(alpha = 0.14f),
                    radius = raio,
                    center = centro,
                )
                rotate(degrees = -80f, pivot = centro) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                cores.destaque,
                                Color(0xFFFFD0EC),
                                cores.azulSuave,
                                cores.destaque,
                            ),
                            center = centro,
                        ),
                        radius = raio * (26f / 32f),
                        center = centro,
                    )
                }
            },
    )
}

/** O `.auth-brand`: o ponto colorido, o nome e a linha de baixo. */
@Composable
fun Marca(nome: String, lema: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
    ) {
        PontoDaMarca()
        Column {
            Text(
                text = nome,
                style = TipografiaBeazeth.titleLarge,
                color = Doce.tinta,
            )
            Text(
                text = lema,
                style = TipografiaBeazeth.bodyMedium,
                color = Doce.tintaSuave,
            )
        }
    }
}

/**
 * O campo do `.event-form`: rotulo em cima, caixa arredondada, anel rosa ao
 * focar.
 *
 * O espaco do anel esta reservado o tempo todo (o `padding` externo e fixo e so
 * a cor entra e sai). Se ele so existisse com o foco, cada toque num campo
 * empurraria a tela dois pixels -- o tipo de tremor que faz uma interface
 * parecer barata.
 */
@Composable
fun CampoDoce(
    rotulo: String,
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    ocultarTexto: Boolean = false,
    opcoesDoTeclado: KeyboardOptions = KeyboardOptions.Default,
    acoesDoTeclado: KeyboardActions = KeyboardActions.Default,
    /** Texto de dica dentro do campo vazio. O site nao usa nenhum nas telas de
     *  conta, mas usa nos campos de criar -- e sem ele um campo vazio no meio
     *  de uma lista nao diz para que serve. */
    dica: String? = null,
    /** Raio do canto. O padrao e o das telas; a tela de to-do pede mais reto. */
    canto: Dp = Canto.caixa,
    /**
     * Troca a caixa por uma linha de escrever.
     *
     * E o que faz o to-do parecer agenda: numa pagina de papel nao ha caixinha,
     * ha a pauta. A area de toque continua a mesma -- so a moldura sai.
     */
    sublinhado: Boolean = false,
    /**
     * Para devolver o cursor a este campo de fora.
     *
     * Existe por causa do foco que fugia: enquanto o pedido de login estava no
     * ar, os campos ficavam desligados e o foco escorregava para o unico
     * clicavel que sobrava no cartao. Ver [RodapeComLink].
     */
    foco: FocusRequester? = null,
) {
    var focado by remember { mutableStateOf(false) }
    val anel = if (focado) Doce.destaque.copy(alpha = 0.20f) else Color.Transparent
    val borda = if (focado) Doce.destaque else Doce.traco

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Espaco.e1),
    ) {
        // Rotulo vazio some em vez de reservar espaco: o campo de nova tarefa
        // fica dentro de uma lista, e um espaco em branco acima dele quebraria
        // o ritmo das linhas.
        if (rotulo.isNotEmpty()) {
            Text(
                text = rotulo,
                style = TipografiaBeazeth.labelLarge,
                color = Doce.tintaSuave,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (sublinhado) {
                        Modifier
                            .padding(4.dp)
                            .drawBehind {
                                // A pauta: uma linha so, embaixo. Engrossa e
                                // acende no foco, que e o unico sinal de que o
                                // campo esta ativo sem a moldura.
                                val grossura = if (focado) 2.dp.toPx() else 1.dp.toPx()
                                drawLine(
                                    color = borda,
                                    start = Offset(0f, size.height - grossura / 2f),
                                    end = Offset(size.width, size.height - grossura / 2f),
                                    strokeWidth = grossura,
                                )
                            }
                            .padding(horizontal = 2.dp, vertical = 9.dp)
                    } else {
                        Modifier
                            .background(anel, RoundedCornerShape(canto + 4.dp))
                            .padding(4.dp)
                            .background(Doce.fundoCampo, RoundedCornerShape(canto))
                            .border(1.dp, borda, RoundedCornerShape(canto))
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                    }
                ),
        ) {
            if (dica != null && valor.isEmpty()) {
                Text(
                    text = dica,
                    style = TipografiaBeazeth.bodyLarge.copy(color = Doce.tintaSuave),
                )
            }
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                enabled = habilitado,
                singleLine = true,
                textStyle = TipografiaBeazeth.bodyLarge.copy(color = Doce.tinta),
                cursorBrush = SolidColor(Doce.destaque),
                visualTransformation = if (ocultarTexto) {
                    SenhaOculta
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = opcoesDoTeclado,
                keyboardActions = acoesDoTeclado,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (foco != null) Modifier.focusRequester(foco) else Modifier)
                    .onFocusChanged { focado = it.isFocused },
            )
        }
    }
}

/** Uma so instancia: a transformacao nao guarda estado. */
private val SenhaOculta = PasswordVisualTransformation()

/**
 * O `.btn-primary`: pilula em degrade, largura toda, texto na fonte de display.
 *
 * `RoundedCornerShape(50)` e por cento, e nao `999.dp` como o CSS: em Compose a
 * porcentagem acompanha a altura, entao a pilula continua pilula se o texto
 * crescer com a fonte do sistema.
 */
@Composable
fun BotaoPrimario(
    texto: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    carregando: Boolean = false,
) {
    val opacidade = if (habilitado) 1f else 0.5f
    val forma = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (habilitado) 10.dp else 0.dp,
                shape = forma,
                ambientColor = Doce.destaqueEscuro,
                spotColor = Doce.destaqueEscuro,
            )
            .clip(forma)
            .background(
                Brush.linearGradient(
                    listOf(
                        Doce.destaque.copy(alpha = opacidade),
                        Doce.destaqueClaro.copy(alpha = opacidade),
                    )
                )
            )
            .clickable(
                enabled = habilitado,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClick = aoTocar,
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            if (carregando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = texto,
                style = TipografiaBeazeth.titleMedium,
                color = Color.White.copy(alpha = opacidade),
            )
        }
    }
}

/**
 * O aviso de erro, no formato das mensagens do site (`--error-bg`/`--error-bd`).
 *
 * Cartao e nao texto solto: uma linha vermelha perdida entre os campos passa
 * despercebida justamente quando mais importa.
 */
@Composable
fun AvisoDeErro(mensagem: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Doce.erroFundo)
            .border(1.dp, Doce.erroBorda, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = mensagem,
            style = TipografiaBeazeth.bodyMedium,
            color = Doce.perigo,
        )
    }
}

/**
 * O `.auth-switch`: a linha que alterna entre entrar e criar conta.
 *
 * Duas `Text` numa linha, e nao uma so com trecho clicavel: a area de toque de
 * um trecho anotado e a do texto, uns 14 dp de altura -- pequena demais para o
 * polegar. Aqui o alvo tem folga em volta.
 */
@Composable
fun RodapeComLink(
    prefixo: String,
    link: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Desligar isto tira o link do caminho do foco, e nao so do toque.
     *
     * Enquanto o login esta no ar, os campos e o botao ficam desligados. Se
     * este link continuasse ligado, ele seria o unico ponto do cartao capaz de
     * receber foco -- e o foco ia parar nele sozinho. Como no Compose um
     * `clickable` com foco responde ao Enter, a proxima tecla abria a tela de
     * criar conta em vez de tentar entrar de novo. Foi assim que "aperto Enter
     * para entrar e ele abre criar conta" acontecia toda vez que a senha
     * estava errada.
     */
    habilitado: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prefixo,
            style = TipografiaBeazeth.bodyMedium,
            color = Doce.tintaSuave,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(
                    enabled = habilitado,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = Doce.destaque),
                    role = Role.Button,
                    onClick = aoTocar,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = link,
                style = TipografiaBeazeth.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
                color = Doce.destaqueEscuro,
            )
        }
    }
}

/** Titulo e subtitulo do cartao (`h2` + `.auth-subtitle`). */
@Composable
fun TituloDoCartao(titulo: String, subtitulo: String) {
    Text(
        text = titulo,
        style = TipografiaBeazeth.headlineMedium,
        color = Doce.tinta,
    )
    Text(
        text = subtitulo,
        style = TipografiaBeazeth.bodyLarge,
        color = Doce.tintaSuave,
    )
}

/**
 * Uma caixa que parece campo mas abre um seletor.
 *
 * Nasceu na tela de agenda, para data e hora. Virou componente quando o editor
 * do planner precisou da mesma coisa para inicio e fim: e a regra do CSS do
 * site aplicada aqui -- o que serve duas telas sai da tela e vira peca.
 */
@Composable
fun SeletorEmCaixa(
    rotulo: String,
    valor: String,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Espaco.e1),
    ) {
        Text(
            text = rotulo,
            style = TipografiaBeazeth.labelLarge,
            color = Doce.tintaSuave,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Canto.caixa))
                .background(Doce.fundoCampo)
                .border(1.dp, Doce.traco, RoundedCornerShape(Canto.caixa))
                .clickable(onClick = aoTocar)
                .padding(horizontal = 12.dp, vertical = 13.dp),
        ) {
            Text(
                text = valor,
                style = TipografiaBeazeth.bodyLarge,
                color = Doce.tinta,
            )
        }
    }
}
