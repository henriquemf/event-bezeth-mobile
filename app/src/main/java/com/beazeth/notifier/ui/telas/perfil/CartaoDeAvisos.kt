package com.beazeth.notifier.ui.telas.perfil

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.beazeth.notifier.avisos.alarmeExatoLiberado
import com.beazeth.notifier.avisos.podeAvisar
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * O cartao que diz se os avisos estao mesmo chegando.
 *
 * ## Por que uma tela para isto
 *
 * A permissao de avisar e pedida uma vez, na primeira abertura. Quem tocar em
 * "Não permitir" nunca mais ve aquela caixa -- o Android nao a mostra de novo,
 * por mais que o app peça. Sem este cartao, o app ficaria mudo para sempre e
 * pareceria quebrado: o pomodoro terminaria em silencio, o lembrete de agua
 * nunca chegaria, e nada na tela explicaria por que.
 *
 * O estado e RELIDO a cada volta para o app, e nao lido uma vez: o caminho
 * inteiro passa por sair daqui para os ajustes do sistema e voltar. Sem a
 * releitura, quem acabasse de ligar os avisos continuaria vendo "desligados" e
 * concluiria que nao adiantou.
 */
@Composable
internal fun CartaoDeAvisos() {
    val contexto = LocalContext.current
    val cores = Doce
    val dono = LocalLifecycleOwner.current

    var ligados by remember { mutableStateOf(podeAvisar(contexto)) }
    var naHoraCerta by remember { mutableStateOf(alarmeExatoLiberado(contexto)) }

    DisposableEffect(dono) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) {
                ligados = podeAvisar(contexto)
                naHoraCerta = alarmeExatoLiberado(contexto)
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose { dono.lifecycle.removeObserver(observador) }
    }

    CartaoDaTela(titulo = "Avisos") {
        Linha(rotulo = "Lembretes", valor = if (ligados) "ligados" else "desligados")

        Text(
            text = when {
                !ligados ->
                    "O sistema está bloqueando os avisos deste app. Sem eles o " +
                        "pomodoro termina em silêncio e o lembrete de água não chega."
                !naHoraCerta ->
                    "Os avisos chegam, mas podem atrasar: o sistema não está " +
                        "deixando o app marcar alarmes na hora exata."
                else ->
                    "Você é avisada quando o pomodoro acaba, na hora de beber água e " +
                        "quando um evento da agenda chega. Cada um tem a sua chave nos " +
                        "ajustes, se algum incomodar."
            },
            style = TipografiaBeazeth.bodyMedium,
            color = cores.tintaSuave,
        )

        if (!ligados) {
            BotaoPrimario(
                texto = "Abrir os ajustes",
                aoTocar = { contexto.startActivity(ajustesDeAviso(contexto.packageName)) },
            )
        } else if (!naHoraCerta && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BotaoPrimario(
                texto = "Permitir hora exata",
                aoTocar = { contexto.startActivity(ajustesDeAlarme(contexto.packageName)) },
            )
        }
    }
}

/**
 * A tela de avisos DESTE app, e nao a lista de todos os apps.
 *
 * `ACTION_APP_NOTIFICATION_SETTINGS` cai direto nos tres canais -- agua,
 * pomodoro e agenda --, que e onde a pessoa resolve tanto "esta tudo
 * desligado" quanto "so o da agua me incomoda".
 */
private fun ajustesDeAviso(pacote: String): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, pacote)

/** A chave de "alarmes e lembretes", que existe do Android 12 em diante. */
private fun ajustesDeAlarme(pacote: String): Intent =
    Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        android.net.Uri.parse("package:$pacote"),
    )
