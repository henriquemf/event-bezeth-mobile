package com.beazeth.notifier.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra o token de acesso com uma chave que nunca sai do Android Keystore.
 *
 * O DataStore mora no armazenamento privado do app, e isso ja barra outro app.
 * Nao barra quem le o disco por fora: aparelho com root, imagem forense, ou uma
 * build de debug, que o `adb shell run-as` abre sem root nenhum. Nesses casos o
 * token em texto claro era a conta inteira por noventa dias. Cifrado, o arquivo
 * sozinho nao serve: a chave fica no Keystore (no chip, onde houver) e so este
 * app, neste aparelho, consegue usa-la.
 *
 * AES-GCM direto e nao `EncryptedSharedPreferences`: a biblioteca
 * security-crypto foi descontinuada, e isto e uma chave e duas funcoes.
 *
 * Toda falha devolve `null` em vez de levantar. Chave invalidada (o Android
 * apaga as do Keystore em alguns resets de seguranca) vira "sem token", ou
 * seja, pedir o login de novo -- nunca um app que fecha sozinho na abertura.
 */
internal object Cofre {

    private const val PROVEDOR = "AndroidKeyStore"
    private const val APELIDO = "event-beazeth-sessao"
    private const val TRANSFORMACAO = "AES/GCM/NoPadding"
    private const val BITS_DA_ETIQUETA = 128

    /** O ultimo texto decifrado, para o token nao ir ao Keystore a cada leitura:
     *  a fila e o sincronizador o pedem a cada escrita, e cada ida e uma
     *  chamada entre processos. */
    @Volatile
    private var ultimo: Pair<String, String>? = null

    private fun chave(): SecretKey {
        val cofre = KeyStore.getInstance(PROVEDOR).apply { load(null) }
        (cofre.getKey(APELIDO, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEDOR).apply {
            init(
                KeyGenParameterSpec.Builder(
                    APELIDO,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    /** `iv + cifrado` em Base64, ou `null` se o Keystore recusar. */
    fun cifrar(texto: String): String? = runCatching {
        val cifra = Cipher.getInstance(TRANSFORMACAO).apply { init(Cipher.ENCRYPT_MODE, chave()) }
        val corpo = cifra.doFinal(texto.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cifra.iv + corpo, Base64.NO_WRAP)
    }.getOrNull()

    fun decifrar(guardado: String): String? {
        ultimo?.let { (de, para) -> if (de == guardado) return para }
        return runCatching {
            val bytes = Base64.decode(guardado, Base64.NO_WRAP)
            // O GCM do Keystore sempre gera IV de 12 bytes.
            val iv = bytes.copyOfRange(0, 12)
            val cifra = Cipher.getInstance(TRANSFORMACAO).apply {
                init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(BITS_DA_ETIQUETA, iv))
            }
            String(cifra.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        }.getOrNull()?.also { ultimo = guardado to it }
    }
}
