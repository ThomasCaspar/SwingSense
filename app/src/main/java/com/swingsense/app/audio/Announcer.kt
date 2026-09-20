package com.swingsense.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Gestionnaire de synthèse vocale pour annoncer les résultats de swing.
 * Utilise TextToSpeech natif Android pour vocaliser les mesures et événements.
 */
class Announcer(private val context: Context) {
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    
    init {
        initializeTextToSpeech()
    }
    
    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale("fr", "FR")  // Français de France
                textToSpeech?.setSpeechRate(1.0f)  // Vitesse normale
                isInitialized = true
                Log.d("Announcer", "TextToSpeech initialized successfully")
            } else {
                Log.e("Announcer", "TextToSpeech initialization failed with status: $status")
            }
        }
    }
    
    /**
     * Annonce un texte simple via TTS.
     */
    fun announce(text: String) {
        if (!isInitialized || textToSpeech == null) {
            Log.w("Announcer", "TextToSpeech not initialized, skipping announcement")
            return
        }
        
        try {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_ADD, null)
        } catch (e: Exception) {
            Log.e("Announcer", "Error announcing text: ${e.message}")
        }
    }
    
    /**
     * Annonce les résultats complets d'une mesure de swing.
     */
    fun announceSwingResults(
        clubHeadSpeed: Float? = null,
        ballSpeed: Float? = null,
        smash: Float? = null,
        distance: Float? = null,
        direction: String? = null,
        position: String? = null
    ) {
        val announcements = mutableListOf<String>()
        
        // Préambule
        announcements.add("Résultats du swing")
        
        // Vitesse de la tête du club
        clubHeadSpeed?.let {
            announcements.add("Vitesse tête du club: ${it.toInt()} kilomètres par heure")
        }
        
        // Vitesse de la balle
        ballSpeed?.let {
            announcements.add("Vitesse de la balle: ${it.toInt()} kilomètres par heure")
        }
        
        // Smash factor
        smash?.let {
            announcements.add("Facteur de contact: ${String.format("%.2f", it)}")
        }
        
        // Distance
        distance?.let {
            announcements.add("Distance estimée: ${it.toInt()} mètres")
        }
        
        // Direction
        direction?.let {
            val directionFrench = when (it.lowercase()) {
                "left" -> "la balle part à gauche"
                "straight" -> "la balle part droit devant"
                "right" -> "la balle part à droite"
                else -> "direction: $it"
            }
            announcements.add(directionFrench)
        }
        
        // Position du corps
        position?.let {
            announcements.add("Position du corps: $it")
        }
        
        // Joindre et annoncer
        val fullAnnouncement = announcements.joinToString(". ") + "."
        announce(fullAnnouncement)
    }
    
    /**
     * Annonce un événement d'impact.
     */
    fun announceImpact() {
        announce("Impact détecté")
    }
    
    /**
     * Annonce une erreur de détection.
     */
    fun announceError(message: String) {
        announce("Erreur: $message")
    }
    
    /**
     * Libérer les ressources TextToSpeech.
     */
    fun release() {
        if (textToSpeech != null) {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
        }
    }
}