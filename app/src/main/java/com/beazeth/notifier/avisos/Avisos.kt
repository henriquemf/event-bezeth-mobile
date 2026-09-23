package com.beazeth.notifier.avisos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.beazeth.notifier.MainActivity
import com.beazeth.notifier.R

/**
 * Um aviso na barra de notificacao: os canais, e o ato de postar.
 *
 * ## Por que nada disto passa pelo servidor
 *
 * O site avisa por web push, o que exige o navegador aberto e uma ida ao
 * servidor no instante do aviso. Aqui nada disso e necessario: **o aparelho ja
 * sabe tudo o que precisa para avisar sozinho.** A configuracao de agua
 * (intervalo, janela) e a agenda inteira ja estao no Room, e o pomodoro nunca
 * saiu daqui. Um aviso local acerta com o aparelho em modo aviao; um push
 * dependeria de rede justamente no minuto em que ela pode faltar.
 *
 * ## Tres canais, e nao um
 *
 * A partir do Android 8 quem decide o volume, a vibracao e se o aviso aparece
 * na tela e a PESSOA, canal por canal, nos ajustes do sistema. Um canal so
 * transformaria "o lembrete de agua esta me atrapalhando" em "desliguei os
 * avisos do app" -- e junto iria o fim do pomodoro. Separados, da para calar a
 * agua e continuar sendo avisado da prova de amanha.
 *
 * ## O som, e por que ele e um arquivo do app
 *
 * O toque padrao de notificacao muda de aparelho para aparelho e quase sempre e
 * desenhado para CHAMAR: ataque seco e volume cheio. Num app que avisa varias
 * vezes ao dia isso cansa, e cansar e o que faz alguem desligar tudo.
 *
 * Os toques em `res/raw/` sao sinos curtos, de ataque macio, gerados com pico
 * entre 26% e 30% da escala -- ou seja, nascem baixos, e nao so parecem baixos.
 * Sendo arquivos do app, soam igual em qualquer aparelho. Qual deles vale e
 * escolha de quem usa, na tela de perfil; ver [Som].
 *
 * ## O que esta escrito aqui vale SO NA CRIACAO do canal
 *
 * Importancia, som e vibracao sao copiados uma unica vez, quando o canal nasce.
 * Dali em diante quem manda e a pessoa, nos ajustes do sistema, e o Android
 * IGNORA o que o codigo pedir -- mudar um valor deste arquivo nao mexe em quem
 * ja instalou. E o comportamento certo: ninguem quer uma atualizacao religando
 * um som que desligou a mao.
 *
 * E por isso que trocar o toque pelo app **troca o canal de lugar** em vez de
 * mexer no que existe: o id carrega a escolha (`pomodoro_gota`), e o canal
 * velho e apagado. Ver [Som] para o detalhe de por que apagar e recriar com o
 * mesmo id NAO funcionaria.
 *
 * ## O que a tela bloqueada mostra
 *
 * Num aparelho com PIN, o Android pode trocar o texto de um aviso por
 * "conteudo oculto" na tela de bloqueio -- e ai o lembrete chega, mas nao diz
 * nada. Quem decide e a [visibilidade] de cada aviso, e ela nao e a mesma para
 * os tres:
 *
 * - **Agua e pomodoro sao PUBLIC.** "Hora de beber agua" e "acabou o tempo" nao
 *   revelam nada de ninguem, e sao justamente os que precisam ser lidos de
 *   relance, sem desbloquear.
 * - **A agenda depende de uma escolha**, porque o nome de um evento pode ser
 *   assunto de quem o marcou -- "Consulta" na tela de bloqueio e visivel para
 *   quem passar perto da mesa. O padrao e mostrar (quem pos o lembrete quer
 *   le-lo de relance); a chave esta no cartao de avisos do perfil.
 *
 * Quando a agenda fica PRIVATE, o aviso leva junto uma versao publica dizendo
 * que HA um lembrete e para quando, sem dizer qual. **Do Android 15 em diante
 * o sistema ignora essa versao** e troca a linha inteira pelo "conteudo oculto"
 * dele -- conferido no emulador, com a versao publica chegando corretamente ao
 * `dumpsys` e nao aparecendo na tela. Ela fica porque continua valendo nas
 * versoes anteriores, e porque nao custa nada.
 */
enum class Canal(
    /**
     * A raiz do id do canal. O id de verdade leva o som junto
     * (`pomodoro_gota`) -- ver [idCom] e [Som].
     */
    val base: String,
    val titulo: String,
    val descricao: String,
    val importancia: Int,
    val vibra: Boolean,
    val visibilidade: Int,
) {
    AGUA(
        base = "agua",
        titulo = "Beber água",
        descricao = "No intervalo e na janela escolhidos na tela de hidratação.",
        // DEFAULT e nao HIGH: e um empurraozinho de rotina. Aparece na barra e
        // faz um som, mas nao toma a tela de quem esta no meio de outra coisa.
        // Sem vibracao pelo mesmo motivo -- este e o aviso que mais se repete
        // no dia, e o que mais cansa se insistir.
        importancia = NotificationManager.IMPORTANCE_DEFAULT,
        vibra = false,
        visibilidade = NotificationCompat.VISIBILITY_PUBLIC,
    ),
    POMODORO(
        base = "pomodoro",
        titulo = "Pomodoro",
        descricao = "Quando a contagem chega ao fim.",
        // HIGH: quem pos um cronometro quer saber na hora, e este e o unico
        // aviso que a pessoa pediu explicitamente, minutos antes, apertando um
        // botao. HIGH e sobre APARECER na frente, nao sobre volume -- o som e o
        // mesmo sino baixo dos outros.
        importancia = NotificationManager.IMPORTANCE_HIGH,
        vibra = true,
        visibilidade = NotificationCompat.VISIBILITY_PUBLIC,
    ),
    EVENTOS(
        base = "eventos",
        titulo = "Agenda",
        descricao = "Os lembretes dos eventos do calendário.",
        importancia = NotificationManager.IMPORTANCE_HIGH,
        vibra = true,
        visibilidade = NotificationCompat.VISIBILITY_PRIVATE,
    );

    /** O id do canal deste assunto com [som] tocando. Ver [Som]. */
    fun idCom(som: Som): String = "${base}_${som.chave}"
}

/** Extra que diz a [MainActivity] em que tela abrir. Ver `CascaApp`. */
const val EXTRA_DESTINO = "destino"

/**
 * O numero que identifica um aviso, derivado de um nome estavel.
 *
 * Postar de novo com o mesmo numero SUBSTITUI o aviso anterior, e e isso que se
 * quer: recalcular a agenda duas vezes nao pode encher a barra com o mesmo
 * lembrete duas vezes. O numero tambem vira o `requestCode` do
 * `PendingIntent`, e por isso precisa ser unico entre TIPOS diferentes -- dai
 * ser um so espaco de nomes ("agua", "pomodoro", "evento-12-event_now") em vez
 * de contadores separados que se cruzariam no 1.
 *
 * Dois nomes diferentes podem, em teoria, cair no mesmo `hashCode`. Com algumas
 * dezenas de avisos a chance e de uma em milhoes, e o estrago seria um aviso
 * ocupar o lugar do outro na barra -- nao uma queda.
 */
internal fun idDoAviso(nome: String): Int = nome.hashCode()

/**
 * Cria os canais, se ainda nao existirem.
 *
 * Chamado de todo lugar que possa ser o PRIMEIRO a rodar depois de instalar --
 * a tela, o despertador, o religar. Nao ha um `Application.onCreate` no projeto
 * e nao vale a pena criar um so para isto: `createNotificationChannel` e
 * idempotente e custa microssegundos.
 *
 * Criar cedo tambem importa para a pessoa: um canal que nunca foi criado NAO
 * aparece nos ajustes do sistema. Sem isto, quem fosse desligar so o lembrete
 * de agua nao encontraria a chave ate o primeiro aviso ter chegado.
 */
fun garantirCanais(context: Context, som: Som) {
    val gerente = context.getSystemService(NotificationManager::class.java) ?: return
    val toque = som.uri(context)
    val comoTocar = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    val atuais = Canal.entries.map { it.idCom(som) }.toSet()

    for (canal in Canal.entries) {
        gerente.createNotificationChannel(
            NotificationChannel(canal.idCom(som), canal.titulo, canal.importancia).apply {
                description = canal.descricao
                setSound(toque, comoTocar)
                enableVibration(canal.vibra)
                // Um toque so, curto. O padrao de um canal de importancia alta
                // e um zumbido duplo e longo, que junto do som vira sobressalto
                // -- e o alvo aqui e ser notado, nao assustar.
                if (canal.vibra) vibrationPattern = longArrayOf(0, 120)
            },
        )
    }

    // Os canais do som ANTERIOR, que nao valem mais. Sem isto os ajustes do
    // sistema encheriam de "Pomodoro" repetido, um por som ja experimentado, e
    // nao haveria como saber qual esta valendo.
    //
    // A comparacao aceita o id sem sufixo (`agua`) porque foi assim que os
    // canais nasceram na primeira versao dos avisos, antes de haver escolha de
    // som -- quem instalou aquela tem tres canais orfaos para limpar.
    for (existente in gerente.notificationChannels) {
        val nosso = Canal.entries.any {
            existente.id == it.base || existente.id.startsWith("${it.base}_")
        }
        if (nosso && existente.id !in atuais) gerente.deleteNotificationChannel(existente.id)
    }
}

/**
 * O sistema deixa este app avisar?
 *
 * Cobre os dois jeitos de estar impedido de uma vez: a permissao
 * `POST_NOTIFICATIONS` negada (Android 13 em diante) e os avisos desligados a
 * mao nos ajustes -- no Android 13+ a primeira ja faz esta devolver `false`.
 *
 * Sem a checagem, `notify` nao lanca: ele e ENGOLIDO em silencio. Um lembrete
 * que nunca chega e que nao deixa rastro e pior do que um erro.
 */
fun podeAvisar(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * Posta um aviso.
 *
 * [id] identifica o aviso: postar de novo com o mesmo id SUBSTITUI o anterior
 * em vez de empilhar. E o que se quer -- dois "hora de beber agua" na barra nao
 * dizem mais do que um.
 */
fun avisar(
    context: Context,
    canal: Canal,
    som: Som,
    id: Int,
    titulo: String,
    texto: String,
    rota: String,
    resumoPublico: String? = null,
    /** Sobrepoe a [Canal.visibilidade] padrao. Ver o cartao de avisos do perfil. */
    visibilidade: Int = canal.visibilidade,
) {
    if (!podeAvisar(context)) return
    garantirCanais(context, som)

    val aviso = montar(context, canal, som, id, titulo, texto, rota)
        .setVisibility(visibilidade)
        // A versao que aparece no lugar da outra quando a tela bloqueada esta
        // escondendo conteudo. So os avisos de canal PRIVATE precisam dela; nos
        // outros o Android nem olha.
        .apply {
            if (resumoPublico != null) {
                setPublicVersion(
                    montar(context, canal, som, id, TITULO_GENERICO, resumoPublico, rota)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .build(),
                )
            }
        }
        .build()

    NotificationManagerCompat.from(context).notify(id, aviso)
}

/**
 * Tira um aviso da barra.
 *
 * Existe porque **aviso velho na barra cala o proximo** -- ver
 * [TEMPO_NA_BARRA]. Quando a pessoa resolve o assunto dentro do app (bebeu o
 * copo, começou outro pomodoro), o aviso daquele assunto passou a descrever uma
 * coisa que nao e mais verdade, e sair da barra e o certo mesmo sem o efeito
 * colateral do som.
 */
fun desavisar(context: Context, id: Int) {
    NotificationManagerCompat.from(context).cancel(id)
}

/**
 * Quanto tempo um aviso fica na barra antes de sumir sozinho.
 *
 * **Isto nao e enfeite: sem ele os avisos deste app chegam MUDOS.** Do Android
 * 16 em diante o sistema junta os avisos de um mesmo app num pacote ("Event
 * Beazeth · 3"), e o que entra no pacote nasce com a marca de silencioso. Basta
 * um segundo aviso na barra para o primeiro par ja ser empacotado -- e como
 * nada aqui tirava os avisos de la, bastava passar o dia com o app instalado
 * para tudo virar silencio. Foi assim, e nao por causa do som ou do canal, que
 * "nenhuma notificacao faz barulho" apareceu.
 *
 * Meia hora tambem e o tempo certo pelo que o aviso E: um lembrete e um
 * instante, nao um item de lista. Quem nao viu "hora de beber agua" em trinta
 * minutos nao precisa ve-lo empilhado com o das 10, o das 11 e o das 12 -- e o
 * proprio app mostra o estado a qualquer momento.
 */
private const val TEMPO_NA_BARRA = 30 * 60 * 1000L

/**
 * O corpo comum de um aviso.
 *
 * Existe porque o aviso e a versao publica dele sao o MESMO cartao com outro
 * texto -- mesmo icone, mesma cor, mesmo destino ao tocar. Montar os dois na
 * mao deixaria os dois saindo de sintonia na primeira mudanca de estilo.
 */
private fun montar(
    context: Context,
    canal: Canal,
    som: Som,
    id: Int,
    titulo: String,
    texto: String,
    rota: String,
): NotificationCompat.Builder =
    NotificationCompat.Builder(context, canal.idCom(som))
        .setSmallIcon(R.drawable.ic_aviso)
        .setColor(COR_DA_MARCA)
        .setContentTitle(titulo)
        .setContentText(texto)
        // Sem isto o texto e cortado numa linha so. O aviso de evento leva o
        // nome, o quando e a descricao -- e a descricao e justamente o que nao
        // cabe.
        .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
        .setContentIntent(aoTocar(context, id, rota))
        .setVisibility(canal.visibilidade)
        .setAutoCancel(true)
        .setTimeoutAfter(TEMPO_NA_BARRA)

/** O que a tela bloqueada mostra no lugar de um evento, quando esconde. */
private const val TITULO_GENERICO = "Lembrete da agenda 💗"

/**
 * O toque no aviso abre o app JA na tela do assunto.
 *
 * O `requestCode` e o id do aviso, e nao zero, por um detalhe que custa caro:
 * **dois `PendingIntent` sao "o mesmo" para o Android quando so os extras
 * diferem.** Com o codigo fixo, o aviso de agua e o de evento disputariam um
 * `PendingIntent` so, e o `FLAG_UPDATE_CURRENT` faria o ultimo postado reescrever
 * o destino do outro -- tocar no lembrete da prova abriria a tela de agua.
 *
 * `SINGLE_TOP` mais o `onNewIntent` da activity: quem ja esta com o app aberto
 * troca de tela, em vez de ganhar uma segunda copia na pilha.
 */
private fun aoTocar(context: Context, id: Int, rota: String): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_DESTINO, rota)
    }
    return PendingIntent.getActivity(
        context,
        id,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * O rosa da paleta padrao, para o Android tingir o iconezinho do aviso.
 *
 * Fixo, e nao a cor do tema escolhido: a paleta mora no DataStore, e ler disco
 * para pintar um icone de 24 dp nao se paga. Quem trocou para o roxo ve o aviso
 * rosa -- e o aviso nao esta dentro do app, esta na barra do sistema, onde nada
 * mais segue o tema do app de qualquer forma.
 */
private const val COR_DA_MARCA = 0xFFFF78B2.toInt()
