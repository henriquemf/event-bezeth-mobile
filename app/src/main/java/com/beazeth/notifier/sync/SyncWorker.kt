package com.beazeth.notifier.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.beazeth.notifier.avisos.Lembretes
import com.beazeth.notifier.data.Sincronizador
import java.util.concurrent.TimeUnit

/**
 * Quem roda a sincronizacao, e quando.
 *
 * WorkManager e nao uma corrotina solta no ViewModel: o pedido sobrevive ao app
 * ser fechado, ao aparelho reiniciar e a falta de rede. Uma tarefa criada no
 * elevador precisa subir quando o sinal voltar, mesmo que o app ja tenha sido
 * fechado -- e um `viewModelScope.launch` morre junto com a tela.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (Sincronizador(applicationContext).rodar()) {
        is Sincronizador.Fim.Ok -> {
            // A sincronizacao e a unica porta por onde entra mudanca vinda do
            // SITE: um evento criado no computador, um intervalo de agua
            // ajustado por la. Sem recalcular aqui, esse evento so passaria a
            // avisar na proxima vez que alguem abrisse o app -- possivelmente
            // depois da hora dele.
            //
            // Vale tambem para a rodada de hora em hora, o que da ao aparelho
            // uma segunda chance periodica de acertar os alarmes.
            Lembretes.rearmar(applicationContext)
            Result.success()
        }

        // `retry` e nao `failure`: o WorkManager reagenda sozinho, com espera
        // crescente. Dizer `failure` aqui descartaria a fila em cima de uma
        // queda de rede de dois segundos.
        is Sincronizador.Fim.Adiado -> Result.retry()

        // Token vencido nao melhora com tentativa. Repetir so gastaria bateria
        // ate alguem entrar de novo -- e ai a propria entrada dispara a sync.
        is Sincronizador.Fim.SemSessao -> Result.failure()
    }

    companion object {
        private const val AGORA = "sync-agora"
        private const val PERIODICO = "sync-periodico"

        private val COM_REDE = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Sincroniza assim que der.
         *
         * Chamado depois de toda escrita e ao abrir o app. `KEEP` e deliberado:
         * marcar cinco tarefas seguidas nao deve enfileirar cinco execucoes --
         * a que ja esta rodando vai encontrar as cinco pendencias na fila.
         */
        fun agora(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                AGORA,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(COM_REDE)
                    .build(),
            )
        }

        /**
         * A rede de seguranca: de hora em hora, mesmo sem ninguem mexer.
         *
         * Serve para o que muda no SITE chegar ao celular sem precisar abrir o
         * app -- um evento criado no computador, por exemplo. Uma hora e o
         * meio-termo entre bateria e frescor; o minimo que o Android aceita
         * para trabalho periodico e quinze minutos.
         */
        fun periodico(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODICO,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                    .setConstraints(COM_REDE)
                    .build(),
            )
        }

        /** Ao sair da conta: nao faz sentido continuar batendo no servidor. */
        fun parar(context: Context) {
            WorkManager.getInstance(context).apply {
                cancelUniqueWork(AGORA)
                cancelUniqueWork(PERIODICO)
            }
        }
    }
}
