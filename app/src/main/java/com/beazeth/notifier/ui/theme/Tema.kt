package com.beazeth.notifier.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * O tema do site, com as dez paletas, as dez fontes e o modo escuro.
 *
 * **Como as telas nao precisaram mudar.** [Doce] e [TipografiaBeazeth] parecem
 * constantes -- `Doce.destaque`, como antes -- mas sao propriedades com getter
 * `@Composable` que leem um `CompositionLocal`. Trocar de tema reexecuta quem
 * as leu, e so isso. A alternativa era passar a paleta como parametro por toda
 * a arvore, ou ler `LocalPaleta.current` em cada tela: dezenas de pontos onde
 * alguem esqueceria um.
 *
 * O preco: dentro de um `drawBehind` nao ha contexto de composicao, entao la e
 * preciso capturar `val cores = Doce` antes do modificador. Onde isso acontece,
 * o comentario diz por que.
 */

/** A paleta em vigor. `staticCompositionLocalOf` porque trocar de tema deve
 *  repintar tudo, e nao so quem leu a cor especifica. */
val LocalPaleta = staticCompositionLocalOf { paletaDe(PALETA_PADRAO, escuro = false) }

/** A tipografia em vigor, montada a partir da dupla de fonte escolhida. */
val LocalTipografia = staticCompositionLocalOf { tipografiaDe(fonteDe(FONTE_PADRAO)) }

/** As cores do tema atual. Use dentro de `@Composable`. */
val Doce: Paleta
    @Composable
    @ReadOnlyComposable
    get() = LocalPaleta.current

/** Os estilos de texto do tema atual. Use dentro de `@Composable`. */
val TipografiaBeazeth: Typography
    @Composable
    @ReadOnlyComposable
    get() = LocalTipografia.current

/**
 * Os tamanhos vem do CSS convertidos a 1rem = 16sp.
 *
 * `sp` e nao `dp`: quem aumentou a fonte do sistema fez isso para conseguir
 * ler, e um app que ignora essa escolha e um app que essa pessoa nao usa.
 */
fun tipografiaDe(dupla: DuplaDeFonte): Typography = Typography(
    // .auth-card h2 -- 1.55rem, fonte de display
    headlineMedium = TextStyle(
        fontFamily = dupla.titulo,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 32.sp,
    ),
    // .auth-brand h1 -- 1.3rem. line-height 1.2 e nao 1: em 1 o topo das letras
    // das fontes de script (Great Vibes, Berkshire Swash) era cortado.
    titleLarge = TextStyle(
        fontFamily = dupla.titulo,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
    ),
    // corpo: subtitulos e texto dentro dos campos
    bodyLarge = TextStyle(
        fontFamily = dupla.corpo,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    // .auth-brand small e .auth-switch -- 0.92rem
    bodyMedium = TextStyle(
        fontFamily = dupla.corpo,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    // .event-form label -- 0.92rem, peso 700
    labelLarge = TextStyle(
        fontFamily = dupla.corpo,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    // texto do botao -- .btn-primary usa a fonte de display
    titleMedium = TextStyle(
        fontFamily = dupla.titulo,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
)

@Composable
fun TemaBeazeth(
    tema: String = PALETA_PADRAO,
    fonte: String = FONTE_PADRAO,
    escuro: Boolean = false,
    conteudo: @Composable () -> Unit,
) {
    val paleta = remember(tema, escuro) { paletaDe(tema, escuro) }
    val tipografia = remember(fonte) { tipografiaDe(fonteDe(fonte)) }
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val janela = (view.context as Activity).window
            // Desenha atras das barras do sistema: e o que faz o app ocupar a
            // tela inteira em vez de ficar numa moldura, como o site fazia.
            WindowCompat.setDecorFitsSystemWindows(janela, false)
            // Os icones da barra de status acompanham o tema. Sem isto, icones
            // escuros sobre o fundo escuro simplesmente somem.
            WindowCompat.getInsetsController(janela, view)
                .isAppearanceLightStatusBars = !escuro
        }
    }

    // O Material 3 ainda pinta o que nao passa pelos nossos componentes: o
    // cursor de selecao, o menu de colar, a barra de rolagem. Sem isto, tocar
    // duas vezes num campo abre um menu roxo padrao do Android no meio de uma
    // tela rosa.
    val esquema = if (escuro) {
        darkColorScheme(
            primary = paleta.destaque,
            onPrimary = paleta.tinta,
            secondary = paleta.azulSuave,
            background = paleta.fundoMeio,
            onBackground = paleta.tinta,
            surface = paleta.superficie,
            onSurface = paleta.tinta,
            surfaceVariant = paleta.fundoTopo,
            onSurfaceVariant = paleta.tintaSuave,
            outline = paleta.traco,
            error = paleta.perigo,
            onError = Color.White,
        )
    } else {
        lightColorScheme(
            primary = paleta.destaqueEscuro,
            onPrimary = Color.White,
            secondary = paleta.azulSuave,
            background = paleta.fundoMeio,
            onBackground = paleta.tinta,
            surface = paleta.superficie,
            onSurface = paleta.tinta,
            surfaceVariant = paleta.fundoTopo,
            onSurfaceVariant = paleta.tintaSuave,
            outline = paleta.traco,
            error = paleta.perigo,
            onError = Color.White,
        )
    }

    CompositionLocalProvider(
        LocalPaleta provides paleta,
        LocalTipografia provides tipografia,
    ) {
        MaterialTheme(
            colorScheme = esquema,
            typography = tipografia,
            content = conteudo,
        )
    }
}
