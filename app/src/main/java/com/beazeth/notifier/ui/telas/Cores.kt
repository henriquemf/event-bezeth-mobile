package com.beazeth.notifier.ui.telas

import androidx.compose.ui.graphics.Color

/**
 * Converte `#rrggbb` do servidor para a cor do Compose.
 *
 * Devolve o rosa padrao se vier algo estranho: um hex invalido numa tag nao
 * pode derrubar a tela inteira do calendario.
 *
 * Mora aqui, e nao dentro de uma tela, porque tres delas leem cor em texto --
 * o calendario le a da tag, o planner le a do bloco, e o quadro le a do
 * post-it (esse por nome, com queda para ca).
 */
internal fun corDoHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrElse { Color(0xFFFF78B2) }
