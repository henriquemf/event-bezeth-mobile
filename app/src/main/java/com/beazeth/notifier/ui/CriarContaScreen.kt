package com.beazeth.notifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Spacer
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
 * Criar conta -- o mesmo cartão da tela `/criar-conta` do site.
 *
 * Mesma casca da tela de entrar, com os quatro campos do formulário de lá. O
 * texto de apoio também é o mesmo: "Seu espaço é só seu".
 *
 * **A validação de verdade é do servidor.** Aqui só se confere o que não vale a
 * viagem: campo vazio e as duas senhas diferentes -- esta última nem existe na
 * API, porque a confirmação é conversa entre a pessoa e o formulário. Regra de
 * senha mínima, formato de e-mail e conta repetida ficam com quem manda, e a
 * mensagem que aparece é a que o servidor devolveu. Duplicar essas regras aqui
 * seria criar uma segunda verdade, que envelheceria primeiro.
 */
@Composable
fun CriarContaScreen(
    estado: EstadoLogin,
    aoCriar: (String, String, String) -> Unit,
    aoIrParaLogin: () -> Unit,
    aoUsarSemConta: () -> Unit,
) {
    var nome by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var confirmacao by remember { mutableStateOf("") }
    var aviso by remember { mutableStateOf<String?>(null) }

    val focoDaConfirmacao = remember { FocusRequester() }

    // Mesma armadilha da tela de entrar, e pelo mesmo motivo -- ver o
    // comentario la e o de `RodapeComLink`.
    LaunchedEffect(estado.carregando, estado.erro) {
        if (!estado.carregando && estado.erro != null) {
            runCatching { focoDaConfirmacao.requestFocus() }
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
        when {
            estado.carregando -> Unit
            nome.isBlank() || email.isBlank() || senha.isBlank() ->
                aviso = "Preencha todos os campos."

            senha != confirmacao ->
                aviso = "As duas senhas não são iguais."

            else -> {
                aviso = null
                aoCriar(nome, email, senha)
            }
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
                titulo = "Criar conta",
                subtitulo = "Seu espaço é só seu: ninguém mais vê o que você guarda aqui.",
            )

            CampoDoce(
                rotulo = "Como te chamamos?",
                valor = nome,
                aoMudar = { nome = it; aviso = null },
                habilitado = !estado.carregando,
                opcoesDoTeclado = KeyboardOptions(imeAction = ImeAction.Next),
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
                    imeAction = ImeAction.Next,
                ),
            )

            Text(
                text = "Pelo menos 8 caracteres.",
                style = TipografiaBeazeth.bodyMedium,
                color = Doce.tintaSuave,
            )

            CampoDoce(
                rotulo = "Repita a senha",
                valor = confirmacao,
                aoMudar = { confirmacao = it; aviso = null },
                habilitado = !estado.carregando,
                ocultarTexto = true,
                opcoesDoTeclado = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                acoesDoTeclado = KeyboardActions(onDone = { enviar() }),
                foco = focoDaConfirmacao,
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
                texto = if (estado.carregando) "Criando…" else "Criar minha conta",
                aoTocar = enviar,
                habilitado = !estado.carregando,
                carregando = estado.carregando,
            )

            RodapeComLink(
                prefixo = "Já tem conta?",
                link = "Entrar",
                aoTocar = aoIrParaLogin,
                habilitado = !estado.carregando,
            )

            // A mesma saída da tela de entrar. Existe nas duas porque quem
            // chega aqui pelo "criar uma agora" e muda de ideia não deveria ter
            // de voltar uma tela para encontrá-la.
            RodapeComLink(
                prefixo = "Prefere não criar conta?",
                link = "Usar só neste aparelho",
                aoTocar = aoUsarSemConta,
                habilitado = !estado.carregando,
            )
        }
    }
}
