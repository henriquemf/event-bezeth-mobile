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
    private val chavePomoDescansoAte = longPreferencesKey("pomo_descanso_ate")
    private val chaveSubs = stringPreferencesKey("pomo_subs")
    private val chavePomoFestejado = longPreferencesKey("pomo_festejado")
    private val chaveFesta = booleanPreferencesKey("festa_do_pomodoro")
    private val chaveAguaMarco = longPreferencesKey("agua_marco_em")
    private val chaveAguaProximo = longPreferencesKey("agua_proximo_em")

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

    /**
     * Quando o descanso termina, ou zero se nao ha descanso correndo.
     *
     * O descanso comeca sozinho quando o foco acaba -- essa e a regra do
     * pomodoro, e um botao "agora descansar" seria so um jeito de esquecer de
     * aperta-lo. Guardar o INSTANTE do fim, e nao os segundos restantes, e a
     * mesma escolha de `pomoFimEm`: a conta e feita pelo relogio quando alguem
     * olha, entao fechar o app nao desalinha nada.
     *
     * Voltar a zero e o que diz "o descanso acabou e ja foi anunciado" -- por
     * isso nao ha um carimbo separado para ele.
     */
    val pomoDescansoAte: Flow<Long> =
        context.prefsAparencia.data.map { it[chavePomoDescansoAte] ?: 0L }

    suspend fun marcarDescansoAte(instante: Long) {
        context.prefsAparencia.edit { it[chavePomoDescansoAte] = instante }
    }

    /**
     * O pomodoro cuja festa ja aconteceu, pelo instante em que terminou.
     *
     * Sem este carimbo, reabrir o app com um pomodoro vencido soltaria confete
     * de novo -- e de novo a cada abertura, porque `pomoFimEm` continua gravado
     * ate alguem comecar outro.
     */
    val pomodoroFestejado: Flow<Long> =
        context.prefsAparencia.data.map { it[chavePomoFestejado] ?: 0L }

    suspend fun marcarPomodoroFestejado(instante: Long) {
        context.prefsAparencia.edit { it[chavePomoFestejado] = instante }
    }

    /** Confete e palmas no fim do foco. Ligado de fabrica. */
    val festaDoPomodoro: Flow<Boolean> =
        context.prefsAparencia.data.map { it[chaveFesta] ?: true }

    suspend fun definirFestaDoPomodoro(ligada: Boolean) {
        context.prefsAparencia.edit { it[chaveFesta] = ligada }
    }

    /**
     * O ultimo marco da agua, em milissegundos do relogio: o copo mais recente
     * ou o lembrete mais recente, o que tiver acontecido por ultimo.
     *
     * E o `last_sent_at` do servidor, so que do aparelho. O proximo lembrete e
     * um intervalo depois disto -- ver `avisos/Agua.kt`. Zero e "nunca", e
     * nunca quer dizer VENCIDO: o primeiro lembrete sai assim que a janela
     * estiver aberta, como no servidor com `last_sent_at` nulo.
     */
    val aguaMarcoEm: Flow<Long> =
        context.prefsAparencia.data.map { it[chaveAguaMarco] ?: 0L }

    suspend fun marcarAgua(instante: Long) {
        context.prefsAparencia.edit { it[chaveAguaMarco] = instante }
    }

    /**
     * Para quando o proximo lembrete de agua esta marcado, ou zero se nao ha.
     *
     * Escrito por quem marca o alarme, lido pela tela de agua, que mostra
     * "proximo lembrete as 11:37". E a resposta a pergunta que ficava no ar:
     * "e quando e que ele vai me lembrar?" -- sem isto, o unico jeito de saber
     * se o lembrete existia era esperar por ele.
     */
    val aguaProximoEm: Flow<Long> =
        context.prefsAparencia.data.map { it[chaveAguaProximo] ?: 0L }

    suspend fun marcarProximoCopo(instante: Long) {
        context.prefsAparencia.edit { it[chaveAguaProximo] = instante }
    }

    /**
     * Os outros pomodoros da tela, ate dez. Ver [SubPomodoro].
     *
     * Uma chave so para a lista inteira, e nao seis chaves por sub: sessenta
     * chaves com numero no nome dariam um `combine` de sessenta fluxos para
     * responder "o que esta correndo agora".
     */
    val subsDoPomodoro: Flow<List<SubPomodoro>> =
        context.prefsAparencia.data.map { lerSubs(it[chaveSubs]) }

    suspend fun salvarSubs(lista: List<SubPomodoro>) {
        context.prefsAparencia.edit { it[chaveSubs] = escreverSubs(lista) }
    }

    suspend fun salvarPomodoro(minutos: Int, fimEm: Long, restante: Int) {
        context.prefsAparencia.edit {
            it[chavePomoMinutos] = minutos
            it[chavePomoFimEm] = fimEm
            it[chavePomoRestante] = restante
        }
    }
}
