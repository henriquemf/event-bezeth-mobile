package com.beazeth.notifier.ui.telas.postits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.data.Remapeamentos
import com.beazeth.notifier.data.Repositorio
import com.beazeth.notifier.data.Sincronizador
import com.beazeth.notifier.ui.componentes.BotaoPilula
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.theme.Canto
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * O quadro de post-its, como a tela inicial do site.
 *
 * **Um quadro de verdade, e nao uma lista.** A primeira versao virava lista
 * porque um quadro de 4000 px nao cabe em 360 dp. Mas trocar o quadro por lista
 * jogava fora o que os post-its tem de proprio: a posicao e a inclinacao sao
 * memoria espacial -- "o do dentista fica no canto de cima" --, e uma lista
 * ordenada por id apaga isso. Entao o quadro ficou, e o que se adapta e o
 * enquadramento: ele rola nos dois eixos e so cresce ate onde ha post-it.
 *
 * Arrastar move de verdade, e a posicao sobe para o servidor -- quem abrir no
 * computador ve o quadro do jeito que foi deixado no celular, e vice-versa.
 *
 * A tela esta dividida como no site (`js/pages/notes/`): as constantes em
 * [QUADROS]/[CORES], o estado no [PostitsViewModel], o cartao em [Postit] e
 * aqui a montagem. Era um arquivo so de 561 linhas -- ainda abaixo do teto de
 * 700, mas ja com quatro assuntos disputando o mesmo lugar.
 */
@Composable
fun PostitsScreen(vm: PostitsViewModel = viewModel()) {
    val quadro by vm.quadro.collectAsState()
    val notas by vm.notas.collectAsState()
    val cores = Doce
    val densidade = LocalDensity.current

    var novo by remember { mutableStateOf("") }
    var corEscolhida by remember { mutableStateOf(CORES.first().first) }

    // A largura VISIVEL do quadro, e nao a do conteudo. Era medida no Box de
    // dentro, que tem o tamanho do que ja foi espalhado -- entao quanto mais
    // longe alguem arrastasse um post-it, mais larga a tela "ficava", e tanto o
    // lugar do proximo post-it quanto o "Organizar" passavam a contar com um
    // espaco que ninguem enxerga. Medida aqui, e o que cabe na tela.
    var larguraVisivel by remember { mutableIntStateOf(360) }

    // Qual post-it esta aberto para escrita -- UM, e da tela, nao de cada
    // cartao. Com um `editando` por cartao, abrir o segundo nao fechava o
    // primeiro (em modo de toque o Compose nao passa foco para um `clickable`),
    // e dava para ficar com o quadro inteiro em modo de edicao.
    var editandoId by remember { mutableStateOf<Long?>(null) }
    val gerenteDeFoco = LocalFocusManager.current

    // O post-it aberto pode trocar de id debaixo da mao: um cartao recem-criado
    // nasce com id negativo e recebe o do servidor assim que a criacao sobe --
    // cerca de um segundo depois, ou seja, bem no meio da primeira frase. Sem
    // seguir a troca, `editandoId` ficaria apontando para uma linha que nao
    // existe mais e o papel fecharia sozinho enquanto a pessoa escreve.
    LaunchedEffect(Unit) {
        Remapeamentos.fluxo.collect { troca ->
            if (troca.entidade == Sincronizador.ALVO_NOTAS && editandoId == troca.de) {
                editandoId = troca.para
            }
        }
    }

    val criar = {
        vm.criar(novo, corEscolhida, larguraVisivel)
        novo = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                larguraVisivel = with(densidade) { it.width.toDp().value.toInt() }
            },
    ) {
        // ------------------------------------------------------ barra de topo
        Column(
            modifier = Modifier.padding(
                start = Espaco.e5, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e2,
            ),
            verticalArrangement = Arrangement.spacedBy(Espaco.e2),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
            ) {
                for ((chave, rotulo, emoji) in QUADROS) {
                    FiltroDeQuadro(
                        rotulo = "$emoji $rotulo",
                        ativo = chave == quadro,
                        aoTocar = { vm.trocarQuadro(chave) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    CampoDeNota(
                        valor = novo,
                        aoMudar = { novo = it },
                        aoEnviar = { if (novo.isNotBlank()) criar() },
                    )
                }
                for ((nome, tom) in CORES) {
                    AmostraDeCor(
                        tom = tom,
                        ativa = nome == corEscolhida,
                        aoTocar = { corEscolhida = nome },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Espaco.e2),
            ) {
                Text(
                    text = if (notas.size == 1) "1 post-it" else "${notas.size} post-its",
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                    modifier = Modifier.weight(1f),
                )
                BotaoPilula(texto = "Organizar", aoTocar = { vm.organizar(larguraVisivel) })
                // Sem texto no campo, cria um post-it em branco: e assim que o
                // site faz, e e como se usa um bloco de papel -- pega uma
                // folha primeiro, escreve depois.
                BotaoPilula(texto = "Novo post-it", aoTocar = { criar() }, destacado = true)
            }
        }

        // ------------------------------------------------------------ quadro
        if (notas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Espaco.e5),
                contentAlignment = Alignment.TopCenter,
            ) {
                CartaoDaTela {
                    Text(
                        text = "Nenhum post-it aqui ainda.",
                        style = TipografiaBeazeth.bodyLarge,
                        color = cores.tintaSuave,
                    )
                    Text(
                        text = "Escreva acima e toque em Novo post-it. Depois é só arrastar " +
                            "para onde quiser.",
                        style = TipografiaBeazeth.bodyMedium,
                        color = cores.tintaSuave,
                    )
                }
            }
        } else {
            // O quadro so cresce ate onde ha post-it, e rola nos dois eixos --
            // e assim que 4000 px de largura cabem numa tela de 360 dp sem
            // virar um mapa vazio.
            val larguraNecessaria = notas.maxOf { it.x + it.width } + Repositorio.FOLGA
            val alturaNecessaria = notas.maxOf { it.y + it.height } + Repositorio.FOLGA

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    // Tocar no vazio do quadro fecha a edicao -- e fechar
                    // grava. Fica aqui, no `Box` que ocupa a tela toda, e nao
                    // no quadro de dentro: com poucos post-its o quadro e menor
                    // que a area visivel, e o toque cairia fora dele.
                    .pointerInput(Unit) {
                        detectTapGestures {
                            gerenteDeFoco.clearFocus()
                            editandoId = null
                        }
                    },
            ) {
                Box(
                    modifier = Modifier.requiredSize(
                        width = larguraNecessaria.dp,
                        height = alturaNecessaria.dp,
                    ),
                ) {
                    for (nota in notas) {
                        Postit(
                            nota = nota,
                            editando = editandoId == nota.id,
                            aoAbrirEdicao = { editandoId = nota.id },
                            aoFecharEdicao = { editandoId = null },
                            aoEditar = { vm.editar(nota, it) },
                            aoRascunhar = { vm.rascunhar(nota, it) },
                            aoRecolorir = { vm.recolorir(nota, it) },
                            aoMover = { x, y -> vm.mover(nota, x, y) },
                            aoApagar = { vm.apagar(nota) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampoDeNota(valor: String, aoMudar: (String) -> Unit, aoEnviar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Canto.botao))
            .background(Doce.fundoCampo)
            .border(1.dp, Doce.traco, RoundedCornerShape(Canto.botao))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (valor.isEmpty()) {
            Text(
                text = "Novo post-it…",
                style = TipografiaBeazeth.bodyLarge.copy(color = Doce.tintaSuave),
            )
        }
        BasicTextField(
            value = valor,
            onValueChange = aoMudar,
            singleLine = true,
            textStyle = TipografiaBeazeth.bodyLarge.copy(color = Doce.tinta),
            cursorBrush = SolidColor(Doce.destaque),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { aoEnviar() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FiltroDeQuadro(rotulo: String, ativo: Boolean, aoTocar: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (ativo) Doce.destaque.copy(alpha = 0.20f) else Doce.fundoCampo)
            .border(
                1.dp,
                if (ativo) Doce.destaque else Doce.traco,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = aoTocar)
            .padding(horizontal = Espaco.e3, vertical = Espaco.e2),
    ) {
        Text(
            text = rotulo,
            style = TipografiaBeazeth.titleMedium.copy(fontSize = 13.sp),
            color = if (ativo) Doce.destaqueEscuro else Doce.tintaSuave,
        )
    }
}

@Composable
internal fun AmostraDeCor(
    tom: Color,
    ativa: Boolean,
    aoTocar: () -> Unit,
    tamanho: Dp = 24.dp,
) {
    Box(
        modifier = Modifier
            .size(if (ativa) tamanho + 4.dp else tamanho)
            .clip(CircleShape)
            .background(tom)
            .border(
                width = if (ativa) 2.dp else 1.dp,
                color = if (ativa) Doce.destaqueEscuro else Doce.traco,
                shape = CircleShape,
            )
            .clickable(onClick = aoTocar),
    )
}
