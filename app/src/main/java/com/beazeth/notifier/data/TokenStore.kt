package com.beazeth.notifier.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Onde o token de acesso mora entre uma abertura e outra do app.
 *
 * DataStore e nao SharedPreferences: a leitura e assincrona e nao trava a
 * thread da interface. Parece detalhe, mas esta leitura acontece na abertura,
 * exatamente quando a primeira tela esta sendo montada.
 *
 * O token vale noventa dias e abre a conta inteira, por isso vai CIFRADO, com
 * uma chave do Android Keystore -- ver [Cofre]. Nome e e-mail ficam em claro:
 * a lateral os mostra, e sem o token eles nao abrem nada.
 */
private val Context.dataStore by preferencesDataStore(name = "sessao")

class TokenStore(private val context: Context) {

    /** O token de antes do [Cofre], em texto claro. So e lido para migrar. */
    private val chaveTokenEmClaro = stringPreferencesKey("token")
    private val chaveToken = stringPreferencesKey("token_cifrado")

    /**
     * De quem sao os dados que estao no banco do aparelho.
     *
     * Sobrevive a queda da sessao de proposito (ver [sessaoCaiu]): e com ele que
     * o proximo login decide se o que esta no aparelho e dele -- e fica, com a
     * fila e tudo -- ou de outra conta, e sai antes de ele ver.
     */
    private val chaveConta = longPreferencesKey("conta_id")

    /**
     * O e-mail da conta dona dos dados, guardado quando a sessao cai.
     *
     * Serve a duas coisas: e o que a tela de login mostra ao avisar que ha
     * alteracoes dessa conta esperando para subir, e e a identidade dos dados
     * no aparelho que veio da versao anterior e caiu da sessao antes de saber
     * o [chaveConta].
     */
    private val chaveEmailDosDados = stringPreferencesKey("email_dos_dados")
    private val chaveNome = stringPreferencesKey("nome")
    private val chaveEmail = stringPreferencesKey("email")
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

    /**
     * O token guardado, ou `null` se ninguem entrou ainda.
     *
     * O token em claro de uma versao anterior tambem vale, para ninguem ser
     * deslogado pela atualizacao; [tokenAtual] o cifra na primeira leitura.
     */
    val token: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[chaveToken]?.let(Cofre::decifrar) ?: prefs[chaveTokenEmClaro]
    }
        // Decifrar vai ao Keystore, uma chamada entre processos: fora da
        // thread da interface, que e de onde a abertura do app pergunta.
        .flowOn(Dispatchers.IO)

    /** Nome de quem entrou, para a interface saudar sem esperar a rede. */
    val nome: Flow<String?> = context.dataStore.data.map { it[chaveNome] }

    /**
     * E-mail da conta, para a tela de perfil mostrar sem pedir a rede.
     *
     * Vem do login e e refeito a cada `/api/me`. Guardar aqui e o que permite a
     * tela abrir com o campo ja preenchido no aviao -- e ela abre offline como
     * todas as outras.
     */
    val email: Flow<String?> = context.dataStore.data.map { it[chaveEmail] }

    suspend fun tokenAtual(): String? {
        if (context.dataStore.data.first()[chaveTokenEmClaro] != null) {
            // Dentro do MESMO edit que le: entre ler e gravar em dois passos,
            // um token novo gravado no meio seria sobrescrito pelo antigo.
            context.dataStore.edit { prefs ->
                prefs[chaveTokenEmClaro]?.let { gravarToken(prefs, it) }
            }
        }
        return token.first()
    }

    /** A conta dona dos dados do aparelho: o id, ou so o e-mail, se e o que ha. */
    suspend fun contaDosDados(): Long? = context.dataStore.data.first()[chaveConta]

    suspend fun emailDosDados(): String? = context.dataStore.data.first()[chaveEmailDosDados]

    /**
     * Grava o token, cifrado. Se o Keystore recusar -- aparelho com o
     * armazenamento de chaves quebrado --, guarda como antes, em claro: perder
     * o login seria pior que o nivel de protecao de todas as versoes
     * anteriores.
     */
    private fun gravarToken(prefs: MutablePreferences, token: String) {
        val cifrado = Cofre.cifrar(token)
        if (cifrado != null) {
            prefs[chaveToken] = cifrado
            prefs.remove(chaveTokenEmClaro)
        } else {
            prefs[chaveTokenEmClaro] = token
            prefs.remove(chaveToken)
        }
    }

    /**
     * Troca so o token -- a senha foi trocada neste aparelho.
     *
     * A troca de senha derruba todo token anterior, inclusive o que fez o
     * pedido; o servidor devolve um novo na mesma resposta, e e ele que segura
     * este aparelho dentro.
     */
    suspend fun trocarToken(token: String) {
        context.dataStore.edit { gravarToken(it, token) }
    }

    suspend fun nomeAtual(): String? = nome.first()

    suspend fun emailAtual(): String? = email.first()

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

    suspend fun guardar(token: String, nome: String, email: String, contaId: Long) {
        context.dataStore.edit {
            // Outra conta: o carimbo da sincronizacao era da anterior, e com ele
            // a conta nova so baixaria o que mudou "desde entao" -- nunca o que
            // ja tinha. Sem carimbo, a primeira conversa traz tudo.
            if (it[chaveConta] != null && it[chaveConta] != contaId) it.remove(chaveSync)
            it.remove(chaveEmailDosDados)
            gravarToken(it, token)
            it[chaveConta] = contaId
            it[chaveNome] = nome
            it[chaveEmail] = email
            // Entrar numa conta encerra o modo local, sempre. Deixar a marca
            // faria a fila continuar desligada com um token valido no bolso --
            // o app pareceria funcionar e nada subiria.
            it.remove(chaveLocal)
        }
    }

    /**
     * Atualiza o que a conta mostra, sem tocar no token.
     *
     * Depois de trocar nome ou e-mail no perfil, e depois de todo `/api/me`: o
     * nome pode ter mudado no site desde o ultimo login, e a lateral mostra
     * este valor em toda tela.
     */
    suspend fun guardarConta(nome: String, email: String, contaId: Long) {
        context.dataStore.edit {
            it[chaveNome] = nome
            it[chaveEmail] = email
            // Refeito a cada `/api/me`: e assim que um aparelho que entrou antes
            // desta chave existir passa a saber de quem sao os seus dados.
            it[chaveConta] = contaId
        }
    }

    /**
     * A sessao caiu (401): token vencido, senha trocada noutro aparelho.
     *
     * Vai tudo menos [chaveConta]. Os dados do aparelho ficam -- podem ter
     * escritas na fila que ainda nao subiram --, e a conta dona deles fica
     * anotada para o proximo login decidir o que fazer com eles.
     */
    suspend fun sessaoCaiu() {
        context.dataStore.edit { prefs ->
            val conta = prefs[chaveConta]
            val email = prefs[chaveEmail]
            prefs.clear()
            if (conta != null) prefs[chaveConta] = conta
            if (email != null) prefs[chaveEmailDosDados] = email
        }
    }

    /**
     * Apaga a sessao inteira -- sair de verdade.
     *
     * Os dados do aparelho saem junto (ver `SessaoViewModel.sair`), entao a
     * conta dona deles tambem.
     */
    suspend fun limpar() {
        context.dataStore.edit { it.clear() }
    }
}
