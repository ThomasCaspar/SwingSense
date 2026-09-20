package com.swingsense.app.model

/**
 * Position de la camera par rapport au golfeur.
 *
 * FACE_ON  : telephone place face au golfeur, perpendiculaire a la ligne de jeu.
 *            La balle traverse le champ de l'image -> le vol est dans le plan image.
 * DOWN_THE_LINE : telephone place derriere le golfeur, dans l'axe de la cible.
 *            La balle s'eloigne -> la profondeur se deduit du diametre apparent.
 */
enum class CameraPosition(val label: String, val shortLabel: String, val help: String) {
    FACE_ON(
        "Face au golfeur",
        "DE FACE",
        "Posez le telephone face a vous, perpendiculairement a la ligne de jeu, " +
            "a environ 2 m de la balle, objectif a hauteur de balle. La balle doit " +
            "traverser l'image de gauche a droite (ou l'inverse)."
    ),
    DOWN_THE_LINE(
        "Derriere le golfeur",
        "DERRIERE",
        "Posez le telephone derriere vous, dans l'axe balle-cible, a environ 2,5 m " +
            "de la balle et a hauteur de hanches. La balle doit s'eloigner vers le " +
            "centre de l'image."
    )
}

enum class BallColor(val label: String) {
    WHITE("Blanche"),
    YELLOW("Jaune"),
    ORANGE("Orange")
}

/** Systeme d'unites affiche. Le calcul reste toujours en SI en interne. */
enum class UnitSystem(val label: String) {
    METRIC("Metrique"),
    IMPERIAL("Imperial")
}

/** Club selectionne : sert au modele de spin et a l'estimation du carry. */
enum class Club(val label: String, val loftDeg: Double) {
    DRIVER("Driver", 10.5),
    WOOD3("Bois 3", 15.0),
    WOOD5("Bois 5", 18.0),
    HYBRID("Hybride", 21.0),
    IRON3("Fer 3", 21.0),
    IRON4("Fer 4", 24.0),
    IRON5("Fer 5", 27.0),
    IRON6("Fer 6", 30.5),
    IRON7("Fer 7", 34.0),
    IRON8("Fer 8", 38.0),
    IRON9("Fer 9", 42.0),
    PW("PW", 46.0),
    SW("SW", 56.0),
    LW("LW", 60.0),
    PUTTER("Putter", 3.0)
}

/** Nature physique d'une metrique : c'est elle qui pilote la conversion d'unites. */
enum class MetricKind { SPEED, DISTANCE, ANGLE, SPIN, RATIO, TEXT }

/**
 * Catalogue des metriques. C'est ce catalogue qui pilote le premier ecran :
 * le golfeur choisit ce qu'il veut mesurer, et l'app en deduit ou poser le telephone.
 */
enum class Metric(
    val key: String,
    val label: String,
    val kind: MetricKind,
    val position: CameraPosition,
    val confidence: Confidence,
    val explanation: String
) {
    BALL_SPEED("ball_speed", "Vitesse de balle", MetricKind.SPEED, CameraPosition.FACE_ON, Confidence.HIGH,
        "Deplacement de la balle image par image juste apres l'impact, converti via l'echelle de calibration."),
    LAUNCH_ANGLE("launch_angle", "Angle de lancement", MetricKind.ANGLE, CameraPosition.FACE_ON, Confidence.HIGH,
        "Pente initiale de la trajectoire dans le plan image."),
    CLUB_SPEED("club_speed", "Vitesse de club", MetricKind.SPEED, CameraPosition.FACE_ON, Confidence.MEDIUM,
        "Suivi de la tete de club par difference d'images sur les ~40 ms avant l'impact."),
    SMASH_FACTOR("smash", "Smash factor", MetricKind.RATIO, CameraPosition.FACE_ON, Confidence.MEDIUM,
        "Vitesse balle / vitesse club."),
    ATTACK_ANGLE("attack_angle", "Angle d'attaque", MetricKind.TEXT, CameraPosition.FACE_ON, Confidence.LOW,
        "Direction du deplacement de la tete de club sur les dernieres images avant impact."),
    APEX("apex", "Hauteur max (apex)", MetricKind.DISTANCE, CameraPosition.FACE_ON, Confidence.MEDIUM,
        "Simulation balistique (trainee + effet Magnus) a partir des conditions de lancement."),
    CARRY("carry", "Carry", MetricKind.DISTANCE, CameraPosition.FACE_ON, Confidence.MEDIUM,
        "Simulation balistique jusqu'au premier contact au sol."),
    TOTAL("total", "Distance totale", MetricKind.DISTANCE, CameraPosition.FACE_ON, Confidence.LOW,
        "Carry + roulement estime selon l'angle de descente."),
    BACKSPIN("backspin", "Backspin estime", MetricKind.SPIN, CameraPosition.FACE_ON, Confidence.LOW,
        "Modele empirique loft / angle d'attaque / vitesse : non mesure optiquement."),
    TEMPO("tempo", "Tempo", MetricKind.TEXT, CameraPosition.FACE_ON, Confidence.MEDIUM,
        "Rapport duree de montee / duree de descente, a partir du profil d'energie de mouvement."),

    START_DIRECTION("start_dir", "Direction de depart", MetricKind.TEXT, CameraPosition.DOWN_THE_LINE, Confidence.HIGH,
        "Angle horizontal de la trajectoire par rapport a l'axe de visee."),
    CURVATURE("curvature", "Courbe (draw/fade)", MetricKind.TEXT, CameraPosition.DOWN_THE_LINE, Confidence.LOW,
        "Acceleration laterale mesuree sur la portion de vol visible."),
    BALL_SPEED_DTL("ball_speed_dtl", "Vitesse de balle (3D)", MetricKind.SPEED, CameraPosition.DOWN_THE_LINE, Confidence.MEDIUM,
        "Vitesse 3D reconstruite : la profondeur vient du diametre apparent de la balle."),
    LAUNCH_ANGLE_DTL("launch_dtl", "Angle de lancement (3D)", MetricKind.ANGLE, CameraPosition.DOWN_THE_LINE, Confidence.MEDIUM,
        "Composante verticale de la vitesse 3D reconstruite."),
    CLUB_PATH("club_path", "Club path", MetricKind.TEXT, CameraPosition.DOWN_THE_LINE, Confidence.LOW,
        "Direction de la tete de club dans le plan horizontal juste avant l'impact (In-Out / Out-In).")
}

/** Niveau de confiance affiche sur chaque tuile. */
enum class Confidence { HIGH, MEDIUM, LOW }

/**
 * Une tuile de resultat.
 * @param value valeur SI (m/s, metres, degres, rpm) - jamais convertie en stockage.
 * @param display valeur deja formatee dans le systeme d'unites choisi.
 */
data class MetricValue(
    val metric: Metric,
    val value: Double,
    val display: String,
    val unit: String,
    val confidence: Confidence
)

data class SwingResult(
    val position: CameraPosition,
    val club: Club,
    val units: UnitSystem,
    val fps: Double,
    val trackedFrames: Int,
    val tiles: List<MetricValue>,
    val warnings: List<String> = emptyList(),
    val videoPath: String? = null
)
