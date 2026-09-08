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
 * `res/raw/aviso_suave.wav` e um sino curto, de ataque macio, gravado com pico
 * em 30% da escala -- ou seja, ele nasce baixo, e nao so parece baixo. Sendo um
 * arquivo do app, soa igual em qualquer aparelho.
 *
 * ## O que esta escrito aqui vale SO NA CRIACAO do canal
 *
 * Importancia, som e vibracao sao copiados uma unica vez, quando o canal nasce.
 * Dali em diante quem manda e a pessoa, nos ajustes do sistema, e o Android
 * IGNORA o que o codigo pedir -- mudar um valor deste arquivo nao mexe em quem
 * ja instalou. E o comportamento certo (ninguem quer uma atualizacao religando
 * um som que desligou a mao), mas tem um preco a lembrar: para trocar o toque
 * padrao de verdade seria preciso criar canais com IDs NOVOS e apagar os
 * velhos. Nenhuma versao publicada ate hoje tinha canal nenhum, entao estes
 * aqui sao os primeiros e ainda estao livres.
 */
enum class Canal(
    val id: String,
    val titulo: String,
    val descricao: String,
    val importancia: Int,
    val vibra: Boolean,
) {
    AGUA(
        id = "agua",
        titulo = "Beber água",
        descricao = "No intervalo e na janela escolhidos na tela de hidratação.",
        // DEFAULT e nao HIGH: e um empurraozinho de rotina. Aparece na barra e
        // faz um som, mas nao toma a tela de quem esta no meio de outra coisa.
        // Sem vibracao pelo mesmo motivo -- este e o aviso que mais se repete
        // no dia, e o que mais cansa se insistir.
        importancia = NotificationManager.IMPORTANCE_DEFAULT,
        vibra = false,
    ),
    POMODORO(
        id = "pomodoro",
        titulo = "Pomodoro",
        descricao = "Quando a contagem chega ao fim.",
        // HIGH: quem pos um cronometro quer saber na hora, e este e o unico
        // aviso que a pessoa pediu explicitamente, minutos antes, apertando um
        // botao. HIGH e sobre APARECER na frente, nao sobre volume -- o som e o
        // mesmo sino baixo dos outros.
        importancia = NotificationManager.IMPORTANCE_HIGH,
        vibra = true,
    ),
    EVENTOS(
        id = "eventos",
        titulo = "Agenda",
        descricao = "Os lembretes dos eventos do calendário.",
        importancia = NotificationManager.IMPORTANCE_HIGH,
        vibra = true,
    ),
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
fun garantirCanais(context: Context) {
    val gerente = context.getSystemService(NotificationManager::class.java) ?: return
    val toque = Uri.parse("android.resource://${context.packageName}/${R.raw.aviso_suave}")
    val comoTocar = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    for (canal in Canal.entries) {
        gerente.createNotificationChannel(
            NotificationChannel(canal.id, canal.titulo, canal.importancia).apply {
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
    id: Int,
    titulo: String,
    texto: String,
    rota: String,
) {
    if (!podeAvisar(context)) return
    garantirCanais(context)

    val aviso = NotificationCompat.Builder(context, canal.id)
        .setSmallIcon(R.drawable.ic_aviso)
        .setColor(COR_DA_MARCA)
        .setContentTitle(titulo)
        .setContentText(texto)
        // Sem isto o texto e cortado numa linha so. O aviso de evento leva o
        // nome, o quando e a descricao -- e a descricao e justamente o que nao
        // cabe.
        .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
        .setContentIntent(aoTocar(context, id, rota))
        .setAutoCancel(true)
        .build()

    NotificationManagerCompat.from(context).notify(id, aviso)
}

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
