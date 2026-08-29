package com.beazeth.notifier.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A foto e o nome do perfil.
 *
 * ## Por que a foto fica NO APARELHO, e nao na conta
 *
 * O servidor nao tem onde guardar imagem: o banco e um Postgres de plano
 * gratuito e o disco do Render e efemero -- some a cada deploy. Guardar a foto
 * la significaria uma coluna de bytes num banco cobrado por byte, ou um
 * servico de arquivos novo, para um app de duas pessoas.
 *
 * O preco de ficar aqui e honesto e pequeno: reinstalar o app pede a foto de
 * novo. Nome e e-mail, esses sim, vivem na conta -- sao o que identifica quem
 * esta falando, e o site tambem os mostra.
 *
 * ## Por que uma VERSAO, e nao o caminho do arquivo
 *
 * O caminho nunca muda (`perfil.jpg`, sempre). Se a interface se inscrevesse
 * nele, trocar a foto nao mudaria nada observavel e a tela continuaria com a
 * imagem velha na tela -- ate alguem sair e voltar. O contador sobe a cada
 * gravacao, e e ele que faz a foto nova aparecer na hora, nos tres lugares que
 * a mostram.
 */
private val Context.prefsPerfil by preferencesDataStore(name = "perfil")

class Perfil(private val context: Context) {

    private val chaveVersaoDaFoto = longPreferencesKey("foto_versao")
    private val chaveNomeLocal = stringPreferencesKey("nome_local")

    /** Sobe a cada foto gravada. Zero significa "nenhuma foto escolhida". */
    val versaoDaFoto: Flow<Long> =
        context.prefsPerfil.data.map { it[chaveVersaoDaFoto] ?: 0L }

    /**
     * O nome de quem usa o app sem conta.
     *
     * So vale no modo local: com conta, o nome e o `display_name` do servidor,
     * que o site tambem mostra. Ter dois nomes para a mesma pessoa faria a
     * lateral discordar do site sem nenhum jeito de saber qual esta certo.
     */
    val nomeLocal: Flow<String> =
        context.prefsPerfil.data.map { it[chaveNomeLocal] ?: "" }

    suspend fun definirNomeLocal(nome: String) {
        context.prefsPerfil.edit { it[chaveNomeLocal] = nome.trim().take(MAX_NOME) }
    }

    /** O arquivo da foto. Pode nao existir -- ver [versaoDaFoto]. */
    fun arquivo(): File = File(context.filesDir, ARQUIVO)

    /**
     * Le a imagem escolhida, corta no quadrado do meio, encolhe e grava.
     *
     * Tudo isso porque o que sai da galeria e uma foto de camera de verdade: 12
     * megapixels, 4 MB, as vezes deitada. Guardar como veio custaria meio
     * segundo de decodificacao a cada abertura da lateral, e ela e desenhada num
     * circulo de 44 dp.
     *
     * Devolve `false` quando o arquivo escolhido nao e uma imagem legivel --
     * acontece com atalho de nuvem que aponta para um arquivo ainda nao baixado.
     */
    suspend fun salvarFoto(origem: Uri): Boolean = withContext(Dispatchers.IO) {
        val bruta = decodificarEncolhida(origem) ?: return@withContext false
        val quadrada = cortarNoQuadrado(bruta)
        val girada = corrigirGiro(origem, quadrada)

        arquivo().outputStream().use { saida ->
            girada.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, saida)
        }

        // Os intermediarios podem ser o MESMO objeto (cortar e girar devolvem a
        // entrada quando nao ha o que fazer), entao reciclar cada um por sua
        // conta chamaria `recycle` duas vezes no mesmo bitmap.
        for (b in setOf(bruta, quadrada, girada)) b.recycle()

        cache = null
        context.prefsPerfil.edit {
            it[chaveVersaoDaFoto] = (it[chaveVersaoDaFoto] ?: 0L) + 1
        }
        true
    }

    suspend fun removerFoto() {
        withContext(Dispatchers.IO) { arquivo().delete() }
        cache = null
        context.prefsPerfil.edit { it[chaveVersaoDaFoto] = 0L }
    }

    /**
     * A foto pronta para desenhar, ou `null` se nao houver.
     *
     * O cache existe porque tres lugares mostram a mesma foto (lateral, barra de
     * cima e a tela de perfil) e cada recomposicao pediria o disco de novo. A
     * chave e a versao: gravar uma foto nova invalida sozinho.
     */
    suspend fun foto(): ImageBitmap? {
        val versao = versaoDaFoto.first()
        if (versao == 0L) return null

        cache?.let { (v, imagem) -> if (v == versao) return imagem }

        return withContext(Dispatchers.IO) {
            val arquivo = arquivo()
            if (!arquivo.exists()) return@withContext null
            BitmapFactory.decodeFile(arquivo.path)?.asImageBitmap()?.also {
                cache = versao to it
            }
        }
    }

    // ------------------------------------------------------------- os detalhes

    /**
     * Decodifica ja reduzida, sem nunca ter a foto inteira na memoria.
     *
     * A primeira passada le SO o cabecalho (`inJustDecodeBounds`), que da o
     * tamanho sem alocar os pixels; a segunda le de verdade, pulando de 2 em 2,
     * 4 em 4... ate caber. Decodificar inteiro para depois encolher e o caminho
     * classico para o `OutOfMemoryError` com foto de celular moderno.
     */
    private fun decodificarEncolhida(origem: Uri): Bitmap? {
        val medida = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        abrir(origem)?.use { BitmapFactory.decodeStream(it, null, medida) }

        val maior = maxOf(medida.outWidth, medida.outHeight)
        if (maior <= 0) return null

        val opcoes = BitmapFactory.Options().apply {
            var passo = 1
            while (maior / passo > LADO * 2) passo *= 2
            inSampleSize = passo
        }
        return abrir(origem)?.use { BitmapFactory.decodeStream(it, null, opcoes) }
    }

    /** O quadrado do meio, depois reduzido ao lado final. */
    private fun cortarNoQuadrado(bitmap: Bitmap): Bitmap {
        val lado = minOf(bitmap.width, bitmap.height)
        val cortada = Bitmap.createBitmap(
            bitmap,
            (bitmap.width - lado) / 2,
            (bitmap.height - lado) / 2,
            lado,
            lado,
        )
        if (lado <= LADO) return cortada

        val menor = Bitmap.createScaledBitmap(cortada, LADO, LADO, true)
        if (menor !== cortada) cortada.recycle()
        return menor
    }

    /**
     * Desfaz o giro que a camera anotou no EXIF em vez de aplicar aos pixels.
     *
     * Foto tirada com o aparelho de lado sai com os pixels na horizontal e uma
     * etiqueta dizendo "gire 90". A galeria le a etiqueta; o `BitmapFactory`
     * nao. Sem isto, metade das fotos entra deitada -- e num circulo de 44 dp
     * fica um rosto de lado, que parece defeito da tela.
     */
    private fun corrigirGiro(origem: Uri, bitmap: Bitmap): Bitmap {
        val etiqueta = runCatching {
            abrir(origem)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: return bitmap

        val graus = when (etiqueta) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }

        val virada = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(graus) },
            true,
        )
        if (virada !== bitmap) bitmap.recycle()
        return virada
    }

    private fun abrir(origem: Uri) = runCatching {
        context.contentResolver.openInputStream(origem)
    }.getOrNull()

    companion object {
        private const val ARQUIVO = "perfil.jpg"

        /**
         * 512 px de lado.
         *
         * O maior uso na tela e o circulo de 96 dp da tela de perfil, que num
         * tablet de densidade 2 da 192 px. 512 deixa margem para um aparelho
         * mais denso e ainda cabe em 40 KB de JPEG.
         */
        private const val LADO = 512
        private const val QUALIDADE = 88

        /** O mesmo teto do `display_name` no servidor (`MAX_NAME_LENGTH`). */
        const val MAX_NOME = 40

        @Volatile
        private var cache: Pair<Long, ImageBitmap>? = null
    }
}
