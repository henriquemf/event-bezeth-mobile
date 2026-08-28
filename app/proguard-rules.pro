# Regras do R8 para o build de release.
#
# O R8 e o "tree-shaking" do Android: percorre o codigo a partir dos pontos de
# entrada e joga fora o que nao tem como ser alcancado -- do app e das
# bibliotecas. Ficava desligado, e por isso o `.apk` levava o Compose, o
# OkHttp, o Room e o WorkManager inteiros, com tudo o que este app nunca chama.
#
# O que ele NAO consegue enxergar sozinho e o que e alcancado por reflexao ou
# por nome, e e disso que trata cada regra abaixo. O modo de falhar e sempre o
# mesmo e sempre so no release: compila, instala, e estoura em tempo de
# execucao numa tela especifica.

# ------------------------------------------------------- kotlinx.serialization
#
# O serializador de cada `@Serializable` e uma classe gerada (`Foo$$serializer`)
# que ninguem instancia pelo nome: quem a acha e o `serializer()` do companion,
# por reflexao. Sem estas regras o R8 a remove, e a primeira resposta da API
# falha com "Serializer for class 'X' is not found".
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.beazeth.notifier.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.beazeth.notifier.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.beazeth.notifier.data.**$$serializer { *; }

# ------------------------------------------------------------------ WorkManager
#
# O WorkManager guarda o NOME da classe do worker no proprio banco e a instancia
# por reflexao quando a hora chega. Renomeada ou removida, a sincronizacao
# periodica para de rodar em silencio -- e o app continua funcionando, so que
# sem nunca mais falar com o servidor sozinho. Erro dificil de notar.
-keep class com.beazeth.notifier.sync.SyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ------------------------------------------------------------------------ Room
#
# As entidades sao lidas por codigo gerado, que usa os nomes dos campos para
# montar o SQL, e o `_Impl` de cada DAO e resolvido por nome na subida do banco.
-keep class com.beazeth.notifier.data.local.** { *; }

# ---------------------------------------------------------------------- OkHttp
#
# O OkHttp ja traz as proprias regras no `.aar`; estas so calam avisos sobre
# classes opcionais (Conscrypt, BouncyCastle) que este app nao usa.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
