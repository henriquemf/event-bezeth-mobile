package com.beazeth.notifier.avisos

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.beazeth.notifier.R
import com.beazeth.notifier.data.Preferencias
import kotlinx.coroutines.flow.first

/**
 * Qualquer coisa que a tela de sons saiba listar, mostrar e deixar ouvir.
 *
 * Existe porque ha DUAS listas de opcoes -- os toques dos avisos ([Som]) e os
 * sons da festa ([SomDaFesta]) -- e uma tela so para escolher entre elas. Sem
 * isto seriam duas telas iguais, uma por enum.
 */
interface OpcaoDeSom {
    val chave: String
    val nome: String
    val descricao: String

    /** Onde o som mora, ou `null` quando nao ha nada para tocar ("sem som"). */
    fun uri(context: Context): Uri?
}

/** `android.resource://<pacote>/<id>`, o endereco que canal e `Ringtone` aceitam. */
internal fun enderecoDe(context: Context, recurso: Int): Uri =
    Uri.parse("android.resource://${context.packageName}/$recurso")

/**
 * Os toques que os avisos podem ter.
 *
 * ## Por que trocar de som obriga a trocar o canal de lugar
 *
 * O som de um canal e copiado UMA vez, quando ele nasce. Depois disso quem
 * manda e a pessoa, nos ajustes do sistema, e `setSound` num canal existente
 * nao faz nada -- e nem apagar e recriar resolve, porque o Android lembra dos
 * ajustes de um canal apagado e os restaura quando um canal com o MESMO id
 * reaparece.
 *
 * O jeito que funciona e o id carregar a escolha: `pomodoro_marimba` e um canal
 * diferente de `pomodoro_gotinha`. Trocar o som cria o novo e apaga o velho, e
 * e por isso que [chave] entra no id do canal em [Canal.idCom].
 *
 * O preco: quem tiver ajustado esse canal a mao nos ajustes do sistema perde o
 * ajuste ao trocar o som pelo app. E aceitavel porque foi a propria pessoa que
 * acabou de pedir a troca -- ninguem troca o toque esperando manter o toque.
 *
 * ## Chave nova a cada troca de arquivo
 *
 * O canal guarda o endereco do som pelo id NUMERICO do recurso, e o build
 * renumera os recursos quando a lista de arquivos muda -- conferido no
 * emulador: o numero que era da Gotinha na 1.7.1 passou a ser de outro arquivo
 * na 1.8.0. Trocar so o conteudo de um arquivo, mantendo a chave, deixaria o
 * canal velho tocando o som errado por acaso. Por isso nenhuma chave desta
 * lista repete uma chave de versao anterior, e [ANTIGAS] leva cada uma delas
 * ao toque novo mais parecido.
 *
 * ## De onde vem cada toque, e por que nao chia
 *
 * Marimba, vibrafone e glockenspiel sao notas gravadas em estudio pela
 * Universidade de Iowa (MIS), em AIFF sem perda, livres para qualquer uso. A
 * gotinha e da Kenney (CC0), feita digitalmente -- sem microfone, sem ruido de
 * sala. O tratamento (ver o README) tira o chiado de gravacao que ainda havia,
 * e o arquivo final e WAV sem perda: medido, o chiado ficou abaixo de -97 dBFS
 * em todos, onde nenhum alto-falante de celular chega.
 */
enum class Som(
    override val chave: String,
    override val nome: String,
    override val descricao: String,
) : OpcaoDeSom {
    MARIMBA("marimba", "Marimba", "Três notas de madeira subindo. Alegre e macio."),
    VIBRAFONE("vibrafone", "Vibrafone", "Duas notas longas, calmas, que se apagam devagar."),
    SININHO("glockenspiel", "Sininho", "Duas notas claras de glockenspiel."),
    GOTINHA("gotinha", "Gotinha", "Duas gotas, plic-ploc. O mais curto de todos."),

    /**
     * O toque de notificacao do proprio aparelho.
     *
     * Fica na lista porque e o que muita gente espera, e porque quem trocar o
     * som do sistema espera que o app acompanhe. E o mais alto de todos: o
     * padrao do Android e desenhado para chamar.
     */
    SISTEMA("sistema", "Toque do aparelho", "O mesmo som das outras notificações."),

    /** Sem som nenhum. A vibracao de cada canal continua valendo. */
    MUDO("mudo", "Sem som", "Só aparece na tela, sem tocar nada.");

    override fun uri(context: Context): Uri? = when (this) {
        MUDO -> null
        SISTEMA -> Settings.System.DEFAULT_NOTIFICATION_URI
        else -> enderecoDe(context, recurso())
    }

    private fun recurso(): Int = when (this) {
        MARIMBA -> R.raw.aviso_marimba
        VIBRAFONE -> R.raw.aviso_vibrafone
        SININHO -> R.raw.aviso_glockenspiel
        GOTINHA -> R.raw.aviso_gotinha
        // Os dois nao tem arquivo; `uri` os resolve antes de chegar aqui.
        SISTEMA, MUDO -> 0
    }

    companion object {
        /**
         * As chaves de versoes anteriores, no toque novo mais parecido.
         *
         * 1.7.1: `sino`, `gota`, `harpa`. 1.8.0: `kalimba`, `caixinha`,
         * `sininho`, `bolhas`, `harpinha`.
         */
        private val ANTIGAS = mapOf(
            "sino" to SININHO, "gota" to GOTINHA, "harpa" to VIBRAFONE,
            "kalimba" to MARIMBA, "caixinha" to SININHO, "sininho" to SININHO,
            "bolhas" to GOTINHA, "harpinha" to VIBRAFONE,
        )

        /** O som guardado, ou [padrao] se a chave nao for reconhecida. */
        fun porChave(chave: String?, padrao: Som): Som =
            entries.firstOrNull { it.chave == chave } ?: ANTIGAS[chave] ?: padrao
    }
}

/**
 * O que toca junto do confete, com o app aberto, quando o foco acaba.
 *
 * Nao e um aviso -- nao passa por canal nenhum, quem toca e o proprio app --,
 * e por isso tem a lista dele. A marimba vem de fabrica e as palmas ficam como
 * opcao: palmas SAO rajadas de ruido, e para quem quer zero chiado nao ha
 * palmas que sirvam. A marimba em festa e o mesmo instrumento do aviso, quatro
 * notas subindo, e mede abaixo de -100 dBFS de chiado.
 */
enum class SomDaFesta(
    override val chave: String,
    override val nome: String,
    override val descricao: String,
) : OpcaoDeSom {
    MARIMBA("marimba", "Marimba em festa", "Quatro notas subindo, um \"ta-dá\"."),
    PALMAS("palmas", "Palmas", "Uma salva de palmas de verdade."),
    MUDO("mudo", "Sem som", "Só o confete, em silêncio.");

    override fun uri(context: Context): Uri? = when (this) {
        MARIMBA -> enderecoDe(context, R.raw.festa_marimba)
        PALMAS -> enderecoDe(context, R.raw.festa_palmas)
        MUDO -> null
    }

    companion object {
        val PADRAO = MARIMBA

        fun porChave(chave: String?): SomDaFesta =
            entries.firstOrNull { it.chave == chave } ?: PADRAO
    }
}

/**
 * O som de [canal]: o escolhido para ele; senao o toque unico de antes da
 * 1.9.0 (quem tinha escolhido um continua com ele); senao o padrao do canal.
 *
 * Funcao pura para a tela e a entrega usarem a MESMA regra -- a tela mostraria
 * um nome e o aviso tocaria outro no dia em que as duas contas divergissem.
 */
internal fun resolverSom(proprio: String, antigo: String, canal: Canal): Som = when {
    proprio.isNotEmpty() -> Som.porChave(proprio, canal.somPadrao)
    antigo.isNotEmpty() -> Som.porChave(antigo, canal.somPadrao)
    else -> canal.somPadrao
}

/** O som que [canal] deve tocar agora. */
internal suspend fun somDoCanal(context: Context, canal: Canal): Som {
    val prefs = Preferencias(context)
    return resolverSom(prefs.somDoCanal(canal.base).first(), prefs.somDoAviso.first(), canal)
}

/** O som de cada canal, para criar todos de uma vez. */
internal suspend fun sonsDosCanais(context: Context): Map<Canal, Som> =
    Canal.entries.associateWith { somDoCanal(context, it) }
