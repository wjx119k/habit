package com.example.habit

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.concurrent.thread

/**
 * 彩蛋里的钢琴声。
 *
 * 为什么不用音频文件？
 *   一段像样的钢琴录音动辄几百 KB～几 MB，会把 APK 撑大不少；
 *   而"钢琴"的声音本质可以算出来：基频 + 几个泛音 + 一个衰减包络。
 *   这里用加法合成（additive synthesis）现场生成 16 秒的柔和小曲，
 *   循环播放，体积 0 KB，效果也够"优美"。
 *
 * 音乐设计：
 *   · 五声音阶（C D E G A），怎么弹都不会刺耳
 *   · C – Am – F – G 四个和弦循环，是最舒服的流行走向
 *   · 每个音 0.5 秒一个，琶音上行，像八音盒
 *   · 衰减包络 exp(-t/0.9)，模拟琴弦自然消散
 */
object PianoMusic {

    private const val SAMPLE_RATE = 44100
    private const val DURATION_SEC = 16.0

    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile
    private var playing = false

    /** 开始播放（在后台线程生成音频，不阻塞界面） */
    fun start() {
        if (playing) return
        playing = true

        worker = thread(name = "piano-music", isDaemon = true) {
            try {
                val pcm = renderLoop()
                if (!playing) return@thread

                val buffer = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track = buffer
                buffer.write(pcm, 0, pcm.size)

                // 音量留一点余地，避免戴耳机时过响
                buffer.setVolume(0.55f)
                buffer.setLoopPoints(0, pcm.size, -1)
                buffer.play()
            } catch (_: Throwable) {
                // 音频出问题绝不能让彩蛋崩掉，静默失败即可
            }
        }
    }

    /** 停止并释放 */
    fun stop() {
        playing = false
        try {
            track?.let {
                runCatching { it.stop() }
                runCatching { it.release() }
            }
        } catch (_: Throwable) {
        }
        track = null
        worker = null
    }

    // ---------------- 合成 ----------------

    /**
     * 生成一个完整的 16 秒循环。
     * 返回 PCM 16 位样本数组。
     */
    private fun renderLoop(): ShortArray {
        val total = (SAMPLE_RATE * DURATION_SEC).toInt()
        val mix = FloatArray(total)

        // 四个和弦 + 琶音音型（频率，单位 Hz）
        val chords = listOf(
            doubleArrayOf(261.63, 329.63, 392.00, 523.25), // C
            doubleArrayOf(220.00, 261.63, 329.63, 440.00), // Am
            doubleArrayOf(174.61, 220.00, 261.63, 349.23), // F
            doubleArrayOf(196.00, 246.94, 293.66, 392.00)  // G
        )

        // 琶音顺序：根音 → 五度 → 高八度 → 三度
        val pattern = intArrayOf(0, 1, 2, 3, 2, 1, 3, 1)
        val noteDuration = 0.5
        val barDuration = noteDuration * pattern.size

        var barIndex = 0
        var timeSec = 0.0
        while (timeSec < DURATION_SEC) {
            val chord = chords[barIndex % chords.size]
            pattern.forEachIndexed { step, chordIndex ->
                val start = timeSec + step * noteDuration
                if (start >= DURATION_SEC) return@forEachIndexed
                val freq = chord[chordIndex] * if (step % 2 == 1) 1.0 else 1.0
                addNote(mix, start, freq, 0.9)
            }
            timeSec += barDuration
            barIndex++
        }

        // 一首很轻的低音铺底，让整体不空
        for (i in 0 until 4) {
            val chord = chords[i % chords.size]
            addNote(mix, i * 4.0, chord[0] / 2, 1.6, amplitude = 0.16f)
        }

        // 归一化 + 转成 16 位
        var peak = 0f
        for (v in mix) if (v > peak) peak = v
        val gain = if (peak > 0f) 0.85f / peak else 1f

        val out = ShortArray(total)
        for (i in 0 until total) {
            var v = mix[i] * gain
            // 首尾各 60ms 淡入淡出，循环处不会"咔"一声
            val fade = (SAMPLE_RATE * 0.06).toInt()
            if (i < fade) v *= i.toFloat() / fade
            if (i > total - fade) v *= (total - i).toFloat() / fade
            out[i] = (v.coerceIn(-1f, 1f) * 32767).toInt().toShort()
        }
        return out
    }

    /**
     * 往混音缓冲里加一个钢琴音。
     *
     * 加分项：
     *   · 三个泛音（1x、2x、3x）叠出"琴弦"的厚度
     *   · 2x 泛音稍微失谐 0.3Hz，产生自然的"chorus"感
     *   · 指数衰减 + 5ms 起音，避免爆音
     */
    private fun addNote(
        mix: FloatArray,
        startSec: Double,
        freq: Double,
        decaySec: Double,
        amplitude: Float = 0.5f
    ) {
        val startSample = (startSec * SAMPLE_RATE).toInt()
        val length = (decaySec * 3 * SAMPLE_RATE).toInt() // 衰减到 -60dB 左右
        val attack = (SAMPLE_RATE * 0.005).toInt()

        for (i in 0 until length) {
            val index = startSample + i
            if (index >= mix.size) break

            val t = i.toDouble() / SAMPLE_RATE
            val envelope = exp(-t / (decaySec / 3.0))
            if (envelope < 0.0005) break
            val attackGain = if (i < attack) i.toFloat() / attack else 1f

            val phase = 2.0 * PI * freq * t
            val sample = (
                1.00 * sin(phase) +
                    0.38 * sin(2.0 * PI * (freq * 2 + 0.3) * t) +
                    0.16 * sin(2.0 * PI * freq * 3 * t) +
                    0.07 * sin(2.0 * PI * freq * 4 * t)
                ).toFloat()

            mix[index] += sample * envelope.toFloat() * attackGain * amplitude * 0.25f
        }
    }
}
