package com.beazeth.notifier.ui.telas.diario

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.local.DiarioEntity
import com.beazeth.notifier.ui.telas.calendario.MESES_CURTOS
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.misturar
import java.time.LocalDate
import java.time.YearMonth

/**
 * O ano inteiro em quadradinhos: doze colunas de mes, trinta e uma linhas de dia.
 *
 * ## Por que 31 linhas fixas
 *
 * Fevereiro tem 28 e as casas que sobram ficam VAZIAS, em vez de a coluna
 * encurtar. A grade e lida nas duas direcoes -- "como foi maio?" desce uma
 * coluna, "como foram todos os dias 12?" atravessa uma linha -- e encurtar o mes
 * curto desalinharia a segunda leitura inteira.
 *
 * ## Um Canvas so, e nao 372 composables
 *
 * Mesma escolha da grade de agua: 372 celulas viram 372 nos de layout, com
 * medicao e recomposicao de cada um. Aqui e um desenho e uma conta de divisao
 * para saber em qual casa o dedo caiu. O preco e nao ter acessibilidade por
 * celula -- e por isso o dia escolhido e dito por extenso no editor, em vez de
 * a cor ser a unica informacao.
 */
@Composable
internal fun GradeDoAno(
    ano: Int,
    dias: Map<LocalDate, DiarioEntity>,
    escolhido: LocalDate?,
    aoEscolher: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val medidor = rememberTextMeasurer()
    val hoje = remember { LocalDate.now() }

    // Quantos dias cada mes tem neste ano: fevereiro muda a cada quatro.
    val tamanhoDoMes = remember(ano) {
        (1..12).map { YearMonth.of(ano, it).lengthOfMonth() }
    }

    val vazia = misturar(cores.tinta, cores.superficie, 0.10f)
    val estiloMiudo = TextStyle(color = cores.tintaSuave, fontSize = 9.sp)

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        // Centralizada: com o teto da celula, num tablet a grade ocupa menos de
        // metade do cartao, e encostada a esquerda ela fica um retangulo
        // perdido num campo vazio. O teto existe porque 31 linhas de celula
        // grande passariam da altura da tela.
        contentAlignment = Alignment.TopCenter,
    ) {
        // A celula cresce ate caber doze colunas na largura disponivel, com um
        // teto para nao virar azulejo no tablet e um piso para o dedo acertar.
        val livre = maxWidth - CALHA
        val celula = ((livre + VAO) / COLUNAS - VAO).coerceIn(CELULA_MINIMA, CELULA_MAXIMA)
        val passo = celula + VAO

        Canvas(
            modifier = Modifier
                .width(CALHA + passo * COLUNAS - VAO)
                .height(CABECALHO + passo * LINHAS - VAO)
                .pointerInput(ano, passo) {
                    detectTapGestures { toque ->
                        val coluna = ((toque.x - CALHA.toPx()) / passo.toPx()).toInt()
                        val linha = ((toque.y - CABECALHO.toPx()) / passo.toPx()).toInt()
                        if (coluna in 0 until COLUNAS && linha in 0 until LINHAS &&
                            linha < tamanhoDoMes[coluna]
                        ) {
                            aoEscolher(LocalDate.of(ano, coluna + 1, linha + 1))
                        }
                    }
                },
        ) {
            val lado = celula.toPx()
            val p = passo.toPx()
            val calha = CALHA.toPx()
            val topo = CABECALHO.toPx()
            val canto = CornerRadius(lado * 0.28f)
            val aro = Stroke(width = 1.5.dp.toPx())

            for (coluna in 0 until COLUNAS) {
                val x = calha + coluna * p

                val rotulo = medidor.measure(MESES_CURTOS[coluna], estiloMiudo)
                drawText(
                    textLayoutResult = rotulo,
                    topLeft = Offset(x + (lado - rotulo.size.width) / 2f, 0f),
                )

                for (linha in 0 until LINHAS) {
                    // A casa que o mes nao tem fica em branco: ver o cabecalho.
                    if (linha >= tamanhoDoMes[coluna]) continue

                    val dia = LocalDate.of(ano, coluna + 1, linha + 1)
                    val registro = dias[dia]
                    val origem = Offset(x, topo + linha * p)
                    val tamanho = Size(lado, lado)

                    drawRoundRect(
                        color = Humor.porChave(registro?.mood)?.cor ?: vazia,
                        topLeft = origem,
                        size = tamanho,
                        cornerRadius = canto,
                    )

                    // Um ponto para "tem texto aqui": um dia pode ter anotacao
                    // sem humor escolhido, e ai a cor sozinha nao contaria.
                    if (!registro?.note.isNullOrEmpty()) {
                        drawCircle(
                            color = cores.tinta,
                            radius = lado * 0.11f,
                            center = Offset(origem.x + lado * 0.78f, origem.y + lado * 0.22f),
                        )
                    }

                    when (dia) {
                        escolhido -> drawRoundRect(
                            color = cores.tinta, topLeft = origem, size = tamanho,
                            cornerRadius = canto, style = aro,
                        )
                        hoje -> drawRoundRect(
                            color = cores.destaque, topLeft = origem, size = tamanho,
                            cornerRadius = canto, style = aro,
                        )
                    }
                }
            }

            // Os numeros do dia, de cinco em cinco: todos os 31 viraria uma
            // parede de digitos do lado de uma parede de cores.
            for (linha in 0 until LINHAS) {
                val numero = linha + 1
                if (numero != 1 && numero % 5 != 0) continue
                val texto = medidor.measure(numero.toString(), estiloMiudo)
                drawText(
                    textLayoutResult = texto,
                    topLeft = Offset(
                        calha - VAO.toPx() - texto.size.width,
                        topo + linha * p + (lado - texto.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

private const val COLUNAS = 12
private const val LINHAS = 31

private val CELULA_MINIMA = 11.dp
private val CELULA_MAXIMA = 26.dp
private val VAO = 3.dp

/** Espaco do cabecalho dos meses, acima da primeira linha. */
private val CABECALHO = 14.dp

/** A calha dos numeros do dia, a esquerda. */
private val CALHA = 20.dp
