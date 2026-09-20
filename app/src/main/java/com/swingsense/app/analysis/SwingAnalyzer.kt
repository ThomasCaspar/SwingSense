package com.swingsense.app.analysis

import com.swingsense.app.model.*
import com.swingsense.app.vision.*

/** Position de la balle designee par l'utilisateur, en coordonnees normalisees 0..1. */
data class SeedPoint(val x: Float, val y: Float)

data class AnalysisInput(
    val videoPath: String,
    val position: CameraPosition,
    val club: Club,
    val ballColor: BallColor,
    val units: UnitSystem,
    val fps: Double,
    val focalPx: Double,
    val selectedMetrics: Set<Metric>,
    /** Zone ou chercher la balle a l'adresse, verrouillee pendant la calibration. */
    val seed: SeedPoint? = null,
    /**
     * Fond de reference capture avant que la balle soit posee (voir
     * CalibrationSession). Sert a amorcer la detection dans la video
     * enregistree exactement comme pendant la calibration : on cherche ce
     * qui differe du fond, pas une couleur - c'est ce qui rend la detection
     * fiable sur l'herbe. Redimensionne au besoin a la resolution reelle de
     * la video (qui peut differer de celle du flux de calibration).
     */
    val background: BackgroundModel? = null
)

/**
 * Orchestrateur : relit la video haute vitesse une seule fois et, en une passe,
 *  - retrouve la balle a l'adresse (dans la zone designee au doigt si elle existe) ;
 *  - apprend son profil colorimetrique reel ;
 *  - la suit image par image ;
 *  - alimente le profil de mouvement (tempo, tete de club) ;
 *  - puis delegue le calcul a l'analyseur correspondant a la position camera.
 */
class SwingAnalyzer {

    fun analyze(
        input: AnalysisInput,
        isCancelled: () -> Boolean = { false },
        onProgress: (Float, String) -> Unit
    ): SwingResult {
        onProgress(0.05f, "Ouverture de la video...")

        val extractor = VideoFrameExtractor(input.videoPath)
        val profiler = MotionProfiler()
        var profile: BallProfile? = null
        var tracker: TrajectoryTracker? = null
        var frameW = 0
        var frameH = 0
        var bootstrapFrames = 0
        var resizedBackground: BackgroundModel? = null
        val warnings = mutableListOf<String>()

        val decoded = extractor.forEachFrame { index, _, frame ->
            if (isCancelled()) return@forEachFrame false
            frameW = frame.width
            frameH = frame.height

            if (profile == null) {
                // Phase d'amorcage : la balle est encore a l'adresse, immobile.
                // La zone verrouillee pendant la calibration evite de chercher sur
                // toute l'image ; le fond de reference evite de chercher "du blanc"
                // (peu fiable sur l'herbe) et cherche "ce qui a change" a la place.
                if (resizedBackground == null && input.background != null) {
                    resizedBackground = input.background.resizedTo(frame.width, frame.height)
                }
                val seedRoi = input.seed?.let { s ->
                    val half = frame.width * (0.10 + 0.02 * (bootstrapFrames / 10))
                    Roi.around((s.x * frame.width).toDouble(), (s.y * frame.height).toDouble(), half)
                        .clamp(frame.width, frame.height)
                }
                val bg = resizedBackground
                val det = when {
                    bg != null && seedRoi != null ->
                        BallDetector.detectByBackground(frame, bg, roi = seedRoi, step = 1, colorHint = input.ballColor)
                    bg != null ->
                        BallDetector.detectByBackground(frame, bg, step = 2, colorHint = input.ballColor)
                    seedRoi != null ->
                        BallDetector.detect(frame, input.ballColor, null, roi = seedRoi, step = 1)
                    else ->
                        BallDetector.detect(frame, input.ballColor, null, step = 2)
                }
                bootstrapFrames++
                if (det != null && det.radiusPx >= 3.0) {
                    profile = BallProfile(
                        color = input.ballColor,
                        refY = det.meanY,
                        refU = det.meanU,
                        refV = det.meanV,
                        radiusPx = det.radiusPx
                    )
                    tracker = TrajectoryTracker(input.ballColor, profile!!, input.fps)
                } else if (bootstrapFrames > 40) {
                    return@forEachFrame false   // balle introuvable, inutile de continuer
                }
                if (index % 25 == 0) onProgress(0.10f, "Recherche de la balle...")
                return@forEachFrame true
            }

            val tk = tracker!!
            val keep = tk.onFrame(index, frame)
            val last = tk.points.lastOrNull()
            profiler.onFrame(
                index, frame,
                ballX = last?.xPx ?: (frameW / 2.0),
                ballY = last?.yPx ?: (frameH / 2.0),
                ballRadius = profile!!.radiusPx
            )
            if (index % 20 == 0) {
                onProgress(0.15f + (index / 900f).coerceAtMost(0.6f), "Analyse image ${index}...")
            }
            keep
        }

        onProgress(0.8f, "Calcul des donnees...")

        val tk = tracker
        if (profile == null || tk == null) {
            return SwingResult(
                position = input.position,
                club = input.club,
                units = input.units,
                fps = input.fps,
                trackedFrames = decoded,
                tiles = emptyList(),
                warnings = listOf(
                    "Balle jamais detectee dans l'enregistrement. Verifiez la couleur " +
                        "selectionnee, l'eclairage, et designez bien la balle au doigt " +
                        "pendant la calibration."
                ),
                videoPath = input.videoPath
            )
        }
        if (!tk.hasEnoughData()) {
            warnings += "Seulement ${tk.points.size} positions de balle apres l'impact : " +
                "essayez de reculer le telephone ou d'augmenter la frequence d'images."
        }

        val clubKin = if (tk.impactFrame > 0) profiler.clubHeadKinematics(tk.impactFrame, input.fps) else null
        val tempo = if (tk.impactFrame > 0) profiler.tempo(tk.impactFrame) else null

        val (tiles, analysisWarnings) = when (input.position) {
            CameraPosition.FACE_ON -> FaceOnAnalyzer.analyze(
                points = tk.points,
                metersPerPixel = Calibration.metersPerPixel(profile!!.radiusPx),
                club = input.club,
                clubKin = clubKin,
                tempo = tempo,
                fps = input.fps,
                units = input.units,
                selected = input.selectedMetrics
            )
            CameraPosition.DOWN_THE_LINE -> DownTheLineAnalyzer.analyze(
                points = tk.points,
                focalPx = input.focalPx,
                principalX = frameW / 2.0,
                principalY = frameH / 2.0,
                club = input.club,
                clubKin = clubKin,
                units = input.units,
                selected = input.selectedMetrics
            )
        }

        onProgress(1f, "Termine")
        return SwingResult(
            position = input.position,
            club = input.club,
            units = input.units,
            fps = input.fps,
            trackedFrames = tk.points.size,
            tiles = tiles,
            warnings = warnings + analysisWarnings,
            videoPath = input.videoPath
        )
    }
}
