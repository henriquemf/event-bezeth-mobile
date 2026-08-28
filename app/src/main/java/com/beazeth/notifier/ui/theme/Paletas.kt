package com.beazeth.notifier.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * As dez paletas do site, claras e escuras.
 *
 * **Geradas a partir de `app/static/css/themes.css`**, e nao digitadas. Sao
 * dezessete cores por tema, vinte combinacoes: copiadas na mao, um valor
 * errado nao quebraria nada -- so deixaria um tema levemente diferente do site,
 * para sempre e sem ninguem notar. O gerador esta no scratchpad
 * (`gerar_paletas.py`); rode-o de novo se o CSS mudar.
 *
 * Duas contas o gerador ja fez, e sao as que a primeira versao errou:
 *
 * - **`superficie` e opaca.** O CSS escreve `--surface` como branco a oitenta
 *   por cento, mas o cartao usa `--surface-solid`, que compoe esse branco sobre
 *   `--bg-mid` ANTES de pintar. Translucido aqui deixaria o degrade vazar pelo
 *   cartao.
 * - **`fundoCampo` compoe sobre a superficie**, nao sobre o fundo da tela: o
 *   campo fica dentro do cartao.
 *
 * O que o gerador NAO cobre e o que o CSS resolve com `color-mix` na hora
 * (estados de foco, hover, faixas de destaque). Esses continuam calculados no
 * Compose, com `copy(alpha = ...)`, que e o equivalente direto.
 */
data class Paleta(
    val chave: String,
    val rotulo: String,
    val escura: Boolean,
    val fundoTopo: Color,
    val fundoMeio: Color,
    val fundoBase: Color,
    val tinta: Color,
    val tintaSuave: Color,
    val superficie: Color,
    val fundoCampo: Color,
    val traco: Color,
    val sombra: Color,
    val destaque: Color,
    val destaqueEscuro: Color,
    val azulSuave: Color,
    val perigo: Color,
    val erroFundo: Color,
    val erroBorda: Color,
) {
    /**
     * O fim do degrade do botao primario.
     *
     * **Aqui o app se afasta do site de proposito.** O CSS escreve
     * `linear-gradient(110deg, var(--accent), #ff96c2)` -- com o rosa CRAVADO,
     * igual nos dez temas. No site isso passa; no app, onde trocar de tema e
     * recurso de vitrine, um botao azul terminando em rosa leria como defeito.
     *
     * O valor cravado e exatamente o destaque clareado em 78% (confira:
     * #ff78b2 misturado com branco a 78% da #ff96c2), entao calcular preserva
     * o tema padrao byte a byte e faz nos outros o que o autor claramente quis.
     */
    val destaqueClaro: Color
        get() = Color(
            red = destaque.red * 0.78f + 0.22f,
            green = destaque.green * 0.78f + 0.22f,
            blue = destaque.blue * 0.78f + 0.22f,
            alpha = 1f,
        )

    /**
     * As tres bolhas de luz do fundo (`.bg-candy`).
     *
     * Nao mudam por tema -- no CSS elas sao `rgba` literais, iguais nos dez --
     * mas mudam entre claro e escuro. Ficam aqui porque quem as desenha precisa
     * saber se o tema e escuro.
     */
    val bolhas: List<Color>
        get() = if (escura) {
            listOf(Color(0x47CA549A), Color(0x3D688CE7), Color(0x33FF91B2))
        } else {
            listOf(Color(0x3DFF78B2), Color(0x387EC8FF), Color(0x45FFC3D6))
        }

    /** Onde cada bolha fica, e ate onde ela se dissolve. Ver `.bg-candy`. */
    val posicoesDasBolhas: List<Triple<Float, Float, Float>>
        get() = if (escura) {
            listOf(
                Triple(0.12f, 0.18f, 0.33f),
                Triple(0.84f, 0.18f, 0.35f),
                Triple(0.20f, 0.82f, 0.34f),
            )
        } else {
            listOf(
                Triple(0.08f, 0.12f, 0.30f),
                Triple(0.86f, 0.14f, 0.34f),
                Triple(0.24f, 0.84f, 0.36f),
            )
        }
}

/** As dez paletas no modo claro, na ordem em que o site as lista. */
val PALETAS_CLARAS: List<Paleta> = listOf(
Paleta(
        chave = "rose",
        rotulo = "Rose Dream",
        escura = false,
        fundoTopo = Color(0xFFFFE5F3),
        fundoMeio = Color(0xFFFFEEF8),
        fundoBase = Color(0xFFFFF6FF),
        tinta = Color(0xFF3F1D43),
        tintaSuave = Color(0xFF7A5A81),
        superficie = Color(0xFFFFFCFE),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x40AD71A7),
        sombra = Color(0x33BD80B5),
        destaque = Color(0xFFFF78B2),
        destaqueEscuro = Color(0xFFE45A98),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFB23A7D),
        erroFundo = Color(0x21C94383),
        erroBorda = Color(0x59C94383),
    ),
    Paleta(
        chave = "berry",
        rotulo = "Berry Pop",
        escura = false,
        fundoTopo = Color(0xFFFFD6EA),
        fundoMeio = Color(0xFFFBE4FF),
        fundoBase = Color(0xFFEEF2FF),
        tinta = Color(0xFF341944),
        tintaSuave = Color(0xFF674F78),
        superficie = Color(0xFFFEFAFF),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x408B64B2),
        sombra = Color(0x388263AD),
        destaque = Color(0xFFB56DFF),
        destaqueEscuro = Color(0xFF9D55E8),
        azulSuave = Color(0xFF9AC1FF),
        perigo = Color(0xFF9B3AA7),
        erroFundo = Color(0x21BA53A0),
        erroBorda = Color(0x52BA53A0),
    ),
    Paleta(
        chave = "peach",
        rotulo = "Peach Glow",
        escura = false,
        fundoTopo = Color(0xFFFFE9DF),
        fundoMeio = Color(0xFFFFF0EA),
        fundoBase = Color(0xFFFFF7EF),
        tinta = Color(0xFF422026),
        tintaSuave = Color(0xFF7D5B63),
        superficie = Color(0xFFFFFCFB),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x3DC98194),
        sombra = Color(0x38BF888B),
        destaque = Color(0xFFFF8D91),
        destaqueEscuro = Color(0xFFEA697E),
        azulSuave = Color(0xFF89C9D8),
        perigo = Color(0xFFB54A65),
        erroFundo = Color(0x1FC9617E),
        erroBorda = Color(0x52C9617E),
    ),
    Paleta(
        chave = "lavender",
        rotulo = "Lavender Kiss",
        escura = false,
        fundoTopo = Color(0xFFF1E4FF),
        fundoMeio = Color(0xFFF6EBFF),
        fundoBase = Color(0xFFFFF5FF),
        tinta = Color(0xFF352245),
        tintaSuave = Color(0xFF6D5981),
        superficie = Color(0xFFFDFBFF),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x40A788CD),
        sombra = Color(0x368D72BE),
        destaque = Color(0xFFB084FF),
        destaqueEscuro = Color(0xFF9667EF),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFF9655BF),
        erroFundo = Color(0x21C94383),
        erroBorda = Color(0x59C94383),
    ),
    Paleta(
        chave = "mint",
        rotulo = "Mint Candy",
        escura = false,
        fundoTopo = Color(0xFFE3FFF7),
        fundoMeio = Color(0xFFE9FFF5),
        fundoBase = Color(0xFFF7FFF9),
        tinta = Color(0xFF1F3A34),
        tintaSuave = Color(0xFF567A72),
        superficie = Color(0xFFFBFFFD),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x3D64B197),
        sombra = Color(0x30549988),
        destaque = Color(0xFF53C7A2),
        destaqueEscuro = Color(0xFF3DB48F),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFA85D88),
        erroFundo = Color(0x21C94383),
        erroBorda = Color(0x59C94383),
    ),
    Paleta(
        chave = "sunset",
        rotulo = "Sunset Blush",
        escura = false,
        fundoTopo = Color(0xFFFFE2DC),
        fundoMeio = Color(0xFFFFECE3),
        fundoBase = Color(0xFFFFF5EC),
        tinta = Color(0xFF46222A),
        tintaSuave = Color(0xFF815766),
        superficie = Color(0xFFFFFCFB),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x3DDC8487),
        sombra = Color(0x33D3826F),
        destaque = Color(0xFFFF8577),
        destaqueEscuro = Color(0xFFF06A5B),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFBB4F71),
        erroFundo = Color(0x21C94383),
        erroBorda = Color(0x59C94383),
    ),
    Paleta(
        chave = "ocean",
        rotulo = "Ocean Doll",
        escura = false,
        fundoTopo = Color(0xFFDFF7FF),
        fundoMeio = Color(0xFFE8F7FF),
        fundoBase = Color(0xFFF4FBFF),
        tinta = Color(0xFF173348),
        tintaSuave = Color(0xFF4A6C84),
        superficie = Color(0xFFFBFEFF),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x3D73AED6),
        sombra = Color(0x366CA6CB),
        destaque = Color(0xFF4CA9DF),
        destaqueEscuro = Color(0xFF368EC2),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFF84549F),
        erroFundo = Color(0x21C94383),
        erroBorda = Color(0x59C94383),
    ),
    Paleta(
        chave = "plum",
        rotulo = "Plum Glow",
        escura = false,
        fundoTopo = Color(0xFFF6DDF3),
        fundoMeio = Color(0xFFFCE8FA),
        fundoBase = Color(0xFFFFF2FF),
        tinta = Color(0xFF3D1F3F),
        tintaSuave = Color(0xFF744F77),
        superficie = Color(0xFFFEFBFE),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x40B175AD),
        sombra = Color(0x36A0689D),
        destaque = Color(0xFFC768C1),
        destaqueEscuro = Color(0xFFB653AD),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFF9F3B7F),
        erroFundo = Color(0x21C94383),
        erroBorda = Color(0x59C94383),
    ),
    Paleta(
        chave = "cocoa",
        rotulo = "Cocoa Berry",
        escura = false,
        fundoTopo = Color(0xFFFFE9E2),
        fundoMeio = Color(0xFFFFF1EA),
        fundoBase = Color(0xFFFFF8F4),
        tinta = Color(0xFF3C2A29),
        tintaSuave = Color(0xFF6F5753),
        superficie = Color(0xFFFFFDFC),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x3DBD867B),
        sombra = Color(0x33B68071),
        destaque = Color(0xFFD98E79),
        destaqueEscuro = Color(0xFFC77A68),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFA65E74),
        erroFundo = Color(0x21C94383),
        erroBorda = Color(0x59C94383),
    ),
    Paleta(
        chave = "strawberry",
        rotulo = "Strawberry Milk",
        escura = false,
        fundoTopo = Color(0xFFFFE4EF),
        fundoMeio = Color(0xFFFFEEF4),
        fundoBase = Color(0xFFFFF6FA),
        tinta = Color(0xFF441E35),
        tintaSuave = Color(0xFF81586E),
        superficie = Color(0xFFFFFCFD),
        fundoCampo = Color(0xFFFFFFFF),
        traco = Color(0x40DD8DAC),
        sombra = Color(0x33CA7E9E),
        destaque = Color(0xFFF06AA0),
        destaqueEscuro = Color(0xFFDE4B8B),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFB74472),
        erroFundo = Color(0x21C94383),
        erroBorda = Color(0x59C94383),
    ),
)

/** As mesmas dez com o bloco `[data-dark="true"]` aplicado por cima. */
val PALETAS_ESCURAS: List<Paleta> = listOf(
Paleta(
        chave = "rose",
        rotulo = "Rose Dream",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFFFF78B2),
        destaqueEscuro = Color(0xFFE45A98),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFB23A7D),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "berry",
        rotulo = "Berry Pop",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFFB56DFF),
        destaqueEscuro = Color(0xFF9D55E8),
        azulSuave = Color(0xFF9AC1FF),
        perigo = Color(0xFF9B3AA7),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "peach",
        rotulo = "Peach Glow",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFFFF8D91),
        destaqueEscuro = Color(0xFFEA697E),
        azulSuave = Color(0xFF89C9D8),
        perigo = Color(0xFFB54A65),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "lavender",
        rotulo = "Lavender Kiss",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFFB084FF),
        destaqueEscuro = Color(0xFF9667EF),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFF9655BF),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "mint",
        rotulo = "Mint Candy",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFF53C7A2),
        destaqueEscuro = Color(0xFF3DB48F),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFA85D88),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "sunset",
        rotulo = "Sunset Blush",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFFFF8577),
        destaqueEscuro = Color(0xFFF06A5B),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFBB4F71),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "ocean",
        rotulo = "Ocean Doll",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFF4CA9DF),
        destaqueEscuro = Color(0xFF368EC2),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFF84549F),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "plum",
        rotulo = "Plum Glow",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFFC768C1),
        destaqueEscuro = Color(0xFFB653AD),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFF9F3B7F),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "cocoa",
        rotulo = "Cocoa Berry",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFFD98E79),
        destaqueEscuro = Color(0xFFC77A68),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFA65E74),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
    Paleta(
        chave = "strawberry",
        rotulo = "Strawberry Milk",
        escura = true,
        fundoTopo = Color(0xFF1A1320),
        fundoMeio = Color(0xFF21192A),
        fundoBase = Color(0xFF18111F),
        tinta = Color(0xFFF8DEFF),
        tintaSuave = Color(0xFFD6B6E2),
        superficie = Color(0xFF22142D),
        fundoCampo = Color(0xFF43264C),
        traco = Color(0x38FFB3E6),
        sombra = Color(0x73120618),
        destaque = Color(0xFFF06AA0),
        destaqueEscuro = Color(0xFFDE4B8B),
        azulSuave = Color(0xFF7EC8FF),
        perigo = Color(0xFFB74472),
        erroFundo = Color(0x3DA6326E),
        erroBorda = Color(0x6BE683B4),
    ),
)

/** O tema com que o site abre. */
const val PALETA_PADRAO = "rose"

/**
 * A paleta de uma escolha.
 *
 * Cai no padrao se a chave nao existir -- um tema removido do site nao pode
 * deixar o app sem cor nenhuma no aparelho de quem o tinha escolhido.
 */
fun paletaDe(chave: String, escuro: Boolean): Paleta {
    val lista = if (escuro) PALETAS_ESCURAS else PALETAS_CLARAS
    return lista.firstOrNull { it.chave == chave }
        ?: lista.first { it.chave == PALETA_PADRAO }
}
