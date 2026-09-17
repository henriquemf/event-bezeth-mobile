package com.beazeth.notifier.ui.telas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beazeth.notifier.data.local.AguaDiaEntity
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.telas.calendario.DIAS_LONGOS
import com.beazeth.notifier.ui.telas.calendario.MESES_CURTOS
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import com.beazeth.notifier.ui.theme.misturar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * O historico de agua como o grafico de contribuicoes do GitHub: um quadradinho
 * por dia, sete por coluna, uma coluna por semana, mais escuro quanto mais
 * perto da meta.
 *
 * ## Por que este desenho e nao um grafico de barras
 *
 * A pergunta que o historico responde e "tenho bebido agua?", e a resposta e
 * um PADRAO -- semanas boas, semanas fracas, fins de semana em branco -- e nao
 * um numero. Barras dao o numero de cada dia e escondem o padrao atras de
 * cinquenta alturas para comparar; a grade da o padrao de relance e o numero
 * ao toque.
 *
 * ## As cores
 *
 * Uma escala so, do azul do copo (`azulSuave`), do quase-vazio ao cheio: cor
 * sequencial para uma grandeza, sem trocar de matiz no meio. No tema escuro a
 * escala vai da superficie escura para o azul CLARO, e nao para um azul mais
 * escuro que sumiria no fundo -- a ordem "mais claro = mais agua" muda de lado,
 * mas continua monotona. O nivel e relativo a meta, e nao aos copos: seis copos
 * sao "quase la" para quem quer oito e "estourou" para quem quer cinco.
 *
 * ## O que cabe na tela
 *
 * Num celular cabem umas dezoito semanas; num tablet, um ano inteiro com
 * quadradinhos maiores. Quando o historico e mais longo do que cabe, a grade
 * rola para o lado, e comeca pelo fim -- hoje e o que interessa, e esta na
 * ultima coluna. O teto e um ano ([MAXIMO_DE_SEMANAS]), como no GitHub.
 *
 * Os litros saem do tamanho de copo ATUAL: o servidor guarda copos por dia, e
 * nao mililitros, entao quem mudar o copo de 250 para 300 ml ve o passado
 * recalculado. E uma aproximacao honesta; guardar ml por dia exigiria mudar o
 * servidor para responder a uma pergunta que ninguem fez ainda.
 */
@Composable
internal fun HistoricoDeAgua(
    dias: List<AguaDiaEntity>,
    meta: Int,
    ml: Int,
    modifier: Modifier = Modifier,
) {
    val cores = Doce
    val hoje = LocalDate.now()
    val porDia = remember(dias) {
        dias.mapNotNull { d ->
            runCatching { LocalDate.parse(d.day) }.getOrNull()?.let { it to d.glasses }
        }.toMap()
    }
    var escolhido by remember { mutableStateOf<LocalDate?>(null) }

    val azul = cores.azulSuave
    val escala = remember(cores) {
        listOf(
            misturar(azul, cores.fundoCampo, 0.10f),
            misturar(azul, cores.superficie, 0.35f),
            misturar(azul, cores.superficie, 0.65f),
            azul,
            if (cores.escura) misturar(azul, Color.White, 0.70f)
            else misturar(azul, Color(0xFF2A6F9C), 0.50f),
        )
    }

    CartaoDaTela(modifier = modifier, titulo = "Seu histórico") {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val livre = maxWidth - CALHA
            val cabem = ((livre + VAO) / (CELULA + VAO)).toInt().coerceAtLeast(4)

            val estaSemana = hoje.with(DayOfWeek.MONDAY)
            val primeiraSemana = (porDia.keys.minOrNull() ?: hoje).with(DayOfWeek.MONDAY)
            val comRegistro = ChronoUnit.WEEKS.between(primeiraSemana, estaSemana).toInt() + 1
            // Pelo menos o que cabe, para a grade nao ficar num canto; no
            // maximo um ano, para nao rolar ate 2019. O `minOf` e porque num
            // tablet cabem MAIS de 53 colunas, e um `coerceIn` com o minimo
            // acima do maximo derruba o app -- foi assim que a tela caiu na
            // primeira abertura em tela larga.
            val semanas = comRegistro.coerceIn(minOf(cabem, MAXIMO_DE_SEMANAS), MAXIMO_DE_SEMANAS)
            // Sobrando largura (tablet), os quadradinhos crescem para ocupa-la.
            val celula = if (semanas < cabem) {
                ((livre + VAO) / semanas - VAO).coerceAtMost(CELULA_MAXIMA)
            } else {
                CELULA
            }
            val passo = celula + VAO
            val inicioDaGrade = estaSemana.minusWeeks((semanas - 1).toLong())

            val medidor = rememberTextMeasurer()
            val estiloDoMes = TipografiaBeazeth.bodyMedium.copy(
                fontSize = 10.sp, color = cores.tintaSuave,
            )
            val rolagem = rememberScrollState()

            Row(modifier = Modifier.fillMaxWidth()) {
                // Os dias da semana ficam FORA da rolagem, para nao sumirem
                // quando a grade anda. Seg, qua e sex, como no GitHub: sete
                // rotulos em 16 dp de altura cada nao cabem legiveis.
                Column(modifier = Modifier.width(CALHA)) {
                    Spacer(modifier = Modifier.height(CABECALHO))
                    for (linha in 0 until 7) {
                        Box(
                            modifier = Modifier.height(passo),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (linha % 2 == 0 && linha < 6) {
                                Text(
                                    text = DIAS_LONGOS[linha].take(3).lowercase(Locale.ROOT),
                                    style = estiloDoMes,
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        // `reverseScrolling`: a posicao zero e a DIREITA, entao
                        // a grade abre mostrando esta semana, sem precisar
                        // rolar ate o fim depois de medir.
                        .horizontalScroll(rolagem, reverseScrolling = true),
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(
                                width = passo * semanas - VAO,
                                height = CABECALHO + passo * 7 - VAO,
                            )
                            .pointerInput(semanas, inicioDaGrade, passo) {
                                detectTapGestures { toque ->
                                    val coluna = (toque.x / passo.toPx()).toInt()
                                    val linha = ((toque.y - CABECALHO.toPx()) / passo.toPx()).toInt()
                                    if (coluna in 0 until semanas && linha in 0..6) {
                                        val dia = inicioDaGrade
                                            .plusWeeks(coluna.toLong())
                                            .plusDays(linha.toLong())
                                        if (!dia.isAfter(hoje)) {
                                            escolhido = if (escolhido == dia) null else dia
                                        }
                                    }
                                }
                            },
                    ) {
                        val lado = celula.toPx()
                        val p = passo.toPx()
                        val topo = CABECALHO.toPx()
                        val canto = CornerRadius(lado * 0.25f)
                        val aro = Stroke(width = 1.5.dp.toPx())

                        var mesRotulado = -1
                        var colunaRotulada = -MINIMO_ENTRE_ROTULOS

                        for (coluna in 0 until semanas) {
                            val segunda = inicioDaGrade.plusWeeks(coluna.toLong())

                            // O nome do mes na primeira semana que comeca
                            // dentro dele -- e so se o anterior ficou a pelo
                            // menos tres colunas, senao "ago" e "set" se
                            // escrevem um por cima do outro.
                            val mesNovo = segunda.monthValue != mesRotulado &&
                                (coluna == 0 || segunda.dayOfMonth <= 7)
                            if (mesNovo && coluna - colunaRotulada >= MINIMO_ENTRE_ROTULOS) {
                                drawText(
                                    textMeasurer = medidor,
                                    text = MESES_CURTOS[segunda.monthValue - 1],
                                    topLeft = Offset(coluna * p, 0f),
                                    style = estiloDoMes,
                                )
                                mesRotulado = segunda.monthValue
                                colunaRotulada = coluna
                            }

                            for (linha in 0 until 7) {
                                val dia = segunda.plusDays(linha.toLong())
                                // O resto desta semana ainda nao aconteceu.
                                if (dia.isAfter(hoje)) continue

                                val origem = Offset(coluna * p, topo + linha * p)
                                val tamanho = Size(lado, lado)
                                drawRoundRect(
                                    color = escala[nivel(porDia[dia] ?: 0, meta)],
                                    topLeft = origem,
                                    size = tamanho,
                                    cornerRadius = canto,
                                )
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
                    }
                }
            }
        }

        // ------------------------------------------------------- a leitura
        //
        // O numero do dia tocado -- ou de hoje, enquanto ninguem tocou. E o
        // que faz a cor nao ser a unica portadora da informacao: quem nao
        // distingue os tons le aqui.
        val alvo = escolhido ?: hoje
        val coposDoAlvo = porDia[alvo] ?: 0
        Text(
            text = buildString {
                append(rotuloDoDia(alvo, hoje))
                append(" · ")
                append(coposPorExtenso(coposDoAlvo))
                if (coposDoAlvo > 0) {
                    append(" · ")
                    append(litros(coposDoAlvo * ml))
                }
            },
            style = TipografiaBeazeth.titleMedium,
            color = cores.tinta,
        )

        Text(
            text = if (escolhido == null) "Toque num dia para ver." else "Toque de novo para voltar a hoje.",
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
            color = cores.tintaSuave,
        )

        // A legenda numa linha propria, encostada a direita: dividindo a linha
        // com a dica, num celular as duas se escreviam uma por cima da outra.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Espaco.e1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "menos",
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 10.sp),
                    color = cores.tintaSuave,
                )
                for (cor in escala) {
                    Box(
                        modifier = Modifier
                            .size(CELULA)
                            .clip(RoundedCornerShape(CELULA * 0.25f))
                            .background(cor),
                    )
                }
                Text(
                    text = "meta",
                    style = TipografiaBeazeth.bodyMedium.copy(fontSize = 10.sp),
                    color = cores.tintaSuave,
                )
            }
        }
    }
}

/**
 * De 0 (nada) a 4 (meta batida), pela FRACAO da meta e nao pelos copos.
 *
 * Os tres degraus do meio dividem o caminho ate a meta em tercos: com meta
 * oito, 1-2 copos e o primeiro, 3-5 o segundo, 6-7 o terceiro. Sem meta
 * configurada, qualquer copo conta como cheio -- nao ha regua para medir.
 */
private fun nivel(copos: Int, meta: Int): Int = when {
    copos <= 0 -> 0
    meta <= 0 || copos >= meta -> 4
    else -> 1 + (copos * 3 / meta).coerceIn(0, 2)
}

/** "Hoje", "Ontem", ou "Ter, 16/09". */
private fun rotuloDoDia(dia: LocalDate, hoje: LocalDate): String = when (dia) {
    hoje -> "Hoje"
    hoje.minusDays(1) -> "Ontem"
    else -> "%s, %02d/%02d".format(
        DIAS_LONGOS[dia.dayOfWeek.value - 1].take(3), dia.dayOfMonth, dia.monthValue,
    )
}

private fun coposPorExtenso(copos: Int): String = when (copos) {
    0 -> "nenhum copo"
    1 -> "1 copo"
    else -> "$copos copos"
}

/**
 * "0,25 L", "1,5 L", "2 L": ate dois decimais, sem zeros a direita.
 *
 * `Locale` explicito porque o app e em portugues em qualquer aparelho: sem ele
 * o separador sai do idioma do SISTEMA, e um tablet em ingles mostraria
 * "1.5 L" no meio de uma frase em portugues.
 */
private fun litros(ml: Int): String =
    "%.2f".format(BRASIL, ml / 1000f).trimEnd('0').trimEnd(',') + " L"

private val BRASIL = Locale("pt", "BR")

/** Um ano de semanas, como no GitHub. */
private const val MAXIMO_DE_SEMANAS = 53

/** Colunas entre dois nomes de mes, para um nao escrever em cima do outro. */
private const val MINIMO_ENTRE_ROTULOS = 3

private val CELULA = 13.dp
private val CELULA_MAXIMA = 20.dp
private val VAO = 3.dp
private val CABECALHO = 16.dp

/** A coluna dos dias da semana, a esquerda da grade. */
private val CALHA = 30.dp
