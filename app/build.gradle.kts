plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dreamsounds.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.dreamsounds.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

// Generate compact offline ambience loops before Android packages assets.
val generateAmbience by tasks.registering {
    val outputDir = layout.projectDirectory.dir("src/main/assets/sounds")
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.asFile
        dir.mkdirs()
        val sampleRate = 22050
        val seconds = 10
        val count = sampleRate * seconds
        val names = listOf("rain", "campfire", "birds", "wind", "ocean", "white_noise")
        names.forEachIndexed { index, name ->
            val file = dir.resolve("$name.wav")
            if (!file.exists()) {
                java.io.DataOutputStream(file.outputStream().buffered()).use { out ->
                    val dataBytes = count * 2
                    out.writeBytes("RIFF"); out.writeInt(java.lang.Integer.reverseBytes(36 + dataBytes))
                    out.writeBytes("WAVEfmt "); out.writeInt(java.lang.Integer.reverseBytes(16))
                    out.writeShort(java.lang.Short.reverseBytes(1).toInt()); out.writeShort(java.lang.Short.reverseBytes(1).toInt())
                    out.writeInt(java.lang.Integer.reverseBytes(sampleRate)); out.writeInt(java.lang.Integer.reverseBytes(sampleRate * 2))
                    out.writeShort(java.lang.Short.reverseBytes(2).toInt()); out.writeShort(java.lang.Short.reverseBytes(16).toInt())
                    out.writeBytes("data"); out.writeInt(java.lang.Integer.reverseBytes(dataBytes))
                    var seed = 1234567 + index * 7919
                    var previous = 0.0
                    repeat(count) { i ->
                        seed = seed * 1103515245 + 12345
                        val noise = (((seed ushr 16) and 0x7fff) / 16384.0) - 1.0
                        val t = i.toDouble() / sampleRate
                        val shaped = when (index) {
                            0 -> noise * 0.22 + previous * 0.45
                            1 -> noise * (0.10 + 0.12 * kotlin.math.abs(kotlin.math.sin(t * 1.7)))
                            2 -> if (i % (sampleRate * 2) < 1800) kotlin.math.sin(t * 900) * 0.12 else noise * 0.015
                            3 -> noise * (0.10 + 0.08 * kotlin.math.sin(t * 0.25))
                            4 -> noise * (0.08 + 0.12 * (0.5 + 0.5 * kotlin.math.sin(t * 0.6))) + kotlin.math.sin(t * 0.4) * 0.04
                            else -> noise * 0.16
                        }
                        previous = shaped
                        val fade = kotlin.math.min(1.0, kotlin.math.min((i + 1) / 500.0, (count - i) / 500.0))
                        val value = (shaped * fade * 12000).toInt().coerceIn(-32768, 32767)
                        out.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
                    }
                }
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(generateAmbience) }
