package com.beazeth.notifier.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Onde o token de acesso mora entre uma abertura e outra do app.
 *
 * DataStore e nao SharedPreferences: a leitura e assincrona e nao trava a
 * thread da interface. Parece detalhe, mas esta leitura acontece na abertura,
 * exatamente quando a primeira tela esta sendo montada.
 *
 * O token vale noventa dias e nao da acesso a nada alem desta conta. Guardar em
 * texto claro aqui e o mesmo nivel de protecao do cookie de sessao no
 * navegador: quem tiver o aparelho desbloqueado ja esta dentro do app de
 * qualquer forma. Se um dia isto guardar algo mais sensivel, o lugar passa a
 * ser o EncryptedSharedPreferences.
 */
private val Context.dataStore by preferencesDataStore(name = "sessao")

class TokenStore(private val context: Context) {

    private val chaveToken = stringPreferencesKey("token")
    private val chaveNome = stringPreferencesKey("nome")
    private val chaveSync = stringPreferencesKey("ultima_sync")
    private val chaveLocal = booleanPreferencesKey("modo_local")

    /**
     * A pessoa escolheu usar o app sem conta.
     *
     * Mora aqui, e nao numa variavel de tela, porque e uma ESCOLHA e nao um
     * estado passageiro: quem abriu o app sem conta ontem espera abrir sem
     * conta hoje, sem a tela de login no caminho.
     *
     * Token e modo local se excluem por definicao, e por isso [guardar] apaga
     * esta marca: nao existe "entrou mas continua local".
     */
    val modoLocal: Flow<Boolean> = context.dataStore.data.map { it[chaveLocal] ?: false }

    suspend fun modoLocalAtivo(): Boolean = modoLocal.first()

    suspend fun ligarModoLocal() {
        context.dataStore.edit { it[chaveLocal] = true }
    }

    /** O token guardado, ou `null` se ninguem entrou ainda. */
    val token: Flow<String?> = context.dataStore.data.map { it[chaveToken] }

    /** Nome de quem entrou, para a interface saudar sem esperar a rede. */
    val nome: Flow<String?> = context.dataStore.data.map { it[chaveNome] }

    suspend fun tokenAtual(): String? = token.first()

    suspend fun nomeAtual(): String? = nome.first()

    /**
     * O instante da ultima conversa bem-sucedida com `/api/sync`.
     *
     * Vem do RELOGIO DO BANCO, devolvido pelo proprio servidor -- nunca do
     * relogio do celular. Se fosse do celular, qualquer diferenca entre os
     * dois viraria linha perdida na proxima consulta: a falha mais
     * silenciosa que existe, porque so aparece no dado que ficou para tras.
     *
     * `null` significa "nunca sincronizou", e o servidor entao manda tudo.
     */
    val ultimaSync: Flow<String?> = context.dataStore.data.map { it[chaveSync] }

    suspend fun ultimaSyncAtual(): String? = ultimaSync.first()

    suspend fun guardarUltimaSync(instante: String) {
        context.dataStore.edit { it[chaveSync] = instante }
    }

    suspend fun guardar(token: String, nome: String) {
        context.dataStore.edit {
            it[chaveToken] = token
            it[chaveNome] = nome
            // Entrar numa conta encerra o modo local, sempre. Deixar a marca
            // faria a fila continuar desligada com um token valido no bolso --
            // o app pareceria funcionar e nada subiria.
            it.remove(chaveLocal)
        }
    }

    /**
     * Apaga a sessao.
     *
     * Chamado ao sair e tambem quando a API responde 401: um token vencido no
     * armazenamento nao serve para nada e faria toda abertura seguinte comecar
     * por uma requisicao fadada a falhar.
     */
    suspend fun limpar() {
        context.dataStore.edit { it.clear() }
    }
}
