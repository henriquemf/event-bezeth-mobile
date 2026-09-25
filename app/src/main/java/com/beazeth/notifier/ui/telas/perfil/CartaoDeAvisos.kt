package com.beazeth.notifier.ui.telas.perfil

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beazeth.notifier.avisos.Canal
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.avisos.Som
import com.beazeth.notifier.avisos.alarmeExatoLiberado
import com.beazeth.notifier.avisos.bateriaLiberada
import com.beazeth.notifier.avisos.podeAvisar
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.componentes.LinhaComChave
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.launch

/**
 * Onde os avisos se ajustam: o que avisa, com que som, e se o sistema deixa.
 *
 * ## Por que ha uma chave aqui se o Android ja tem a dele
 *
 * Parecem duas chaves para a mesma coisa -- e o projeto ja pagou por isso uma
 * vez, quando o modo escuro tinha tres interruptores. A diferenca aqui e de
 * SIGNIFICADO, nao de lugar:
 *
 * - A chave do sistema decide se o aviso **aparece**. O app continua acordando
 *   o aparelho na hora certa e postando um aviso que ninguem ve.
 * - A chave daqui decide se o lembrete **existe**. Desligada, o alarme e
 *   desmarcado: o aparelho para de ser acordado, e a bateria para de ser gasta.
 *
 * Fora que a do sistema mora tres telas fundo dos ajustes do Android, e esta
 * mora onde a pessoa esta.
 *
 * ## O estado do sistema e RELIDO a cada volta
 *
 * A permissao e pedida uma vez, na primeira abertura. Quem tocar em "Não
 * permitir" nunca mais ve aquela caixa, e sem este cartao o app ficaria mudo
 * para sempre parecendo quebrado. O caminho de conserto passa por sair daqui
 * para os ajustes e voltar -- e sem reler no `ON_RESUME`, quem acabasse de
 * ligar continuaria vendo "desligados".
 */
@Composable
internal fun CartaoDeAvisos() {
    val contexto = LocalContext.current
    val cores = Doce
    val dono = LocalLifecycleOwner.current
    val escopo = rememberCoroutineScope()
    val prefs = remember { Preferencias(contexto.applicationContext) }

    var ligados by remember { mutableStateOf(podeAvisar(contexto)) }
    var naHoraCerta by remember { mutableStateOf(alarmeExatoLiberado(contexto)) }
    var semFreioDeBateria by remember { mutableStateOf(bateriaLiberada(contexto)) }

    DisposableEffect(dono) {
        val observador = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) {
                ligados = podeAvisar(contexto)
                naHoraCerta = alarmeExatoLiberado(contexto)
                semFreioDeBateria = bateriaLiberada(contexto)
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose { dono.lifecycle.removeObserver(observador) }
    }

    val som by prefs.somDoAviso.collectAsStateWithLifecycle("")

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
                !semFreioDeBateria ->
                    "Os avisos chegam, mas alguns aparelhos seguram os alarmes de " +
                        "um app que passou dias sem ser aberto, para poupar bateria. " +
                        "Liberar o app disso garante o lembrete de água mesmo assim."
                else ->
                    "Chegam mesmo com o app fechado e a tela apagada. Tocar num " +
                        "aviso abre a tela do assunto."
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
        } else if (!semFreioDeBateria) {
            BotaoPrimario(
                texto = "Liberar em segundo plano",
                aoTocar = { contexto.startActivity(ajustesDeBateria(contexto.packageName)) },
            )
        }

        // ------------------------------------------------------ o que avisa
        Subtitulo("O que avisar")

        for (canal in Canal.entries) {
            val marcado by prefs.avisoLigado(canal.base).collectAsStateWithLifecycle(true)
            LinhaComChave(
                titulo = canal.titulo,
                descricao = canal.descricao,
                marcado = marcado,
                aoMudar = { novo ->
                    escopo.launch {
                        prefs.definirAviso(canal.base, novo)
                        // Sem isto a escolha so valeria no proximo recalculo:
                        // desligar deixaria o alarme ja marcado tocar mais uma
                        // vez, e religar nao remarcaria nada ate o app reabrir.
                        Lembretes.rearmar(contexto)
                    }
                },
            )
        }

        // --------------------------------------------- a festa do pomodoro
        Subtitulo("Fim do foco")

        val festa by prefs.festaDoPomodoro.collectAsStateWithLifecycle(true)

        LinhaComChave(
            titulo = "Confete e palmas",
            descricao = "Quando o foco acaba, com o app aberto.",
            marcado = festa,
            aoMudar = { novo -> escopo.launch { prefs.definirFestaDoPomodoro(novo) } },
        )

        // ------------------------------------------- a tela bloqueada
        Subtitulo("Na tela bloqueada")

        val mostrarNome by prefs.nomeDoEventoNaTelaBloqueada
            .collectAsStateWithLifecycle(true)

        LinhaComChave(
            titulo = "Mostrar o nome do evento",
            descricao = "Desligado, aparece só \"um compromisso seu\", sem dizer qual.",
            marcado = mostrarNome,
            aoMudar = { novo ->
                escopo.launch { prefs.definirNomeDoEventoNaTelaBloqueada(novo) }
            },
        )

        Text(
            text = "Água e pomodoro sempre aparecem por inteiro — \"hora de beber " +
                "água\" não revela nada de ninguém.",
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
            color = cores.tintaSuave,
        )

        // ------------------------------------------------------------- o som
        Subtitulo("Som")

        EscolhaDeSom(
            escolhido = Som.porChave(som),
            aoEscolher = { novo ->
                escopo.launch {
                    prefs.definirSomDoAviso(novo.chave)
                    // Trocar o som TROCA O CANAL DE LUGAR (ver `Som`), entao o
                    // canal novo precisa nascer agora -- senao o proximo aviso
                    // sairia por um canal que ainda nao existe.
                    Lembretes.rearmar(contexto)
                }
            },
        )

        Text(
            text = "O toque escolhido vale para os três. Se quiser um som diferente " +
                "em cada um, ou mudar a vibração, isso fica nos ajustes do Android.",
            style = TipografiaBeazeth.bodyMedium.copy(fontSize = 12.sp),
            color = cores.tintaSuave,
        )
    }
}

@Composable
private fun Subtitulo(texto: String) {
    Text(
        text = texto,
        style = TipografiaBeazeth.labelLarge,
        color = Doce.tintaSuave,
        modifier = Modifier.padding(top = Espaco.e2),
    )
}

/**
 * A tela de avisos DESTE app, e nao a lista de todos os apps.
 *
 * `ACTION_APP_NOTIFICATION_SETTINGS` cai direto nos canais, que e onde a pessoa
 * resolve tanto "esta tudo desligado" quanto "quero outra vibracao".
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

/**
 * A caixa do sistema que pergunta "deixar este app ignorar a otimizacao de
 * bateria?". Uma pergunta so, com Sim e Nao -- e nao a lista de todos os apps,
 * onde a pessoa teria de achar este. Ver `bateriaLiberada`.
 */
private fun ajustesDeBateria(pacote: String): Intent =
    Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        android.net.Uri.parse("package:$pacote"),
    )
