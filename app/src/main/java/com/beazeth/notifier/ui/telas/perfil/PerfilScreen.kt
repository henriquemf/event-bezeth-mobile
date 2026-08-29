package com.beazeth.notifier.ui.telas.perfil

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beazeth.notifier.sync.SyncWorker
import com.beazeth.notifier.ui.LocalModoLocal
import com.beazeth.notifier.ui.componentes.AvisoDeErro
import com.beazeth.notifier.ui.componentes.BotaoPilula
import com.beazeth.notifier.ui.componentes.BotaoPrimario
import com.beazeth.notifier.ui.componentes.CampoDoce
import com.beazeth.notifier.ui.componentes.CartaoDaTela
import com.beazeth.notifier.ui.componentes.FotoDePerfil
import com.beazeth.notifier.ui.theme.Doce
import com.beazeth.notifier.ui.theme.Espaco
import com.beazeth.notifier.ui.theme.TipografiaBeazeth

/**
 * A tela de perfil: quem e voce aqui dentro, e o que o app ja acumulou.
 *
 * Chega-se a ela tocando na propria pessoa -- o retrato no alto da lateral e
 * inteiro clicavel. E o gesto que todo aplicativo com conta ensinou, e foi o
 * que permitiu a lateral ficar so com foto e nome: o botao "Sair" que morava
 * la nao precisava estar em TODA tela, e sim no lugar onde se cuida da conta.
 *
 * ## O que muda sem conta
 *
 * Some tudo que fala com o servidor: e-mail, senha e o cartao de sincronizacao.
 * Ficam a foto, o nome e os numeros -- que saem do Room e nunca precisaram de
 * rede. O botao do fim vira "Entrar numa conta", que e a saida honesta de quem
 * escolheu ficar sem uma.
 */
@Composable
fun PerfilScreen(
    nome: String,
    email: String,
    aoSair: () -> Unit,
    aoAtualizarConta: (String, String) -> Unit,
    vm: PerfilViewModel = viewModel(),
) {
    val contexto = LocalContext.current
    val local = LocalModoLocal.current
    val cores = Doce

    val estado by vm.estado.collectAsState()
    val stats by vm.estatisticas.collectAsState()
    val temFoto by vm.temFoto.collectAsState()
    val nomeGuardado by vm.nomeLocal.collectAsState()
    val pendentes by vm.pendencias.collectAsState()
    val ultima by vm.ultimaSync.collectAsState()

    val nomeAtual = if (local) nomeGuardado else nome

    // O seletor de fotos do proprio Android: sem permissao de galeria, porque
    // quem escolhe o arquivo e o sistema e o app so recebe aquele. Pedir
    // READ_MEDIA_IMAGES para trocar uma foto seria pedir a galeria inteira.
    val escolherFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { origem -> if (origem != null) vm.escolherFoto(origem) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Espaco.e5, end = Espaco.e5, top = Espaco.e2, bottom = Espaco.e5,
        ),
        verticalArrangement = Arrangement.spacedBy(Espaco.e3),
    ) {
        // ---------------------------------------------------------- o retrato
        item {
            CartaoDaTela {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Espaco.e3),
                ) {
                    FotoDePerfil(nome = nomeAtual, tamanho = 96.dp)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Espaco.e1),
                    ) {
                        Text(
                            text = nomeAtual.ifBlank { "Sem nome ainda" },
                            style = TipografiaBeazeth.titleLarge.copy(fontSize = 22.sp),
                            color = cores.tinta,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (local) "Tudo neste aparelho" else email,
                            style = TipografiaBeazeth.bodyMedium,
                            color = cores.tintaSuave,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(Espaco.e1)) {
                            BotaoPilula(
                                texto = if (temFoto) "Trocar foto" else "Escolher foto",
                                aoTocar = {
                                    vm.limparRecado()
                                    escolherFoto.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                },
                                destacado = !temFoto,
                            )
                            if (temFoto) {
                                BotaoPilula(texto = "Remover", aoTocar = { vm.removerFoto() })
                            }
                        }
                    }
                }

                Recado(estado, EstadoDoPerfil.Campo.FOTO)
            }
        }

        // ------------------------------------------------------------- o nome
        item {
            CartaoDaTela(titulo = "Nome de exibição") {
                var texto by rememberSaveable(nomeAtual) { mutableStateOf(nomeAtual) }

                CampoDoce(
                    rotulo = "Como você quer ser chamada",
                    valor = texto,
                    aoMudar = { texto = it; vm.limparRecado() },
                    dica = "Bea",
                    opcoesDoTeclado = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Text(
                    text = if (local) {
                        "Fica neste aparelho, junto com a foto."
                    } else {
                        "É o mesmo nome que aparece no site."
                    },
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                )
                BotaoPrimario(
                    texto = "Salvar nome",
                    aoTocar = { vm.salvarNome(texto, aoAtualizarConta) },
                    habilitado = texto.trim() != nomeAtual && texto.isNotBlank(),
                    carregando = estado.salvando && estado.campo == EstadoDoPerfil.Campo.NOME,
                )
                Recado(estado, EstadoDoPerfil.Campo.NOME)
            }
        }

        // ----------------------------------------------------------- o e-mail
        if (!local) item {
            CartaoDaTela(titulo = "E-mail") {
                var novo by rememberSaveable(email) { mutableStateOf(email) }
                var senha by rememberSaveable { mutableStateOf("") }

                // A senha some SO quando deu certo. Apagar tambem no erro
                // obrigaria a redigitar por causa de um errinho -- e o erro
                // mais comum aqui e justamente a senha.
                LimparNoAcerto(estado, EstadoDoPerfil.Campo.EMAIL) { senha = "" }

                CampoDoce(
                    rotulo = "E-mail de acesso",
                    valor = novo,
                    aoMudar = { novo = it; vm.limparRecado() },
                    opcoesDoTeclado = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )
                CampoDoce(
                    rotulo = "Senha atual",
                    valor = senha,
                    aoMudar = { senha = it; vm.limparRecado() },
                    ocultarTexto = true,
                    opcoesDoTeclado = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Text(
                    text = "É com este e-mail que você entra — aqui e no site.",
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                )
                BotaoPrimario(
                    texto = "Trocar e-mail",
                    aoTocar = { vm.salvarEmail(novo, senha, aoAtualizarConta) },
                    habilitado = novo.trim() != email && novo.isNotBlank(),
                    carregando = estado.salvando && estado.campo == EstadoDoPerfil.Campo.EMAIL,
                )
                Recado(estado, EstadoDoPerfil.Campo.EMAIL)
            }
        }

        // ------------------------------------------------------------ a senha
        if (!local) item {
            CartaoDaTela(titulo = "Senha") {
                var atual by rememberSaveable { mutableStateOf("") }
                var nova by rememberSaveable { mutableStateOf("") }
                var repetida by rememberSaveable { mutableStateOf("") }

                LimparNoAcerto(estado, EstadoDoPerfil.Campo.SENHA) {
                    atual = ""; nova = ""; repetida = ""
                }

                CampoDoce(
                    rotulo = "Senha atual",
                    valor = atual,
                    aoMudar = { atual = it; vm.limparRecado() },
                    ocultarTexto = true,
                    opcoesDoTeclado = KeyboardOptions(imeAction = ImeAction.Next),
                )
                CampoDoce(
                    rotulo = "Senha nova",
                    valor = nova,
                    aoMudar = { nova = it; vm.limparRecado() },
                    ocultarTexto = true,
                    opcoesDoTeclado = KeyboardOptions(imeAction = ImeAction.Next),
                )
                CampoDoce(
                    rotulo = "Repita a senha nova",
                    valor = repetida,
                    aoMudar = { repetida = it; vm.limparRecado() },
                    ocultarTexto = true,
                    opcoesDoTeclado = KeyboardOptions(imeAction = ImeAction.Done),
                )
                Text(
                    text = "Pelo menos 8 caracteres. Os aparelhos onde você já " +
                        "entrou continuam conectados.",
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                )
                BotaoPrimario(
                    texto = "Trocar senha",
                    aoTocar = { vm.salvarSenha(atual, nova, repetida, aoAtualizarConta) },
                    habilitado = atual.isNotBlank() && nova.isNotBlank(),
                    carregando = estado.salvando && estado.campo == EstadoDoPerfil.Campo.SENHA,
                )
                Recado(estado, EstadoDoPerfil.Campo.SENHA)
            }
        }

        // ---------------------------------------------------------- os numeros
        item {
            CartaoDaTela(titulo = "Seus números") {
                Numeros(stats)
            }
        }

        // --------------------------------------------------- a sincronizacao
        //
        // Veio da tela de Aparencia: e assunto de CONTA, e la ficava no meio de
        // temas e fontes. Some inteiro sem conta -- nao ha fila, nem servidor.
        if (!local) item {
            CartaoDaTela(titulo = "Sincronização") {
                Linha(
                    rotulo = "Esperando para subir",
                    valor = if (pendentes == 0) "nada" else "$pendentes",
                )
                Linha(
                    rotulo = "Última conversa",
                    valor = ultima?.replace("T", " ")?.take(16) ?: "ainda não",
                )
                Text(
                    text = if (pendentes == 0) {
                        "Tudo o que você escreveu já está no servidor."
                    } else {
                        "Escrito no aparelho. Sobe sozinho quando houver rede."
                    },
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                )
                BotaoPrimario(
                    texto = "Sincronizar agora",
                    aoTocar = { SyncWorker.agora(contexto) },
                )
            }
        }

        // ------------------------------------------------------------- a saida
        item {
            CartaoDaTela {
                BotaoPrimario(
                    texto = if (local) "Entrar numa conta" else "Sair da conta",
                    aoTocar = aoSair,
                )
                Text(
                    text = if (local) {
                        "Post-its, tarefas, blocos e eventos escritos aqui sobem para a " +
                            "conta ao entrar. Nada é apagado. Copos de água e aparência " +
                            "continuam só neste aparelho."
                    } else {
                        "Sair apaga os dados guardados neste aparelho, junto com a foto " +
                            "e o histórico de pomodoros. O resto está no servidor e volta " +
                            "no próximo login."
                    },
                    style = TipografiaBeazeth.bodyMedium,
                    color = cores.tintaSuave,
                )
            }
        }
    }
}

/**
 * Esvazia os campos de senha depois de uma gravacao BEM-SUCEDIDA.
 *
 * Fica aqui, e nao no toque do botao, porque no toque ainda nao se sabe se deu
 * certo: apagar ali fazia "senha atual incorreta" vir com os tres campos ja
 * limpos, obrigando a redigitar tudo por causa de um erro de digitacao.
 */
@Composable
private fun LimparNoAcerto(
    estado: EstadoDoPerfil,
    campo: EstadoDoPerfil.Campo,
    limpar: () -> Unit,
) {
    LaunchedEffect(estado.recado, estado.campo) {
        if (estado.campo == campo && estado.recado != null) limpar()
    }
}

/**
 * O retorno de uma gravacao, embaixo do cartao que a pediu.
 *
 * So aparece no cartao certo: sem o filtro por [campo], trocar a senha deixaria
 * "Senha trocada" verde embaixo do campo de e-mail tambem.
 */
@Composable
private fun Recado(estado: EstadoDoPerfil, campo: EstadoDoPerfil.Campo) {
    if (estado.campo != campo) return

    estado.erro?.let { AvisoDeErro(mensagem = it) }
    estado.recado?.let {
        Text(
            text = it,
            style = TipografiaBeazeth.bodyMedium,
            color = Doce.destaqueEscuro,
        )
    }
}

/** Rotulo a esquerda, valor a direita -- o mesmo par da tela de Aparencia. */
@Composable
private fun Linha(rotulo: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = rotulo,
            style = TipografiaBeazeth.bodyMedium,
            color = Doce.tintaSuave,
        )
        Text(
            text = valor,
            style = TipografiaBeazeth.titleMedium,
            color = Doce.tinta,
        )
    }
}
