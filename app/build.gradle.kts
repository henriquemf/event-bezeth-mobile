import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.beazeth.notifier"
    compileSdk = 36

    defaultConfig {
        // Diferente do pacote do TWA (`com.onrender.events_beazeth.twa`) de
        // propósito: durante a travessia os dois convivem no mesmo aparelho, e
        // dá para comparar lado a lado. Pacotes iguais fariam um substituir o
        // outro na instalação.
        applicationId = "com.beazeth.notifier"

        // 26 (Android 8) deixa de fora menos de 2% dos aparelhos e libera o
        // java.time, usado nas datas ISO que a API devolve.
        minSdk = 26
        targetSdk = 36
        // `versionCode` e o numero que o Android compara para decidir se um
        // `.apk` e mais novo que o instalado: ele so sobe, e sobe UMA vez por
        // `.apk` que sai daqui para a mao de alguem. `versionName` e o que
        // aparece nos ajustes, e e para gente ler.
        //
        // `empacotar.ps1 -Versao x.y.z` mexe nos dois de uma vez.
        versionCode = 8
        versionName = "1.4.1"
    }

    // ------------------------------------------------------------ assinatura
    //
    // A chave nao mora no repositorio (o `.gitignore` barra `*.keystore`) e a
    // senha tampouco: as duas vem de `keystore.properties`, que fica so nesta
    // maquina. Ha um `keystore.properties.exemplo` versionado dizendo o que
    // preencher.
    //
    // Sem esse arquivo o build de release SAI, so que sem assinatura -- e um
    // `.apk` sem assinatura nao instala em aparelho nenhum. Quem avisa e o
    // `empacotar.ps1`, antes de comecar, em vez de entregar no fim um arquivo
    // que nao serve.
    val arquivoDaChave = rootProject.file("keystore.properties")
    val chave = Properties().apply {
        if (arquivoDaChave.exists()) arquivoDaChave.inputStream().use { load(it) }
    }

    signingConfigs {
        if (chave.isNotEmpty()) {
            create("release") {
                // Caminho absoluto, ou relativo a raiz do repositorio.
                storeFile = rootProject.file(chave.getProperty("storeFile"))
                storePassword = chave.getProperty("storePassword")
                keyAlias = chave.getProperty("keyAlias")
                keyPassword = chave.getProperty("keyPassword")
            }
        }
    }

    // Contra qual servidor o app fala.
    //
    // O padrão é produção, inclusive no debug, porque o `.apk` de debug é o que
    // se instala num celular de verdade para experimentar — e `10.0.2.2` só
    // existe dentro do emulador. Apontar para lá por padrão entregaria um app
    // que falha no login sem explicar por quê.
    //
    // Para desenvolver contra o servidor local:
    //   ./gradlew assembleDebug -PapiBase=http://10.0.2.2:5055     (emulador)
    //   ./gradlew assembleDebug -PapiBase=http://192.168.x.x:5055  (celular na mesma rede)
    val apiBase = (project.findProperty("apiBase") as String?)
        ?: "https://events-beazeth.onrender.com"

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE", "\"$apiBase\"")
            // HTTP puro é bloqueado desde o Android 9; liberado só no debug,
            // e só para o servidor local funcionar quando alguém apontar para
            // ele. O release continua exigindo HTTPS.
            manifestPlaceholders["usesCleartextTraffic"] = "true"

            // `-PminifyDebug` liga o R8 tambem no debug.
            //
            // E a unica forma de exercitar as regras de `proguard-rules.pro`
            // contra o servidor LOCAL: o release exige HTTPS, entao um `.apk`
            // de release nao fala com `10.0.2.2`. E as regras precisam ser
            // exercitadas contra dado de verdade -- uma regra faltando so
            // aparece quando a tela que usa aquela classe abre.
            isMinifyEnabled = project.hasProperty("minifyDebug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            buildConfigField("String", "API_BASE", "\"$apiBase\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            // O tree-shaking do Android. Estava desligado, e o `.apk` levava
            // Compose, OkHttp, Room e WorkManager inteiros -- com tudo o que
            // este app nunca chama. `shrinkResources` faz o mesmo com o `res/`.
            //
            // O que o R8 nao enxerga sozinho e o que se alcanca por reflexao;
            // esta tudo em `proguard-rules.pro`, com o motivo de cada regra. E
            // o motivo de o release precisar ser TESTADO no aparelho: uma
            // regra faltando compila, instala e so quebra em execucao.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// O Room grava o esquema em JSON a cada build. Serve para a proxima migracao
// ser revisavel: da para ver no diff o que mudou na tabela, em vez de
// conferir na mao.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
}
