package com.beazeth.notifier.ui.telas.postits

import androidx.compose.ui.graphics.Color

/**
 * Os valores que o quadro de post-its espelha do site.
 *
 * Arquivo separado pelo mesmo motivo de `js/pages/notes/constants.js` existir
 * la: sao os numeros e nomes que TEM de bater com o servidor, e ficam onde da
 * para conferir os dois lados sem rolar por trezentas linhas de interface.
 */

/** Espelha `NOTE_BUCKETS` e os emojis de `js/pages/notes/constants.js`. */
internal val QUADROS = listOf(
    Triple("hoje", "Hoje", "☀️"),
    Triple("amanha", "Amanhã", "🌙"),
    Triple("semana", "Semana", "🗓️"),
    Triple("ideias", "Ideias", "💡"),
)

/**
 * As seis cores do site, por NOME.
 *
 * Nome e nao hexadecimal: `_normalize_note_color` no servidor so aceita estes
 * seis e cai em "sun" para qualquer outra coisa -- em silencio. A primeira
 * versao mandava hex, e todo post-it criado pelo app virava amarelo sem que
 * nada acusasse o erro.
 */
internal val CORES = listOf(
    "sun" to Color(0xFFFFE066),
    "rose" to Color(0xFFFF9EC4),
    "mint" to Color(0xFF7FE0C0),
    "blue" to Color(0xFF8FCDFF),
    "peach" to Color(0xFFFFB08A),
    "lavender" to Color(0xFFC0A8FB),
)

/** A tinta dos post-its. Fixa, e nao a do tema: os seis tons de papel sao
 *  todos claros, e a tinta da paleta sumiria em alguns deles. */
internal val TINTA_DO_PAPEL = Color(0xFF3A2A12)

/**
 * Quanto tempo parado, escrevendo, antes de o texto descer.
 *
 * [PAUSA_DO_RASCUNHO] e curta porque o unico custo e uma linha no banco do
 * aparelho, e o que ela evita e perder o que foi digitado quando o cartao sai
 * da composicao -- o que acontece toda vez que um post-it novo troca o id
 * provisorio pelo do servidor, mais ou menos um segundo depois de nascer.
 *
 * [PAUSA_DO_ENVIO] e longa porque o custo e um PATCH: com uma pausa curta, uma
 * frase digitada devagar viraria meia duzia de envios que so se sobrescrevem.
 */
internal const val PAUSA_DO_RASCUNHO = 150L
internal const val PAUSA_DO_ENVIO = 1_500L

internal fun tomDe(nome: String): Color =
    CORES.firstOrNull { it.first == nome }?.second ?: CORES.first().second

/** A inclinacao leve e estavel de cada post-it: derivada do id, entao o cartao
 *  nao pula de angulo a cada redesenho. Mesma conta do `tiltFor` do site. */
internal fun inclinacaoDe(id: Long): Float = (((id * 37) % 9) - 4).toFloat()

/** O `color-mix(in srgb, tint X%, #fff)` do CSS. */
internal fun misturarComBranco(cor: Color, fracaoDaCor: Float): Color = Color(
    red = cor.red * fracaoDaCor + (1 - fracaoDaCor),
    green = cor.green * fracaoDaCor + (1 - fracaoDaCor),
    blue = cor.blue * fracaoDaCor + (1 - fracaoDaCor),
    alpha = 1f,
)
