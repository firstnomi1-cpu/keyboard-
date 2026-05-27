package com.example

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

enum class VoicePreset(val displayName: String, val description: String) {
    ORIGINAL("Original", "Your natural vocal profile"),
    THE_SHAH("Grand Shah", "Deep sovereign authority with heavy cave reverb"),
    GOLDEN_ELF("Squeaky Herald", "High-pitched royal messenger"),
    CYBER_COMMANDER("Shadow Jester", "Metallic ring-modulated android frequency"),
    COURT_PREACHER("Mystic Vibro", "Unsteady spiritual vibrato tremolo"),
    ECHO_CHAMBER("Solomon Reverb", "Grand royal palace hall echoes")
}

class AudioEngine {
    private val TAG = "AudioEngine"
    private val SAMPLE_RATE = 16000

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    // Flow for current recording volume amplitudes (0.0f to 1.0f) for the live visualizer
    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude = _recordingAmplitude.asStateFlow()

    // Store recorded raw 16-bit PCM shorts
    private var recordedPcm: ShortArray? = null

    // Track state flows for current playback
    private var activeAudioTrack: AudioTrack? = null

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return
        recordedPcm = null

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord could not initialize.")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordJob = scope.launch {
                val readBuffer = ShortArray(1024)
                val recordedBytes = ByteArrayOutputStream()
                val shortList = ArrayList<Short>()

                while (isRecording) {
                    val readCount = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0
                    if (readCount > 0) {
                        var maxVal = 0
                        for (i in 0 until readCount) {
                            val value = readBuffer[i]
                            shortList.add(value)
                            val absVal = kotlin.math.abs(value.toInt())
                            if (absVal > maxVal) {
                                maxVal = absVal
                            }
                        }
                        // Normalize amplitude for the wave visualization
                        val normVolume = (maxVal.toFloat() / 32768f).coerceIn(0f, 1f)
                        _recordingAmplitude.value = normVolume
                    } else {
                        kotlinx.coroutines.delay(10)
                    }
                }
                recordedPcm = shortList.toShortArray()
                _recordingAmplitude.value = 0f
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            isRecording = false
        }
    }

    fun stopRecording(): Boolean {
        if (!isRecording) return false
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder", e)
        }
        recordJob?.cancel()
        recordJob = null
        return (recordedPcm != null && recordedPcm!!.isNotEmpty())
    }

    fun hasRecordings(): Boolean {
        return recordedPcm != null && recordedPcm!!.isNotEmpty()
    }

    // Playback with real-time digital signal processing (DSP)
    fun playProcessed(preset: VoicePreset) {
        val original = recordedPcm ?: return
        if (original.isEmpty()) return

        stopPlayback()

        scope.launch {
            val processed = applyDsp(original, preset)
            playPcm(processed)
        }
    }

    fun stopPlayback() {
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
            activeAudioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "No audio track active or stop failed", e)
        }
    }

    // Core DSP Engine - Pure mathematical short manipulation
    private fun applyDsp(source: ShortArray, preset: VoicePreset): ShortArray {
        return when (preset) {
            VoicePreset.ORIGINAL -> source.clone()

            VoicePreset.THE_SHAH -> {
                // Pitch shift down (0.6x) + cavern echo reverb
                val pitched = resamplePitch(source, 0.62f)
                applyEcho(pitched, delayMs = 180, feedback = 0.52f, wet = 0.6f)
            }

            VoicePreset.GOLDEN_ELF -> {
                // Pitch shift up (1.55x) for high pitch royal squeaks
                resamplePitch(source, 1.55f)
            }

            VoicePreset.CYBER_COMMANDER -> {
                // Ring Modulation with 190Hz high carrier frequency + echo
                val ringMod = applyRingModulation(source, 190.0)
                applyEcho(ringMod, delayMs = 120, feedback = 0.35f, wet = 0.4f)
            }

            VoicePreset.COURT_PREACHER -> {
                // Harmonic tremolo LFO wobble wave (7Hz modulation)
                applyTremolo(source, rateHz = 7.0, depth = 0.65f)
            }

            VoicePreset.ECHO_CHAMBER -> {
                // Deep royal echo reverb chain with multi-tapped delays
                applyEcho(source, delayMs = 280, feedback = 0.6f, wet = 0.5f)
            }
        }
    }

    // 1. Resample pitch shifting (interpolating shorts)
    private fun resamplePitch(source: ShortArray, factor: Float): ShortArray {
        val newLength = (source.size / factor).toInt()
        if (newLength <= 0) return source
        val dest = ShortArray(newLength)
        for (i in 0 until newLength) {
            val originalIndex = i * factor
            val base = originalIndex.toInt()
            val fraction = originalIndex - base
            if (base >= 0 && base < source.size - 1) {
                val s0 = source[base].toFloat()
                val s1 = source[base + 1].toFloat()
                val interpolated = s0 + fraction * (s1 - s0)
                dest[i] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return dest
    }

    // 2. Cavern Echo/Reverb circular delay buffer
    private fun applyEcho(source: ShortArray, delayMs: Int, feedback: Float, wet: Float): ShortArray {
        val delaySamples = (delayMs * SAMPLE_RATE / 1000).coerceAtLeast(1)
        val output = ShortArray(source.size + delaySamples) // Pad to allow decay trailing
        val buffer = FloatArray(delaySamples)
        var writeIndex = 0

        for (i in output.indices) {
            val dry = if (i < source.size) source[i].toFloat() else 0f
            val delayValue = buffer[writeIndex]

            // Mix
            val outVal = dry + delayValue * wet
            output[i] = outVal.toInt().coerceIn(-32768, 32767).toShort()

            // Update circular buffer with input + decayed feedback
            buffer[writeIndex] = dry * 0.4f + delayValue * feedback

            writeIndex = (writeIndex + 1) % delaySamples
        }
        return output
    }

    // 3. Cylinder Ring Modulation
    private fun applyRingModulation(source: ShortArray, carrierFreq: Double): ShortArray {
        val dest = ShortArray(source.size)
        val step = 2.0 * Math.PI * carrierFreq / SAMPLE_RATE
        for (i in source.indices) {
            val carrier = sin(i * step)
            dest[i] = (source[i] * carrier).toInt().coerceIn(-32768, 32767).toShort()
        }
        return dest
    }

    // 4. Sine Oscillator Tremolo
    private fun applyTremolo(source: ShortArray, rateHz: Double, depth: Float): ShortArray {
        val dest = ShortArray(source.size)
        val step = 2.0 * Math.PI * rateHz / SAMPLE_RATE
        val baseMin = 1f - depth
        for (i in source.indices) {
            // LFO oscillates between baseMin and 1.0
            val lfo = baseMin + (1f - baseMin) * (0.5f + 0.5f * sin(i * step).toFloat())
            dest[i] = (source[i] * lfo).toInt().coerceIn(-32768, 32767).toShort()
        }
        return dest
    }

    // Playback a raw PCM ShortArray using static high-fidelity AudioTrack
    private fun playPcm(pcm: ShortArray) {
        try {
            val bufferSizeInBytes = pcm.size * 2
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            activeAudioTrack = audioTrack
            audioTrack.write(pcm, 0, pcm.size)
            audioTrack.play()
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack playback crashed", e)
        }
    }

    // Synthesize Soundboard Procedural Chords & Fanfares
    fun playProceduralSound(type: String) {
        stopPlayback()
        scope.launch {
            val durationSeconds = 1.6
            val samples = (SAMPLE_RATE * durationSeconds).toInt()
            val data = ShortArray(samples)

            when (type) {
                "fanfare" -> {
                    // Sovereignty Fanfare: Harmonic combination of imperial brass trumpet notes (Major 3rd, Perfect 5th, Octave)
                    val freq1 = 261.63 // C4
                    val freq2 = 329.63 // E4
                    val freq3 = 392.00 // G4
                    val freq4 = 523.25 // C5

                    for (i in 0 until samples) {
                        val t = i.toDouble() / SAMPLE_RATE
                        // Envelope has swell then slow decay
                        val env = if (t < 0.2) t / 0.2 else exp(-2.2 * (t - 0.2))

                        val sin1 = sin(2.0 * Math.PI * freq1 * t)
                        val sin2 = sin(2.0 * Math.PI * freq2 * t)
                        val sin3 = sin(2.0 * Math.PI * freq3 * t)
                        val sin4 = sin(2.0 * Math.PI * freq4 * t)

                        // Compose brassy rich overtone harmonics
                        val carrier = 0.35 * sin1 + 0.3 * sin2 + 0.2 * sin3 + 0.15 * sin4
                        val overtone = 0.08 * sin(2.0 * Math.PI * (freq1 * 2) * t)

                        data[i] = ((carrier + overtone) * env * 24000.0).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
                "gong" -> {
                    // Deep Palace Gong chime
                    val baseFreq = 95.0
                    for (i in 0 until samples) {
                        val t = i.toDouble() / SAMPLE_RATE
                        // Very fast attack, slow long ring decay
                        val env = exp(-1.4 * t)
                        // Gong has complex inharmonic frequency modulations
                        val modulation = sin(2.0 * Math.PI * 3.5 * t) * 6.0
                        val gongWave = sin(2.0 * Math.PI * baseFreq * t + modulation)
                        val resonance = 0.25 * sin(2.0 * Math.PI * (baseFreq * 1.62) * t)

                        data[i] = ((gongWave + resonance) * env * 28000.0).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
                "bells" -> {
                    // Grand Royal Cathedral Chime
                    val baseFreq = 164.81 // E3 church chime
                    for (i in 0 until samples) {
                        val t = i.toDouble() / SAMPLE_RATE
                        val env = exp(-1.1 * t)
                        // Traditional chime spectrum combines octave, minor third and fourth
                        val wave = sin(2.0 * Math.PI * baseFreq * t) +
                                0.5 * sin(2.0 * Math.PI * (baseFreq * 1.99) * t) +
                                0.3 * sin(2.0 * Math.PI * (baseFreq * 1.19) * t) +
                                0.25 * sin(2.0 * Math.PI * (baseFreq * 2.45) * t)

                        data[i] = (wave * env * 18000.0).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
                "clash" -> {
                    // Imperial Sword Clash: Pseudo-White noise and rapid high frequency sine wave sweep
                    var randState = 123456789L
                    for (i in 0 until samples) {
                        val t = i.toDouble() / SAMPLE_RATE
                        val env = exp(-7.5 * t) // Rapid snap decay

                        // Linear feedback shift register noise
                        randState = (randState * 1103515245L + 12345L)
                        val noise = ((randState % 65536) - 32768) / 32768.0

                        // Metal sliding frequency gong sweep
                        val sweepFreq = 3000.0 - 1500.0 * (1f - exp(-50 * t))
                        val metalSound = sin(2.0 * Math.PI * sweepFreq * t)

                        val combined = 0.5 * noise + 0.5 * metalSound
                        data[i] = (combined * env * 22000.0).toInt().coerceIn(-32768, 32767).toShort()
                    }
                }
            }
            playPcm(data)
        }
    }

    // Synthesize Fast Single Keypress Feedbacks
    fun playKeyPressBeep(keyChar: Char, tonePreset: String) {
        scope.launch {
            val durationSeconds = 0.08
            val samples = (SAMPLE_RATE * durationSeconds).toInt()
            val data = ShortArray(samples)

            val baseFreq = when (tonePreset) {
                "royal_harps" -> {
                    // Custom pentatonic scale matching letters
                    val octaveMultiplier = if (keyChar.lowercaseChar() in "aeiou") 1.5 else 1.0
                    val base = 261.63 // C4
                    val index = (keyChar.code % 5)
                    val noteFactor = when (index) {
                        0 -> 1.0    // C
                        1 -> 1.125  // D
                        2 -> 1.25   // E
                        3 -> 1.5    // G
                        else -> 1.667 // A
                    }
                    base * noteFactor * octaveMultiplier
                }
                "electric_glitch" -> {
                    120.0 + (keyChar.code % 20) * 25.0
                }
                else -> {
                    // Standard clean dynamic click
                    500.0 + (keyChar.code % 4) * 100.0
                }
            }

            for (i in 0 until samples) {
                val t = i.toDouble() / SAMPLE_RATE
                val env = exp(-45.0 * t) // sharp click decay envelope

                val wave = when (tonePreset) {
                    "royal_harps" -> {
                        // Soft elegant sine wave with soft harmonic
                        sin(2.0 * Math.PI * baseFreq * t) + 0.3 * sin(2.0 * Math.PI * (baseFreq * 2) * t)
                    }
                    "electric_glitch" -> {
                        // Square wave sweep for digital robot vibe
                        val period = SAMPLE_RATE / baseFreq
                        if ((i % period) < (period / 2)) 0.7f else -0.7f
                    }
                    else -> {
                        // Classic round organic click
                        sin(2.0 * Math.PI * baseFreq * t)
                    }
                }

                data[i] = (wave.toDouble() * env * 20000.0).toInt().coerceIn(-32768, 32767).toShort()
            }
            playPcm(data)
        }
    }
}
