package com.beazeth.notifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import com.beazeth.notifier.ui.componentes.AvisoDeErro
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CampoDoce
import com.beazeth.notifier.ui.componentes.CartaoDoce
import com.beazeth.notifier.ui.componentes.Marca
import com.beazeth.notifier.ui.componentes.RodapeComLink
import com.beazeth.notifier.ui.componentes.TituloDoCartao
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * Entrada da conta -- o mesmo cartão da tela `/entrar` do site.
 *
 * Cada peça aqui tem contraparte no CSS: `.auth-shell` é a coluna centrada,
 * `.auth-card` é o [CartaoDoce], `.auth-brand` é a [Marca]. A primeira versão
 * desta tela usava os componentes prontos do Material 3, e o veredito de quem
 * olhou foi direto: "totalmente diferente do site". Era.
 *
 * O fundo não está aqui: mora na raiz, em `MainActivity`, como o `body` no
 * site. Desenhá-lo também nesta tela dobraria a opacidade das bolhas.
 *
 * `imePadding` junto de `safeDrawingPadding` resolve uma das quatro queixas do
 * app anterior: no site o teclado subia por cima do campo de senha e empurrava
 * o layout. Aqui a coluna encolhe e rola, que é o comportamento de aplicativo.
 */
@Composable
fun LoginScreen(
    estado: EstadoLogin,
    aoEntrar: (String, String) -> Unit,
    aoIrParaCadastro: () -> Unit,
    aoUsarSemConta: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }

    // Aviso de campo vazio, separado do erro que vem do servidor: um e nosso e
    // some assim que a pessoa digita, o outro e resposta da rede e fica ate a
    // proxima tentativa.
    var aviso by remember { mutableStateOf<String?>(null) }

    val focoDaSenha = remember { FocusRequester() }

    // O servidor recusou: o cursor volta para a senha.
    //
    // Nao e so conforto. Enquanto o pedido esta no ar todo o cartao fica
    // desligado, e o foco se perde; sem devolve-lo, o Enter seguinte nao tem
    // onde cair -- e era assim que ele acabava no link de criar conta. Voltar
    // para a senha tambem poe o cursor onde a pessoa precisa digitar: o e-mail
    // quase sempre estava certo.
    LaunchedEffect(estado.carregando, estado.erro) {
        if (!estado.carregando && estado.erro != null) {
            runCatching { focoDaSenha.requestFocus() }
        }
    }

    // Depois de alguns segundos parados, a tela conta o que está acontecendo.
    //
    // O plano gratuito do Render desliga o container por inatividade, e a
    // primeira requisição do dia espera ele subir -- em torno de meio minuto.
    // Não dá para encurtar isso daqui, mas dá para não deixar a pessoa achar
    // que o app travou. Quatro segundos porque abaixo disso a frase apareceria
    // no caminho normal, que é rápido.
    var demorando by remember { mutableStateOf(false) }
    LaunchedEffect(estado.carregando) {
        demorando = false
        if (estado.carregando) {
            delay(4_000)
            demorando = true
        }
    }

    val enviar = {
        if (estado.carregando) {
            // Dois Enter seguidos nao sao dois logins.
        } else if (email.isBlank() || senha.isBlank()) {
            aviso = "Informe e-mail e senha."
        } else {
            aviso = null
            aoEntrar(email.trim(), senha)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(Espaco.e5),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CartaoDoce {
            Marca(nome = "Event Notifier", lema = "Organize seus momentos")
            Spacer(Modifier.height(Espaco.e1))

            TituloDoCartao(
                titulo = "Entrar",
                subtitulo = "Seus eventos, post-its e rotina esperando por você.",
            )

            CampoDoce(
                rotulo = "E-mail",
                valor = email,
                aoMudar = { email = it; aviso = null },
                habilitado = !estado.carregando,
                opcoesDoTeclado = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            CampoDoce(
                rotulo = "Senha",
                valor = senha,
                aoMudar = { senha = it; aviso = null },
                habilitado = !estado.carregando,
                ocultarTexto = true,
                opcoesDoTeclado = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                // Entrar pelo teclado, sem precisar procurar o botão.
                acoesDoTeclado = KeyboardActions(onDone = { enviar() }),
                foco = focoDaSenha,
            )

            val recado = estado.erro ?: aviso
            if (recado != null) {
                AvisoDeErro(recado)
            }

            if (demorando && estado.carregando) {
                Text(
                    text = "O servidor está acordando. Isso leva alguns segundos " +
                        "na primeira vez do dia.",
                    style = TipografiaBeazeth.bodyMedium,
                    color = Doce.tintaSuave,
                )
            }

            BotaoPrimario(
                texto = if (estado.carregando) "Entrando…" else "Entrar",
                aoTocar = enviar,
                habilitado = !estado.carregando,
                carregando = estado.carregando,
            )

            RodapeComLink(
                prefixo = "Ainda não tem conta?",
                link = "Criar uma agora",
                aoTocar = aoIrParaCadastro,
                habilitado = !estado.carregando,
            )

            // A terceira saída, e a única que não depende de rede.
            //
            // Fica abaixo das outras duas de propósito: conta é o caminho
            // principal, porque é o que dá o site junto e o que sobrevive à
            // troca de aparelho. Mas exigir conta para escrever um post-it
            // transforma "não tenho sinal agora" em "não dá para usar" -- e
            // este app existe justamente por causa disso.
            RodapeComLink(
                prefixo = "Prefere não criar conta?",
                link = "Usar só neste aparelho",
                aoTocar = aoUsarSemConta,
                habilitado = !estado.carregando,
            )
        }
    }
}
