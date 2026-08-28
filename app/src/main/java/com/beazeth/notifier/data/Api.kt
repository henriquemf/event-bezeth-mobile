package com.beazeth.notifier.data

import com.beazeth.notifier.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente da API do event-beazeth.
 *
 * O contrato que ele segue esta em `event-beazeth/app/api_contract.py`, e um
 * teste no servidor falha se as duas pontas divergirem -- inclusive quando uma
 * rota nova aparece la e nao e publicada para ca.
 *
 * `ignoreUnknownKeys` esta ligado de proposito: campo novo na resposta nao pode
 * derrubar uma versao antiga do app instalada no celular de alguem. O contrario
 * -- campo que some -- e o que o teste do servidor existe para pegar.
 */
object Api {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val http = OkHttpClient.Builder()
        // O plano gratuito do Render desliga o servico por inatividade, e a
        // primeira requisicao depois disso espera o container subir. Trinta
        // segundos evita que a tela de login falhe justamente na primeira vez
        // que alguem abre o app no dia.
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /** Resultado de uma chamada: ou o corpo, ou o motivo de ter falhado. */
    sealed interface Resultado<out T> {
        data class Ok<T>(val corpo: T) : Resultado<T>
        /** `semSessao` separa "seu token venceu" de "a rede caiu". */
        data class Erro(val mensagem: String, val semSessao: Boolean = false) : Resultado<Nothing>
    }

    @Serializable
    data class Conta(
        val id: Long,
        val email: String,
        @SerialName("displayName") val nome: String,
    )

    @Serializable
    data class RespostaLogin(
        val ok: Boolean = false,
        val token: String? = null,
        val user: Conta? = null,
        val message: String? = null,
    )

    @Serializable
    data class RespostaConta(
        val ok: Boolean = false,
        val user: Conta? = null,
        val message: String? = null,
    )

    suspend fun entrar(email: String, senha: String): Resultado<RespostaLogin> =
        chamar(
            caminho = "/api/auth/login",
            metodo = "POST",
            corpo = json.encodeToString(
                MapaSimples.serializer(),
                MapaSimples(email = email, password = senha),
            ),
            desserializar = { json.decodeFromString(RespostaLogin.serializer(), it) },
        )

    /**
     * Cria a conta e ja devolve o token: quem acabou de se cadastrar entra
     * direto, sem passar pela tela de login de novo.
     *
     * A validacao (nome, e-mail, senha minima, e-mail repetido) e do servidor,
     * e a mensagem dele e a que a tela mostra. Duplicar essas regras aqui seria
     * duas verdades sobre a mesma coisa -- e a do cliente envelheceria primeiro.
     */
    suspend fun criarConta(nome: String, email: String, senha: String): Resultado<RespostaLogin> =
        chamar(
            caminho = "/api/auth/signup",
            metodo = "POST",
            corpo = json.encodeToString(
                NovaConta.serializer(),
                NovaConta(nome = nome, email = email, password = senha),
            ),
            desserializar = { json.decodeFromString(RespostaLogin.serializer(), it) },
        )

    suspend fun quemSou(token: String): Resultado<RespostaConta> =
        chamar(
            caminho = "/api/me",
            token = token,
            desserializar = { json.decodeFromString(RespostaConta.serializer(), it) },
        )

    /**
     * O que mudou desde [desde].
     *
     * `desde` nulo na primeira vez: o servidor entao devolve tudo o que a conta
     * ja tinha, porque o piso dele e 1970 -- o mesmo valor que a migracao pos
     * como padrao nas colunas de carimbo.
     */
    suspend fun sincronizar(token: String, desde: String?): Resultado<RespostaSync> {
        val consulta = if (desde.isNullOrBlank()) "" else "?since=" + urlEncode(desde)
        return chamar(
            caminho = "/api/sync$consulta",
            token = token,
            desserializar = { json.decodeFromString(RespostaSync.serializer(), it) },
        )
    }

    /**
     * Uma escrita qualquer, para a fila de pendencias drenar.
     *
     * Devolve o corpo cru: quem chamou sabe o que espera de volta, e uma fila
     * generica nao tem como saber. Assim uma tela nova nao precisa de um metodo
     * novo aqui.
     */
    suspend fun escrever(
        token: String,
        metodo: String,
        caminho: String,
        corpo: String?,
    ): Resultado<String> = chamar(
        caminho = caminho,
        metodo = metodo,
        corpo = corpo,
        token = token,
        desserializar = { it },
    )

    /** Corpo do login. Uma `data class` em vez de montar JSON com texto: uma
     *  senha com aspas ou barra invertida quebraria a string na mao. */
    @Serializable
    private data class MapaSimples(val email: String, val password: String)

    /** `displayName` e o nome do campo no servidor; `nome` e o daqui. */
    @Serializable
    private data class NovaConta(
        @SerialName("displayName") val nome: String,
        val email: String,
        val password: String,
    )

    private fun urlEncode(valor: String): String =
        java.net.URLEncoder.encode(valor, "UTF-8")

    private suspend fun <T> chamar(
        caminho: String,
        metodo: String = "GET",
        corpo: String? = null,
        token: String? = null,
        desserializar: (String) -> T,
    ): Resultado<T> = withContext(Dispatchers.IO) {
        // DELETE e PATCH sem corpo precisam de um corpo vazio, e nao de `null`:
        // o OkHttp recusa `null` em metodos que permitem corpo.
        val carga: RequestBody? = when {
            corpo != null -> corpo.toRequestBody(JSON_TYPE)
            metodo in setOf("POST", "PUT", "PATCH") -> "".toRequestBody(JSON_TYPE)
            else -> null
        }

        val pedido = Request.Builder()
            .url(BuildConfig.API_BASE + caminho)
            .method(metodo, carga)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

        try {
            http.newCall(pedido).execute().use { resposta ->
                val texto = resposta.body?.string().orEmpty()

                if (resposta.code == 401) {
                    return@withContext Resultado.Erro(
                        mensagem = mensagemDoCorpo(texto) ?: "Sua sessão expirou.",
                        semSessao = true,
                    )
                }
                if (!resposta.isSuccessful) {
                    return@withContext Resultado.Erro(
                        mensagemDoCorpo(texto) ?: "O servidor respondeu ${resposta.code}.",
                    )
                }
                Resultado.Ok(desserializar(texto))
            }
        } catch (e: IOException) {
            // Sem internet, DNS fora, servidor dormindo. A mensagem e para a
            // pessoa, nao para o log: ela nao pode fazer nada com um stack trace.
            Resultado.Erro("Não foi possível falar com o servidor. Verifique a conexão.")
        } catch (e: Exception) {
            Resultado.Erro("Resposta inesperada do servidor.")
        }
    }

    /** Extrai o `message` do JSON de erro, se houver um. */
    private fun mensagemDoCorpo(texto: String): String? = runCatching {
        json.decodeFromString(RespostaSimples.serializer(), texto).message
    }.getOrNull()
}
