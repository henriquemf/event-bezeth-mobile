package com.beazeth.notifier.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.beazeth.notifier.R

/**
 * As dez duplas de fonte da tela de Aparencia, empacotadas no `.apk`.
 *
 * Espelham os blocos `[data-font="..."]` de `themes.css`, um por um. Cada dupla
 * e uma fonte de corpo (`--font-main`) e uma de titulo (`--font-display`).
 *
 * **Empacotadas, e nao baixadas.** O site carrega as dezoito familias do Google
 * Fonts; o app nao pode, porque e offline-first -- trocar de tema no aviao
 * mostraria a fonte errada. Sao 1,8 MB pagos uma vez, ja recortados para o
 * alfabeto latino (a Baloo 2 completa sozinha pesava 412 KB, com devanagari
 * que este app nunca vai escrever).
 *
 * **Um arquivo por peso.** As variaveis foram instanciadas com
 * `fonttools varLib.instancer` antes de entrar aqui: o Android ignora o eixo de
 * peso em silencio e renderiza a instancia padrao, e foi assim que os rotulos
 * em negrito sairam mais leves que o texto normal na primeira tentativa.
 *
 * Sete familias tem um peso so -- sao as manuscritas (Pacifico, Great Vibes,
 * Satisfy, Caveat Brush, Cherry Bomb One, Berkshire Swash) e a Varela Round.
 * Nelas o negrito aponta para o mesmo arquivo: o navegador engorda o desenho
 * para fingir, e numa letra cursiva isso borra os tracos finos.
 */
data class DuplaDeFonte(
    val chave: String,
    val rotulo: String,
    val corpo: FontFamily,
    val titulo: FontFamily,
)

private fun familia(regular: Int, bold: Int = regular) = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(bold, FontWeight.Bold),
)

val FONTES: List<DuplaDeFonte> = listOf(
    DuplaDeFonte(
        chave = "sugar",
        rotulo = "Sugar",
        corpo = familia(R.font.quicksand_regular, R.font.quicksand_bold),
        titulo = familia(R.font.baloo2_bold),
    ),
    DuplaDeFonte(
        chave = "bubble",
        rotulo = "Bubble",
        corpo = familia(R.font.nunito_regular, R.font.nunito_bold),
        titulo = familia(R.font.fredoka_bold),
    ),
    DuplaDeFonte(
        chave = "love",
        rotulo = "Love Note",
        corpo = familia(R.font.varelaround_regular),
        titulo = familia(R.font.cherrybombone_regular),
    ),
    DuplaDeFonte(
        chave = "daisy",
        rotulo = "Daisy",
        corpo = familia(R.font.poppins_regular, R.font.poppins_bold),
        titulo = familia(R.font.pacifico_regular),
    ),
    DuplaDeFonte(
        chave = "glam",
        rotulo = "Glam",
        corpo = familia(R.font.montserrat_regular, R.font.montserrat_bold),
        titulo = familia(R.font.lobstertwo_bold),
    ),
    DuplaDeFonte(
        chave = "cotton",
        rotulo = "Cotton",
        corpo = familia(R.font.raleway_regular, R.font.raleway_bold),
        titulo = familia(R.font.comfortaa_bold),
    ),
    DuplaDeFonte(
        chave = "diary",
        rotulo = "Diary",
        corpo = familia(R.font.rubik_regular, R.font.rubik_bold),
        titulo = familia(R.font.caveatbrush_regular),
    ),
    DuplaDeFonte(
        chave = "pearl",
        rotulo = "Pearl",
        corpo = familia(R.font.manrope_regular, R.font.manrope_bold),
        titulo = familia(R.font.satisfy_regular),
    ),
    DuplaDeFonte(
        chave = "chic",
        rotulo = "Chic",
        corpo = familia(R.font.urbanist_regular, R.font.urbanist_bold),
        titulo = familia(R.font.greatvibes_regular),
    ),
    DuplaDeFonte(
        chave = "dream",
        rotulo = "Dream",
        corpo = familia(R.font.lexend_regular, R.font.lexend_bold),
        titulo = familia(R.font.berkshireswash_regular),
    ),
)

/** A dupla com que o site abre. */
const val FONTE_PADRAO = "sugar"

/** Cai no padrao se a chave nao existir, pelo mesmo motivo de [paletaDe]. */
fun fonteDe(chave: String): DuplaDeFonte =
    FONTES.firstOrNull { it.chave == chave } ?: FONTES.first { it.chave == FONTE_PADRAO }
