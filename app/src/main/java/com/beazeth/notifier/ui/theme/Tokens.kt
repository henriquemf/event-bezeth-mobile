package com.beazeth.notifier.ui.theme

import androidx.compose.ui.unit.dp

/**
 * A escala de espacamento do site (`--space-1` a `--space-5`).
 *
 * Constante, e nao parte da [Paleta], pelo mesmo motivo que no CSS ela mora em
 * `base.css` e nao em `themes.css`: espacamento nao muda por tema, e repeti-lo
 * em dez blocos seriam dez chances de as telas sairem de sincronia.
 */
object Espaco {
    val e1 = 6.dp
    val e2 = 10.dp
    val e3 = 14.dp
    val e4 = 18.dp
    val e5 = 24.dp
}

/**
 * Os raios de canto.
 *
 * **Mais retos que os do site, de proposito.** O CSS usa 24 px nos cartoes e
 * 999 px nos botoes; num celular, com a tela toda ocupada por cartoes
 * empilhados, essa curva toda vira uma pilha de balas. Aqui os cartoes sao
 * quase retos e a curva fica reservada ao que se toca.
 *
 * O cartao de entrar/criar conta e a excecao e continua com os 26 px do site:
 * ele aparece uma vez, centrado, e e o primeiro contato com o app.
 */
object Canto {
    /** Cartoes de tela. */
    val cartao = 6.dp

    /** Caixas dentro de um cartao: campos, itens de lista, faixas. */
    val caixa = 4.dp

    /** Marcadores pequenos, como a caixinha de concluir. */
    val marca = 3.dp

    /** O que se toca e precisa parecer tocavel: botoes e abas. */
    val botao = 10.dp
}
