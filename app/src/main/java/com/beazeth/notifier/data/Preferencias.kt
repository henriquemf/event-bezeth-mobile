package com.beazeth.notifier.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.beazeth.notifier.ui.theme.FONTE_PADRAO
import com.beazeth.notifier.ui.theme.PALETA_PADRAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * O que a pessoa escolheu, e o que o Pomodoro precisa lembrar.
 *
 * DataStore separado do da sessao de proposito: sair da conta apaga o token e o
 * banco local, mas nao deve apagar o tema. Quem escolheu Lavender Kiss nao
 * espera voltar ao rosa so por ter feito login de novo.
 *
 * O estado do Pomodoro mora aqui e nao no ViewModel por um motivo concreto: a
 * queixa era "troco de tela e a contagem para". Guardando o INSTANTE EM QUE
 * TERMINA -- e nao os segundos restantes -- o timer continua correto mesmo se o
 * app for fechado, o processo morto ou o aparelho reiniciado. O relogio do
 * sistema faz a conta; nao ha nada para manter vivo.
 */
private val Context.prefsAparencia by preferencesDataStore(name = "aparencia")

class Preferencias(private val context: Context) {

    private val chaveTema = stringPreferencesKey("tema")
    private val chaveFonte = stringPreferencesKey("fonte")
    private val chaveEscuro = booleanPreferencesKey("escuro")

    private val chavePomoMinutos = intPreferencesKey("pomo_minutos")
    private val chavePomoFimEm = longPreferencesKey("pomo_fim_em")
    private val chavePomoRestante = intPreferencesKey("pomo_restante")
    private val chavePomoCreditado = longPreferencesKey("pomo_creditado")
    private val chavePomoAvisado = longPreferencesKey("pomo_avisado")
    private val chaveAvisoDeEvento = longPreferencesKey("aviso_de_evento_ate")
    private val chaveSomDoAviso = stringPreferencesKey("som_do_aviso")
    private val chaveNomeNaTelaBloqueada = booleanPreferencesKey("nome_na_tela_bloqueada")

    val tema: Flow<String> = context.prefsAparencia.data.map { it[chaveTema] ?: PALETA_PADRAO }
    val fonte: Flow<String> = context.prefsAparencia.data.map { it[chaveFonte] ?: FONTE_PADRAO }
    val escuro: Flow<Boolean> = context.prefsAparencia.data.map { it[chaveEscuro] ?: false }

    suspend fun definirTema(chave: String) {
        context.prefsAparencia.edit { it[chaveTema] = chave }
    }

    suspend fun definirFonte(chave: String) {
        context.prefsAparencia.edit { it[chaveFonte] = chave }
    }

    suspend fun definirEscuro(ligado: Boolean) {
        context.prefsAparencia.edit { it[chaveEscuro] = ligado }
    }

    // ----------------------------------------------------------- pomodoro

    /** Duracao escolhida, em minutos. */
    val pomoMinutos: Flow<Int> = context.prefsAparencia.data.map { it[chavePomoMinutos] ?: 25 }

    /**
     * Quando a contagem termina, em milissegundos do relogio do sistema.
     *
     * Zero significa parado. E o instante, e nao os segundos que faltam, porque
     * o instante nao envelhece: reabrir o app cinco minutos depois da o numero
     * certo sem ninguem ter contado nada nesse meio-tempo.
     */
    val pomoFimEm: Flow<Long> = context.prefsAparencia.data.map { it[chavePomoFimEm] ?: 0L }

    /** Segundos que faltavam quando alguem pausou. So vale com `pomoFimEm` zerado. */
    val pomoRestante: Flow<Int> = context.prefsAparencia.data.map {
        it[chavePomoRestante] ?: ((it[chavePomoMinutos] ?: 25) * 60)
    }

    /**
     * O `pomoFimEm` do ultimo pomodoro que ja entrou na conta do perfil.
     *
     * Existe para o credito nao acontecer duas vezes. O momento em que um
     * pomodoro termina nao e um evento que alguem receba: o app pode estar
     * fechado, e quem descobre e a primeira tela que olhar para o relogio
     * depois -- podendo ser duas ao mesmo tempo (a tela do pomodoro e o widget
     * da lateral). Comparar com este carimbo faz o segundo a chegar nao ter o
     * que fazer.
     */
    val pomoCreditado: Flow<Long> =
        context.prefsAparencia.data.map { it[chavePomoCreditado] ?: 0L }

    suspend fun marcarPomodoroCreditado(fimEm: Long) {
        context.prefsAparencia.edit { it[chavePomoCreditado] = fimEm }
    }

    // -------------------------------------------------------------- avisos

    /**
     * Quais assuntos avisam. A chave e o `Canal.base` -- "agua", "pomodoro",
     * "eventos".
     *
     * Chega LIGADO por padrao: quem instala um app de lembretes espera ser
     * lembrado, e quem nao quiser desliga em dois toques. O contrario seria um
     * app que so funciona depois de alguem descobrir que precisa liga-lo.
     *
     * Desligado aqui nao e "o Android nao mostra": e o app **nao agendar**. O
     * alarme e desmarcado, e o aparelho para de ser acordado a toa.
     */
    fun avisoLigado(chave: String): Flow<Boolean> =
        context.prefsAparencia.data.map { it[booleanPreferencesKey("aviso_$chave")] ?: true }

    suspend fun definirAviso(chave: String, ligado: Boolean) {
        context.prefsAparencia.edit { it[booleanPreferencesKey("aviso_$chave")] = ligado }
    }

    /**
     * O nome do evento aparece na tela bloqueada, ou so "um compromisso seu"?
     *
     * Chega LIGADO: quem pos um lembrete quer le-lo de relance, sem digitar o
     * PIN, e num aparelho pessoal isso quase sempre e o que se quer. Desligado
     * e para quem deixa o tablet na mesa da sala.
     *
     * A escolha existe porque o Android nao a oferece de um jeito util: ele so
     * tem um "esconder conteudo sensivel" que vale para o aparelho inteiro, e
     * do Android 15 em diante ele nem usa mais o resumo que o app oferece --
     * troca a linha toda por "conteudo oculto". Ou o aviso e legivel, ou nao ha
     * aviso legivel nenhum; e essa decisao e de quem usa.
     */
    val nomeDoEventoNaTelaBloqueada: Flow<Boolean> =
        context.prefsAparencia.data.map { it[chaveNomeNaTelaBloqueada] ?: true }

    suspend fun definirNomeDoEventoNaTelaBloqueada(mostrar: Boolean) {
        context.prefsAparencia.edit { it[chaveNomeNaTelaBloqueada] = mostrar }
    }

    /**
     * O toque escolhido, pela chave do enum `Som`.
     *
     * Vazio quando ninguem escolheu ainda, e nao "sino": quem traduz chave em
     * som e `Som.porChave`, que ja devolve o padrao para o que nao reconhece.
     * Repetir o nome do padrao aqui seria uma segunda verdade para desencontrar
     * da primeira no dia em que o padrao mudar.
     */
    val somDoAviso: Flow<String> =
        context.prefsAparencia.data.map { it[chaveSomDoAviso] ?: "" }

    suspend fun definirSomDoAviso(chave: String) {
        context.prefsAparencia.edit { it[chaveSomDoAviso] = chave }
    }

    /**
     * O `pomoFimEm` do ultimo pomodoro que ja foi ANUNCIADO na barra.
     *
     * Irmao de [pomoCreditado], e pelo mesmo motivo: quem descobre que um
     * pomodoro acabou pode ser o alarme, mas tambem pode ser o app reabrindo
     * depois. Sem este carimbo, religar o aparelho reanunciaria um pomodoro que
     * ja apitou.
     *
     * Separado do credito porque as duas perguntas sao diferentes -- "ja entrou
     * na conta do perfil?" e "ja apareceu na barra?" -- e um pomodoro pode ter
     * sido creditado com os avisos desligados.
     */
    val pomodoroAvisado: Flow<Long> =
        context.prefsAparencia.data.map { it[chavePomoAvisado] ?: 0L }

    suspend fun marcarPomodoroAvisado(fimEm: Long) {
        context.prefsAparencia.edit { it[chavePomoAvisado] = fimEm }
    }

    /**
     * Ate que instante os lembretes da agenda ja foram tratados.
     *
     * Nao ha tabela de "avisos entregues": os gatilhos sao recalculados do zero
     * a cada rodada, e sem uma marca d'agua o lembrete de um evento seria
     * reentregue toda vez -- e a agenda e recalculada a cada sincronizacao, a
     * cada abertura do app e a cada alarme.
     *
     * Um `long` da conta porque os gatilhos sao percorridos em ordem de tempo:
     * tudo que e anterior a marca ja passou pela decisao de entregar ou nao.
     */
    val avisoDeEventoAte: Flow<Long> =
        context.prefsAparencia.data.map { it[chaveAvisoDeEvento] ?: 0L }

    suspend fun marcarAvisoDeEventoAte(instante: Long) {
        context.prefsAparencia.edit { it[chaveAvisoDeEvento] = instante }
    }

    suspend fun salvarPomodoro(minutos: Int, fimEm: Long, restante: Int) {
        context.prefsAparencia.edit {
            it[chavePomoMinutos] = minutos
            it[chavePomoFimEm] = fimEm
            it[chavePomoRestante] = restante
        }
    }
}
