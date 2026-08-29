package com.beazeth.notifier.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * O `color-mix(in srgb, a X%, b)` do CSS: [fracaoDeA] de `a`, o resto de `b`.
 *
 * Mora no tema porque e a lingua em que as cores do site foram escritas -- os
 * widgets desenhados (a ampulheta, o copo) sao portes de CSS que mistura cor em
 * quase toda regra, e a conta precisa ser a mesma nos dois.
 *
 * **Nao serve para misturar com transparente.** `color-mix(..., transparent)`
 * no CSS resulta na mesma cor com menos alfa; aqui a interpolacao componente a
 * componente puxaria o RGB para o preto junto. Para aquele caso, use
 * `.copy(alpha = ...)`.
 */
internal fun misturar(a: Color, b: Color, fracaoDeA: Float): Color = Color(
    red = a.red * fracaoDeA + b.red * (1 - fracaoDeA),
    green = a.green * fracaoDeA + b.green * (1 - fracaoDeA),
    blue = a.blue * fracaoDeA + b.blue * (1 - fracaoDeA),
    alpha = a.alpha * fracaoDeA + b.alpha * (1 - fracaoDeA),
)
