package com.beazeth.notifier.ui.flutuante

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.beazeth.notifier.MainActivity
import com.beazeth.notifier.avisos.EXTRA_DESTINO
import com.beazeth.notifier.data.Preferencias
import com.beazeth.notifier.data.Remapeamentos
import com.beazeth.notifier.data.Sincronizador
import com.beazeth.notifier.data.lerTextoRico
import com.beazeth.notifier.data.local.BancoLocal
import com.beazeth.notifier.data.local.NotaEntity
import com.beazeth.notifier.ui.componentes.Destino
import com.beazeth.notifier.ui.telas.postits.COR_DO_LINK
import com.beazeth.notifier.ui.telas.postits.TINTA_DO_PAPEL
import com.beazeth.notifier.ui.telas.postits.anotado
import com.beazeth.notifier.ui.telas.postits.misturarComBranco
import com.beazeth.notifier.ui.telas.postits.tomDe
import com.beazeth.notifier.ui.theme.FONTE_PADRAO
import com.beazeth.notifier.ui.theme.PALETA_PADRAO
import com.beazeth.notifier.ui.theme.TemaBeazeth
import com.beazeth.notifier.ui.theme.TipografiaBeazeth
import kotlinx.coroutines.launch

/**
 * Um post-it em picture-in-picture: a janelinha que fica por cima de qualquer
 * app, e o equivalente do botao ⧉ do site (`js/pages/notes/pip.js`).
 *
 * ## Uma activity propria, numa tarefa propria
 *
 * O picture-in-picture do Android e da ACTIVITY: quem entra nele encolhe
 * inteira. Se fosse a `MainActivity`, o app todo viraria a janelinha e nao
 * haveria mais app para usar por baixo. Esta vive noutra tarefa
 * (`taskAffinity` propria no manifesto): o post-it flutua e o app continua ali,
 * inteiro. `singleTask` porque o sistema so mostra um PiP por vez -- pedir
 * outro post-it troca o desta janela (`onNewIntent`), em vez de empilhar.
 *
 * ## So mostra; quem edita e o app
 *
 * Janela de PiP nao recebe toque nem teclado: tocar nela mostra os controles
 * do sistema (ampliar, fechar). Por isso aqui nao ha editor -- ha o papel,
 * formatado, lido do Room ao vivo. Uma edicao feita no app, ou vinda do site
 * pela sincronizacao, aparece na janelinha sem ninguem fazer nada. AMPLIAR
 * abre o app no quadro de post-its: quem quer o papel grande quer escrever.
 *
 * ## O id pode trocar debaixo da janela
 *
 * Um post-it recem-criado tem id provisorio (negativo) ate o servidor emitir o
 * de verdade, e a linha provisoria e APAGADA na troca. Sem seguir
 * [Remapeamentos], a janela de um post-it novo mostraria "apagado" um segundo
 * depois de abrir.
 */
class PostitFlutuante : ComponentActivity() {

    private var notaId by mutableLongStateOf(0L)
    private var jaFlutuou = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notaId = intent.getLongExtra(EXTRA_NOTA, 0L)

        lifecycleScope.launch {
            Remapeamentos.fluxo.collect { troca ->
                if (troca.entidade == Sincronizador.ALVO_NOTAS && troca.de == notaId) notaId = troca.para
            }
        }

        setContent {
            val prefs = remember { Preferencias(applicationContext) }
            val tema by prefs.tema.collectAsStateWithLifecycle(PALETA_PADRAO)
            val fonte by prefs.fonte.collectAsStateWithLifecycle(FONTE_PADRAO)
            val escuro by prefs.escuro.collectAsStateWithLifecycle(false)
            val dao = remember { BancoLocal.obter(applicationContext).notas() }
            val nota by remember(notaId) { dao.observarUma(notaId) }.collectAsStateWithLifecycle(null)

            TemaBeazeth(tema = tema, fonte = fonte, escuro = escuro) {
                PapelFlutuante(nota)
            }
        }
    }

    /**
     * Entra na janelinha aqui, e nao no `onCreate`: o Android so aceita o
     * pedido de uma activity ja visivel, e antes disso ele e recusado com
     * excecao.
     */
    override fun onResume() {
        super.onResume()
        if (jaFlutuou) return
        jaFlutuou = true
        lifecycleScope.launch {
            val nota = BancoLocal.obter(applicationContext).notas().buscar(notaId)
            val entrou = runCatching { enterPictureInPictureMode(parametros(nota)) }.getOrDefault(false)
            if (!entrou) {
                // O sistema pode ter o PiP desligado para este app nos ajustes.
                // Ficar aberta em tela cheia seria uma tela que ninguem pediu.
                Toast.makeText(
                    this@PostitFlutuante,
                    "O picture-in-picture está desligado para o app nos ajustes do Android.",
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        notaId = intent.getLongExtra(EXTRA_NOTA, notaId)
        lifecycleScope.launch {
            val nota = BancoLocal.obter(applicationContext).notas().buscar(notaId)
            runCatching { setPictureInPictureParams(parametros(nota)) }
        }
    }

    override fun onPictureInPictureModeChanged(noPip: Boolean, novaConfiguracao: Configuration) {
        super.onPictureInPictureModeChanged(noPip, novaConfiguracao)
        if (noPip) return
        // Saiu da janelinha. Visivel ainda, foi AMPLIAR; parada, foi fechar.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(EXTRA_DESTINO, Destino.POSTITS.rota),
            )
        }
        finish()
    }

    /** Fechar a janelinha (o X, ou arrastar para fora) para a activity: acabou. */
    override fun onStop() {
        super.onStop()
        if (jaFlutuou && !isChangingConfigurations) finish()
    }

    /**
     * A janela no formato do papel. O Android recusa proporcao mais esticada
     * que 2,39:1 para qualquer lado, entao o formato e aparado a isso.
     */
    private fun parametros(nota: NotaEntity?): PictureInPictureParams {
        val largura = (nota?.width ?: 224).coerceAtLeast(1)
        val altura = (nota?.height ?: 208).coerceAtLeast(1)
        val proporcao = (largura.toFloat() / altura).coerceIn(0.42f, 2.38f)
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((proporcao * 1000).toInt(), 1000))
            .build()
    }

    companion object {
        const val EXTRA_NOTA = "nota"

        /** O aparelho sabe fazer picture-in-picture? Sem isso o botao nem aparece. */
        fun disponivel(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

        fun abrir(context: Context, notaId: Long) {
            context.startActivity(Intent(context, PostitFlutuante::class.java).putExtra(EXTRA_NOTA, notaId))
        }
    }
}

/** O papel, como no quadro -- a mesma cor, o mesmo degrade, a mesma tinta. */
@Composable
private fun PapelFlutuante(nota: NotaEntity?) {
    val tom = tomDe(nota?.color ?: "sun")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to misturarComBranco(tom, 0.74f),
                    0.58f to misturarComBranco(tom, 0.90f),
                    1f to tom,
                ),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        val lido = remember(nota?.content) { lerTextoRico(nota?.content.orEmpty()) }
        Text(
            text = when {
                nota == null -> AnnotatedString("Este post-it foi apagado.")
                lido.texto.isBlank() -> AnnotatedString("Post-it em branco")
                else -> lido.anotado(COR_DO_LINK, comLinks = false)
            },
            color = TINTA_DO_PAPEL,
            style = TipografiaBeazeth.bodyLarge.copy(fontSize = 14.sp, lineHeight = 19.sp),
        )
    }
}
