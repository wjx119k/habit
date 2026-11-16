package com.example.habit

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 彩蛋用的环境音：风声 + 开关门音效。
 *
 * 全部现场合成，不打包任何音频文件：
 *   · 风   = 白噪声过一次低通 + 缓慢起伏的包络（模拟阵风）
 *   · 开门 = 频率下滑的"吱呀"声 + 一点噪声摩擦
 *   · 关门 = 低频闷响 + 短促噪声
 */
object WinterAudio {

    private const val RATE = 44100

    private var windTrack: AudioTrack? = null
    private var windThread: Thread? = null

    @Volatile
    private var windOn = false

    // ---------------- 风 ----------------

    fun startWind() {
        if (windOn) return
        windOn = true

        windThread = thread(name = "winter-wind", isDaemon = true) {
            try {
                val pcm = renderWind()
                if (!windOn) return@thread

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                windTrack = track
                track.write(pcm, 0, pcm.size)
                track.setVolume(0.35f)
                track.setLoopPoints(0, pcm.size, -1)
                track.play()
            } catch (_: Throwable) {
            }
        }
    }

    fun stopWind() {
        windOn = false
        runCatching { windTrack?.stop() }
        runCatching { windTrack?.release() }
        windTrack = null
        windThread = null
    }

    // ---------------- 开门 / 关门 ----------------

    fun playDoorOpen() = playOneShot(renderDoorOpen())

    fun playDoorClose() = playOneShot(renderDoorClose())

    fun stopAll() {
        stopWind()
    }

    private fun playOneShot(pcm: ShortArray) {
        thread(isDaemon = true) {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(pcm, 0, pcm.size)
                track.setVolume(0.7f)
                track.play()

                // 播完再释放（长度就是音频本身）
                Thread.sleep((pcm.size.toLong() * 1000 / RATE) + 120)
            } catch (_: Throwable) {
            } finally {
                runCatching { track?.stop() }
                runCatching { track?.release() }
            }
        }
    }

    // ---------------- 合成 ----------------

    /**
     * 8 秒的风声循环。
     * 白噪声 → 一阶低通（滤掉刺耳高频）→ 两条不同周期的 LFO 相乘（形成不规则的阵风）。
     */
    private fun renderWind(): ShortArray {
        val seconds = 8.0
        val total = (RATE * seconds).toInt()
        val out = ShortArray(total)
        val random = Random(20261116)
        var lowPass = 0f

        for (i in 0 until total) {
            val t = i.toDouble() / RATE

            // 白噪声
            val white = random.nextFloat() * 2f - 1f
            // 一阶低通：让声音变"闷"，像风穿过树林
            lowPass += 0.06f * (white - lowPass)

            // 两条 LFO 叠加，周期约 5.3 秒和 2.1 秒，产生忽强忽弱
            val lfo = 0.55f +
                0.28f * sin(2.0 * PI * t / 5.3).toFloat() +
                0.17f * sin(2.0 * PI * t / 2.1 + 1.7).toFloat()
            val gust = lfo.coerceIn(0f, 1f)

            var v = lowPass * gust * 1.6f

            // 首尾淡入淡出，循环接缝不"咔"
            val fade = (RATE * 0.35).toInt()
            if (i < fade) v *= i.toFloat() / fade
            if (i > total - fade) v *= (total - i).toFloat() / fade

            out[i] = (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
        return out
    }

    /** 开门：木质门轴的"吱呀"，音高从高往下滑 */
    private fun renderDoorOpen(): ShortArray {
        val seconds = 1.15
        val total = (RATE * seconds).toInt()
        val out = ShortArray(total)
        var phase = 0.0
        val random = Random(11)

        for (i in 0 until total) {
            val t = i.toDouble() / RATE
            val p = (t / seconds).coerceIn(0.0, 1.0)

            // 频率 330Hz → 170Hz，再加一点抖动（门轴摩擦不均匀）
            val freq = 330.0 - 160.0 * p + 6.0 * sin(2.0 * PI * 11.0 * t)
            phase += 2.0 * PI * freq / RATE

            val envelope = sin(PI * p.coerceIn(0.0, 1.0)) // 中间最响
            val squeak = (sin(phase) * 0.55 + sin(phase * 2.01) * 0.2).toFloat()
            val rub = (random.nextFloat() * 2f - 1f) * 0.12f
            val v = (squeak * 0.5f + rub) * envelope.toFloat() * 0.5f
            out[i] = (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
        return out
    }

    /** 关门：低频闷响，很短的"咚" */
    private fun renderDoorClose(): ShortArray {
        val seconds = 0.55
        val total = (RATE * seconds).toInt()
        val out = ShortArray(total)
        val random = Random(22)

        for (i in 0 until total) {
            val t = i.toDouble() / RATE
            val thud = sin(2.0 * PI * 72.0 * t) * exp(-t / 0.08)
            val click = (random.nextFloat() * 2f - 1f) * exp(-t / 0.02).toFloat() * 0.35f
            val v = (thud.toFloat() * 0.8f + click) * 0.75f
            out[i] = (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
        return out
    }
}
