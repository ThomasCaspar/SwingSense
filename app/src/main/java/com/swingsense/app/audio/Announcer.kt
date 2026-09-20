package com.swingsense.app.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Retour sonore : pendant l'enregistrement, l'ecran n'affiche presque rien
 * (le golfeur est en position, il ne regarde pas le telephone).
 * C'est la voix et le bip qui pilotent la sequence.
 */
class Announcer(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRENCH
                ready = true
            }
        }
    }

    fun say(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_ADD, null, text.hashCode().toString())
    }

    fun beep() = tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
    fun doubleBeep() = tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)

    fun release() {
        runCatching { tts?.stop(); tts?.shutdown() }
        runCatching { tone.release() }
    }
}
