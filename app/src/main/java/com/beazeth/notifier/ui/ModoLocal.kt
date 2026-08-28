package com.beazeth.notifier.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * "Este app esta rodando sem conta" -- disponivel em qualquer ponto da arvore.
 *
 * ## Por que ambiente, e nao um parametro
 *
 * Quem precisa saber disto sao duas folhas distantes: o selo "nao enviado" do
 * post-it e o do evento. Levar um `Boolean` ate la significaria acrescentar o
 * mesmo parametro a `PostitsScreen`, a `Postit`, a `CalendarioScreen` e a
 * `LinhaDeEvento` -- quatro assinaturas mudadas para uma informacao que nenhuma
 * das quatro usa para decidir nada alem de mostrar ou nao uma palavra.
 *
 * E o caso classico de propriedade de ambiente: vale para a tela inteira, muda
 * uma vez por sessao e nao pertence a nenhuma tela em particular. `static`
 * porque troca praticamente nunca -- quando troca, recompor tudo e justamente o
 * que se quer.
 *
 * ## O que ele muda
 *
 * O selo "nao enviado" quer dizer "isto ainda nao chegou ao servidor". Sem
 * conta nao ha servidor, entao a frase deixa de ter sentido: mostraria um aviso
 * permanente de pendencia para quem nunca pediu para enviar coisa alguma. O id
 * negativo continua la, mas passa a significar so "criado aqui".
 */
val LocalModoLocal = staticCompositionLocalOf { false }
