package com.swingsense.app.viewmodel

import android.app.Application
import android.util.Size
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swingsense.app.analysis.*
import com.swingsense.app.analysis.SeedPoint
import com.swingsense.app.audio.Announcer
import com.swingsense.app.camera.*
import com.swingsense.app.model.*
import com.swingsense.app.vision.BackgroundModel
import com.swingsense.app.vision.Detection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Step { SETUP, PLACEMENT, CALIBRATION, WAITING, PROCESSING, RESULTS }

data class UiState(
    val step: Step = Step.SETUP,
    val selectedMetrics: Set<Metric> = setOf(
        Metric.BALL_SPEED, Metric.LAUNCH_ANGLE, Metric.CLUB_SPEED, Metric.CARRY
    ),
    val club: Club = Club.IRON7,
    val ballColor: BallColor = BallColor.WHITE,
    val position: CameraPosition = CameraPosition.FACE_ON,
    val units: UnitSystem = UnitSystem.METRIC,

    val highSpeedOption: HighSpeedOption? = null,
    val availableOptions: List<HighSpeedOption> = emptyList(),
    val highSpeedSupported: Boolean = true,

    /** Niveau a bulle (accelerometre), lu pendant PLACEMENT et CALIBRATION. */
    val rollDeg: Float = 0f,
    val pitchDeg: Float = 0f,
    val levelAvailable: Boolean = true,

    val calibrationPhase: CalibrationPhase = CalibrationPhase.CAPTURING_BACKGROUND,
    val backgroundProgress: Float = 0f,
    val detection: Detection? = null,
    val analysisWidth: Int = 1280,
    val analysisHeight: Int = 720,
    val guidance: CalibrationGuidance = CalibrationGuidance.NOT_FOUND,
    val lockProgress: Float = 0f,
    /** Zone designee au doigt (0..1), aide optionnelle. */
    val seed: SeedPoint? = null,

    val countdownSec: Int = 0,

    val progress: Float = 0f,
    val progressLabel: String = "",

    val result: SwingResult? = null,
    val error: String? = null
) {
    /** La position requise decoule des metriques choisies (regle du cahier des charges). */
    val requiredPosition: CameraPosition
        get() = selectedMetrics.firstOrNull()?.position ?: CameraPosition.FACE_ON
}

class SwingViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val announcer = Announcer(app)
    private var calibration: CalibrationSession? = null
    private var recorder: HighSpeedRecorder? = null
    private var stableFrames = 0

    private var countdownJob: Job? = null
    private var analysisJob: Job? = null
    @Volatile private var analysisCancelled = false

    /**
     * Fond de reference courant. Conserve entre deux swings "du meme type"
     * (le trepied n'a pas bouge, inutile de recapturer), efface des qu'on
     * change de position camera ou de type d'analyse.
     */
    private var background: BackgroundModel? = null

    /**
     * Duree maximale d'un enregistrement (secondes).
     *
     * L'ancienne version essayait de detecter la fin du swing en ecoutant
     * l'impact au micro : peu fiable, et carrement inutilisable sur un
     * practice partage (l'impact du golfeur voisin declenche l'arret au
     * meme titre que le sien - meme signature acoustique, aucun moyen fiable
     * de les distinguer sur un micro mono).
     *
     * Nouvelle strategie : pas de detection en temps reel du tout. La camera
     * ne recoit de toute facon aucune image pendant l'enregistrement haute
     * vitesse (contrainte materielle, voir HighSpeedRecorder), donc rien
     * n'est "detectable" en direct quelle que soit la methode. L'enregistrement
     * tourne simplement pendant une duree fixe, largement suffisante pour
     * couvrir adresse + swing, puis s'arrete automatiquement - et c'est
     * l'analyse hors ligne de la video (TrajectoryTracker, deja en place)
     * qui retrouve OU se trouve le swing dans cette fenetre, quel que soit
     * le moment exact ou il s'est produit. La detection automatique se fait
     * donc apres coup, sur l'image, plutot qu'en direct, sur le son.
     *
     * Le bouton "Swing termine" reste disponible pour ecourter l'attente des
     * que le golfeur sait avoir swingue - un confort, pas une necessite.
     */
    var recordingCeilingSeconds = 12

    init {
        val options = CameraCapabilities.listHighSpeed(app)
        _state.value = _state.value.copy(
            availableOptions = options,
            highSpeedOption = options.firstOrNull(),
            highSpeedSupported = options.isNotEmpty()
        )
    }

    // ---------------- Configuration ----------------

    fun toggleMetric(m: Metric) {
        val cur = _state.value.selectedMetrics.toMutableSet()
        if (m in cur) cur.remove(m) else {
            // Une session = une position camera. Changer de famille reinitialise la selection.
            if (cur.isNotEmpty() && cur.first().position != m.position) cur.clear()
            cur.add(m)
        }
        _state.value = _state.value.copy(
            selectedMetrics = cur,
            position = cur.firstOrNull()?.position ?: CameraPosition.FACE_ON
        )
    }

    fun setClub(c: Club) { _state.value = _state.value.copy(club = c) }
    fun setUnits(u: UnitSystem) { _state.value = _state.value.copy(units = u) }
    fun setBallColor(c: BallColor) { _state.value = _state.value.copy(ballColor = c) }
    fun setOption(o: HighSpeedOption) { _state.value = _state.value.copy(highSpeedOption = o) }

    fun goTo(step: Step) { _state.value = _state.value.copy(step = step, error = null) }

    // ---------------- Niveau a bulle ----------------

    fun updateLevel(rollDeg: Float, pitchDeg: Float) {
        _state.value = _state.value.copy(rollDeg = rollDeg, pitchDeg = pitchDeg)
    }

    fun setLevelAvailable(available: Boolean) {
        _state.value = _state.value.copy(levelAvailable = available)
    }

    // ---------------- Calibration ----------------

    /**
     * Deroule : capture du fond (le golfeur ne pose pas encore la balle) puis,
     * une fois le fond pret, detection par difference des que la balle apparait.
     *
     * Si un fond valide existe deja (swing precedent, meme emplacement de
     * trepied), on saute directement l'etape de capture.
     */
    private var previewSession: PreviewSession? = null

    /** Resolution partagee de la camera arriere : privilegie celle du mode haute vitesse choisi. */
    private fun resolveCameraId(): String? =
        _state.value.highSpeedOption?.cameraId ?: CameraCapabilities.backCameraId(getApplication())

    /**
     * Aperitif camera simple (pas d'analyse) pour l'ecran de PLACEMENT : voir
     * reellement ce que la camera cadre, en plus du niveau a bulle, avant de
     * passer a la calibration (capture du fond + detection de la balle).
     */
    fun startPreview(previewSurface: Surface) {
        val app = getApplication<Application>()
        val cameraId = resolveCameraId() ?: run {
            _state.value = _state.value.copy(error = "Aucune camera arriere disponible")
            return
        }
        previewSession = PreviewSession(app).also { it.start(previewSurface, cameraId) }
    }

    fun stopPreview() {
        previewSession?.stop()
        previewSession = null
    }

    fun startCalibration(previewSurface: Surface) {
        val app = getApplication<Application>()
        val cameraId = resolveCameraId() ?: run {
                _state.value = _state.value.copy(error = "Aucune camera arriere disponible")
                return
            }
        stableFrames = 0
        val size = Size(1280, 720)
        val reuse = background

        _state.value = _state.value.copy(
            calibrationPhase = if (reuse != null) CalibrationPhase.AWAITING_BALL else CalibrationPhase.CAPTURING_BACKGROUND,
            backgroundProgress = if (reuse != null) 1f else 0f,
            detection = null,
            lockProgress = 0f
        )
        if (reuse == null) {
            announcer.say("Ne posez pas encore la balle. Capture du fond.")
        } else {
            announcer.say("Posez la balle.")
        }

        calibration = CalibrationSession(
            context = app,
            ballColor = _state.value.ballColor,
            analysisSize = size,
            onPhaseChanged = { phase -> onCalibrationPhaseChanged(phase) },
            onBackgroundProgress = { done, total ->
                _state.value = _state.value.copy(backgroundProgress = done.toFloat() / total)
            },
            onBackgroundReady = { bg ->
                background = bg
                announcer.say("Posez la balle.")
            },
            onDetection = { det, w, h -> onDetection(det, w, h) }
        ).also { session ->
            _state.value.seed?.let { seed ->
                session.seedX = seed.x
                session.seedY = seed.y
            }
            session.start(previewSurface, cameraId, reuseBackground = reuse)
        }
    }

    private fun onCalibrationPhaseChanged(phase: CalibrationPhase) {
        _state.value = _state.value.copy(calibrationPhase = phase)
    }

    /**
     * Pointage optionnel : aide a departager deux objets ayant tous les deux
     * change depuis le fond (la balle ET, par exemple, le bout d'une
     * chaussure claire qui a bouge en meme temps).
     */
    fun onCalibrationTap(normX: Float, normY: Float) {
        val seed = SeedPoint(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))
        stableFrames = 0
        calibration?.seedX = seed.x
        calibration?.seedY = seed.y
        _state.value = _state.value.copy(seed = seed, lockProgress = 0f, detection = null)
    }

    fun clearSeed() {
        stableFrames = 0
        calibration?.seedX = null
        calibration?.seedY = null
        _state.value = _state.value.copy(seed = null, lockProgress = 0f, detection = null)
    }

    /** Refait la capture de fond : eclairage qui a change, faux positif persistant. */
    fun recaptureBackground() {
        stableFrames = 0
        background = null
        _state.value = _state.value.copy(
            detection = null, lockProgress = 0f, backgroundProgress = 0f,
            calibrationPhase = CalibrationPhase.CAPTURING_BACKGROUND
        )
        announcer.say("Ne posez pas la balle. Nouvelle capture du fond.")
        calibration?.recaptureBackground()
    }

    private fun onDetection(det: Detection?, w: Int, h: Int) {
        val guidance = Calibration.guidance(det?.radiusPx)
        if (guidance == CalibrationGuidance.OK) stableFrames++ else stableFrames = 0
        val progress = (stableFrames / 15f).coerceAtMost(1f)

        _state.value = _state.value.copy(
            detection = det,
            analysisWidth = w,
            analysisHeight = h,
            guidance = guidance,
            lockProgress = progress
        )

        if (stableFrames == 15) {
            // Position exacte de la balle au moment du verrouillage : elle sert de
            // point de depart a la recherche dans la video haute vitesse.
            det?.let {
                _state.value = _state.value.copy(
                    seed = SeedPoint((it.cx / w).toFloat(), (it.cy / h).toFloat())
                )
            }
            viewModelScope.launch { onBallLocked() }
        }
    }

    private suspend fun onBallLocked() {
        stopCalibration()
        announcer.say("Balle calibrée. Swing quand vous êtes prêt.")
        announcer.beep()
        delay(400)
        startRecording()
    }

    fun stopCalibration() {
        calibration?.stop()
        calibration = null
    }

    // ---------------- Enregistrement haute vitesse ----------------

    private fun startRecording() {
        val app = getApplication<Application>()
        val option = _state.value.highSpeedOption ?: run {
            _state.value = _state.value.copy(
                error = "Cet appareil n'expose pas de session haute vitesse (120/240 im/s)."
            )
            return
        }
        _state.value = _state.value.copy(
            step = Step.WAITING,
            countdownSec = recordingCeilingSeconds
        )

        val dir = File(app.getExternalFilesDir(null) ?: app.filesDir, "swings").apply { mkdirs() }
        val rec = HighSpeedRecorder(app)
        recorder = rec

        rec.start(
            option = option,
            outputDir = dir,
            onReady = { onRecordingStarted() },
            onError = { msg ->
                _state.value = _state.value.copy(step = Step.CALIBRATION, error = msg)
            }
        )
    }

    private fun onRecordingStarted() {
        countdownJob = viewModelScope.launch {
            var remaining = recordingCeilingSeconds
            while (remaining > 0 && _state.value.step == Step.WAITING) {
                _state.value = _state.value.copy(countdownSec = remaining)
                delay(1000)
                remaining--
            }
            if (_state.value.step == Step.WAITING) {
                finishSwing()
            }
        }
    }

    /** "Swing terminé" : arrete l'enregistrement et lance l'analyse. Ecourte simplement l'attente. */
    fun finishSwing() {
        if (_state.value.step != Step.WAITING) return
        countdownJob?.cancel()
        countdownJob = null
        val file = recorder?.stop()
        val fps = recorder?.actualFps ?: 240
        recorder = null

        if (file == null || !file.exists()) {
            _state.value = _state.value.copy(
                step = Step.CALIBRATION,
                error = "Enregistrement vide. Reessayez."
            )
            return
        }
        announcer.doubleBeep()
        _state.value = _state.value.copy(step = Step.PROCESSING, progress = 0f)
        analyze(file, fps.toDouble())
    }

    /**
     * "Annuler ce swing" : declenchement par erreur, faux depart, etc.
     * Coupe l'enregistrement, SUPPRIME le fichier, et repart directement en
     * calibration (le fond est conserve - la balle n'a normalement pas
     * bouge, le reverrouillage est quasi instantane).
     */
    fun cancelSwing() {
        if (_state.value.step != Step.WAITING) return
        countdownJob?.cancel()
        countdownJob = null
        val file = recorder?.stop()
        recorder = null
        file?.delete()

        _state.value = _state.value.copy(
            step = Step.CALIBRATION, error = null,
            detection = null, lockProgress = 0f,
            calibrationPhase = if (background != null) CalibrationPhase.AWAITING_BALL else CalibrationPhase.CAPTURING_BACKGROUND
        )
    }

    // ---------------- Analyse ----------------

    private fun analyze(file: File, fps: Double) {
        val s = _state.value
        val app = getApplication<Application>()
        val option = s.highSpeedOption
        val lens = option?.let { CameraCapabilities.lensInfo(app, it.cameraId) }
        val width = option?.size?.width ?: 1280
        val focalPx = if (lens != null) {
            Calibration.focalPx(width, lens.focalLengthMm, lens.sensorWidthMm)
        } else {
            Calibration.focalPxFromFov(width, 70.0)
        }
        val bgForAnalysis = background
        analysisCancelled = false

        analysisJob = viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    SwingAnalyzer().analyze(
                        AnalysisInput(
                            videoPath = file.absolutePath,
                            position = s.position,
                            club = s.club,
                            ballColor = s.ballColor,
                            units = s.units,
                            fps = fps,
                            focalPx = focalPx,
                            selectedMetrics = s.selectedMetrics,
                            seed = s.seed,
                            background = bgForAnalysis
                        ),
                        isCancelled = { analysisCancelled }
                    ) { p, label ->
                        _state.value = _state.value.copy(progress = p, progressLabel = label)
                    }
                }.getOrElse { t ->
                    SwingResult(
                        position = s.position, club = s.club, units = s.units,
                        fps = fps, trackedFrames = 0,
                        tiles = emptyList(),
                        warnings = listOf("Erreur pendant l'analyse : ${t.message}"),
                        videoPath = file.absolutePath
                    )
                }
            }

            if (analysisCancelled) {
                // Pas de resultat partiel affiche : retour direct, pret pour un nouvel essai.
                _state.value = _state.value.copy(step = Step.CALIBRATION, progress = 0f, progressLabel = "")
                return@launch
            }
            _state.value = _state.value.copy(step = Step.RESULTS, result = result, progress = 1f)
        }
    }

    /**
     * Annule une analyse en cours (swing lance par erreur, mauvaise prise).
     * La verification `isCancelled` est lue a chaque image decodee dans
     * SwingAnalyzer : l'arret est quasi instantane, pas besoin d'attendre la
     * fin du traitement.
     */
    fun cancelAnalysis() {
        if (_state.value.step != Step.PROCESSING) return
        analysisCancelled = true
        analysisJob?.cancel()
        _state.value = _state.value.copy(
            step = Step.CALIBRATION, progress = 0f, progressLabel = "",
            detection = null, lockProgress = 0f,
            calibrationPhase = if (background != null) CalibrationPhase.AWAITING_BALL else CalibrationPhase.CAPTURING_BACKGROUND
        )
    }

    // ---------------- Enchainement ----------------

    /** "Refaire un swing du meme type" : le fond est reutilise, on repart directement en calibration. */
    fun repeatSameSwing() {
        _state.value = _state.value.copy(
            step = Step.CALIBRATION, result = null, detection = null,
            lockProgress = 0f, error = null
        )
        stableFrames = 0
    }

    /** "Changer de type d'analyse" : la position camera va probablement changer, le fond n'est plus valable. */
    fun newSwingType() {
        background = null
        _state.value = _state.value.copy(
            step = Step.SETUP, result = null, detection = null,
            lockProgress = 0f, error = null,
            seed = null, backgroundProgress = 0f,
            calibrationPhase = CalibrationPhase.CAPTURING_BACKGROUND
        )
        stableFrames = 0
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }

    override fun onCleared() {
        stopCalibration()
        stopPreview()
        countdownJob?.cancel()
        analysisCancelled = true
        analysisJob?.cancel()
        recorder?.stop()
        announcer.release()
        super.onCleared()
    }
}
