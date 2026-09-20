# SwingSense - prototype d'analyse de swing de golf (Android / Kotlin)

Projet Android Studio complet, a ouvrir tel quel. Aucun binaire, que du source.

## Ouvrir le projet

1. Decompressez l'archive.
2. Android Studio -> **Open** -> selectionnez le dossier `SwingSense`.
3. Le dossier `gradle/wrapper` ne contient **pas** `gradle-wrapper.jar` (binaire non
   transmissible ici). Deux solutions :
   - Android Studio propose automatiquement de regenerer le wrapper : acceptez ;
   - ou en ligne de commande : `gradle wrapper --gradle-version 8.9`.
4. Sync Gradle (les dependances se telechargent), puis Run sur l'appareil.

`minSdk 28`, `compileSdk 35`, Kotlin 2.0.21, Jetpack Compose. Application en
**paysage force** (le telephone est couche sur son trepied).

## Le point technique central

Le cahier des charges pose une contrainte que l'architecture respecte a la lettre :

> l'analyse temps reel plafonne a 30-60 im/s, or un swing demande 240 im/s.

D'ou une separation stricte en deux sessions camera differentes :

| Phase | Session | Cadence | Images visibles par l'app |
|---|---|---|---|
| Calibration (balle immobile) | session normale + `ImageReader` | ~30 im/s | oui, temps reel |
| Swing | `SESSION_HIGH_SPEED` + `MediaRecorder` | 120 / 240 im/s | **non** |
| Analyse | `MediaExtractor` + `MediaCodec` | hors ligne | oui, toutes |

Pendant le swing, la camera alimente directement l'encodeur materiel en rafale
(`createHighSpeedRequestList` / `setRepeatingBurst`) : Android **interdit** tout
`ImageReader` dans cette session. L'application ne voit donc rien pendant le
swing, et relit le fichier juste apres, image par image, via
`MediaCodec.getOutputImage()`.

`setCaptureRate(fps) == setVideoFrameRate(fps)` : le fichier est ecrit a la
cadence reelle (240 im/s) et non en "slow motion" a 30 im/s, ce qui garde des
horodatages exploitables.

## Comment l'app sait que le swing est fini - troisieme revision

Deux versions successives essayaient de detecter l'impact EN DIRECT via le
micro (transitoire sonore). Retour de terrain sans appel : sur un practice
partage, l'impact du golfeur au poste voisin a exactement la meme signature
acoustique que le sien - amplitude, frequence, tout. Aucune heuristique sur un
micro mono ne peut fiablement les distinguer ; ce n'etait pas un bug a
corriger, mais une limite physique du signal choisi. Le micro et la permission
`RECORD_AUDIO` ont ete retires du projet.

**Nouvelle strategie : ne plus rien detecter en direct.** La camera ne recoit
de toute facon aucune image pendant l'enregistrement haute vitesse (contrainte
materielle de `SESSION_HIGH_SPEED`, voir plus haut) - aucun signal, quel qu'il
soit, ne peut etre analyse en temps reel a ce moment-la. L'enregistrement
tourne donc simplement pendant une duree fixe (`SwingViewModel.recordingCeilingSeconds`,
12 s par defaut), largement suffisante pour couvrir adresse + swing, puis
s'arrete automatiquement. C'est l'analyse HORS LIGNE de la video qui retrouve
ensuite OU se trouve le swing dans cette fenetre - exactement ce que
`TrajectoryTracker` fait deja (detection du deplacement de balle = impact,
puis sortie du champ = fin du vol). La detection automatique se deplace donc
de "en direct, au son" vers "apres coup, a l'image" : c'est la meme idee
d'automatisation, sur un signal qui, lui, fonctionne reellement.

Le bouton **"Swing termine"** reste present pour ecourter l'attente des que le
golfeur sait avoir swingue - un confort, pas une necessite : si personne
n'appuie sur rien, l'enregistrement s'arrete tout seul au bout de
`recordingCeilingSeconds` et l'analyse trouve le swing quand meme.

## Annuler un swing lance par erreur

Avant, un swing mal declenche imposait d'attendre le traitement complet avant
de pouvoir relancer. Deux points de sortie desormais :

- **Pendant l'enregistrement** (ecran d'attente) : bouton **"Annuler ce
  swing"**. Coupe l'enregistrement, supprime le fichier, repart directement en
  calibration (le fond est conserve, reverrouillage quasi instantane).
- **Pendant le calcul** : bouton **"Annuler - mauvaise prise"**. L'analyse
  tourne sur un thread de fond (`Dispatchers.Default`) via une boucle
  synchrone (decodage image par image) qui ne repond pas nativement a
  l'annulation de coroutine standard. `SwingAnalyzer.analyze()` accepte donc un
  parametre `isCancelled: () -> Boolean`, verifie a CHAQUE image decodee : des
  que le drapeau passe a vrai, la boucle s'arrete immediatement (`return@forEachFrame false`),
  sans attendre la fin du fichier. L'arret ressenti est quasi instantane.

## Les deux positions de camera

C'est la selection des donnees qui impose la position, jamais l'inverse
(`Metric.position` dans `model/Model.kt` pilote tout l'enchainement).

**Face au golfeur** (perpendiculaire a la ligne de jeu) : la balle traverse
l'image, le vol est dans le plan image. Une echelle metres/pixel suffit.
-> vitesse de balle, angle de lancement, vitesse de club, smash factor, angle
d'attaque, tempo, puis apex / carry / total par simulation balistique.

**Derriere le golfeur** (axe de la cible) : la balle s'eloigne. La profondeur se
lit dans le **retrecissement du diametre apparent** :

```
z(t) = f_px * 42.67 mm / (2 * r_px(t))
x(t) = (u - cx) * z / f_px
y(t) = -(v - cy) * z / f_px
```

-> vitesse 3D, direction de depart (push/pull), angle de lancement, courbure
(draw/fade) et club path.

## Unites

Bascule **Metrique / Imperial** sur l'ecran de configuration (metrique par defaut).

- metrique : vitesses en **km/h**, distances en **m** ;
- imperial : vitesses en **mph**, distances en **yards**.

Regle interne : tout le pipeline de calcul travaille en SI (m, m/s, degres, rpm)
et `MetricValue.value` stocke toujours la valeur SI. La conversion n'a lieu qu'au
moment de fabriquer la chaine affichee (`model/Units.kt`), ce qui permettra plus
tard de comparer deux sessions enregistrees dans des unites differentes sans
aucune reconversion.

## Calibration - detection par difference au fond

Deuxieme revision de cette etape, apres retour de terrain : le seuillage
colorimetrique ("cherche du blanc/jaune/orange") tenait bien sur un tapis de
practice mais echouait regulierement sur l'herbe - trop de texture, d'ombres
et de variations de teinte pour un seuil fixe. Nouvelle strategie, qui ne
depend plus de la couleur du terrain :

1. **CAPTURING_BACKGROUND** - au lancement de la calibration, le golfeur NE
   POSE PAS encore sa balle. L'app annonce *"Ne posez pas encore la balle,
   capture du fond"* et moyenne ~18 images du sol tel qu'il est (herbe, tapis,
   ombres - et le golfeur lui-meme si deja en place). Cette photo de reference
   remplace le seuillage colorimetrique.
2. **AWAITING_BALL** - l'app annonce *"Posez la balle"*. Chaque image est
   ensuite comparee a ce fond (`BallDetector.detectByBackground`, diff YUV +
   composantes connexes + filtre de forme) : on ne cherche plus "ce qui est
   blanc" mais "ce qui a change" - une balle blanche posee sur de l'herbe
   change enormement, quelle que soit la nuance de vert en dessous. Le cercle
   detecte s'affiche en direct pour verification visuelle immediate.
3. **Verrouillage** - meme logique qu'avant (15 images stables), guidage de
   distance ("rapprochez / eloignez" sur le rayon apparent), annonce vocale
   *"Balle calibree, swing en attente"*.

Le pointage au doigt (cadre en pointilles) reste disponible mais devient une
**aide facultative** plutot qu'une etape obligatoire : il sert a departager
deux objets qui auraient tous les deux change depuis le fond (la balle ET,
par exemple, le bout d'une chaussure claire qui a bouge en meme temps). Un
bouton **Recapturer le fond** permet de relancer l'etape 1 si l'eclairage
change (nuage qui passe) ou si un faux positif persiste.

Benefice pour l'ENCHAINEMENT DE SWINGS : le fond est conserve tant que le
trepied ne bouge pas. "Refaire le meme swing" saute directement l'etape 1 et
passe a *"Posez la balle"* - plus rapide entre deux essais. Changer de type
d'analyse (position camera differente) force en revanche une nouvelle capture,
puisque le cadrage a change.

Benefice pour l'ANALYSE : le meme fond de reference est transmis a
`SwingAnalyzer`, qui l'utilise pour amorcer la detection dans les premieres
images de la video haute vitesse (avant l'impact) exactement de la meme
maniere - la aussi, plus la peine de chercher une couleur, on cherche ce qui a
change. Le fond est redimensionne automatiquement si la resolution
d'enregistrement differe de celle du flux de calibration
(`BackgroundModel.resizedTo`).

La balle de golf fait 42,67 mm : c'est l'etalon. Son rayon apparent donne
l'echelle metres/pixel et la distance camera-balle.

**Limite assumee** : si le golfeur bouge nettement entre la capture du fond et
la pose de la balle (change de position, deplace son sac), la zone concernee
se retrouve elle aussi signalee comme "changee" et peut temporairement
perturber la detection. Le filtre de forme (rond, rempli, 3 a 90 px de rayon)
exclut la plupart des faux positifs de ce genre (un pied ou un sac n'est pas
rond) ; en dernier recours, le bouton Recapturer le fond ou le pointage au
doigt regle le probleme.

Verification croisee automatique en vue de face : la courbure verticale de la
trajectoire doit correspondre a g = 9,81 m/s2. Si l'echelle deduite de la gravite
s'ecarte trop de l'echelle deduite de la balle, un avertissement s'affiche (camera
mal orientee, balle mal calibree).

## Honnetete des donnees

Chaque tuile porte une pastille de confiance :

- **verte** : mesure optique directe (vitesse de balle, angle de lancement) ;
- **orange** : mesure derivee (vitesse de club par difference d'images, carry par
  simulation) ;
- **rouge** : estimation par modele - notamment le **backspin**, qui n'est PAS
  mesure. Le mesurer optiquement supposerait de resoudre les alveoles sur
  plusieurs images a 2 m de distance : hors de portee d'un capteur de telephone.
  Il est estime a partir du loft, de l'angle d'attaque et de la vitesse.

Le carry et l'apex sortent d'une integration balistique reelle (trainee +
effet Magnus, `analysis/Ballistics.kt`), pas d'une formule de vide.

## Detection de balle

Pas d'OpenCV : traitement en Kotlin pur, seuillage directement en YUV (pas de
conversion RGB) puis composantes connexes avec filtres de rondeur et de
remplissage. L'APK reste leger et sans `.so` par ABI.

Pendant la calibration, l'app **apprend** la teinte reelle de la balle
(`BallProfile`) au lieu d'utiliser des seuils fixes : bien plus robuste entre la
lumiere du matin et le plein soleil. Le suivi en vol se fait ensuite dans une ROI
predite par la vitesse, avec des criteres relaches (la balle est floue).

## Reglages utiles

| Quoi | Ou |
|---|---|
| Duree de l'enregistrement (auto-stop) | `SwingViewModel.recordingCeilingSeconds` (12 s par defaut) |
| Fenetre de rayon cible a la calibration | `Calibration.TARGET_RADIUS_MIN/MAX_PX` |
| Sensibilite de la detection d'impact | `ImpactSoundDetector` (seuils `peak > 9000`, `mean > baseline * 6`) |
| Taille du secteur pointe au doigt | `CalibrationSession.searchHalfFraction` (0.12 par defaut) |
| Nombre d'images moyennees pour le fond | `CalibrationSession.referenceFrameCount` (18 par defaut, ~0.6 s) |
| Seuil de difference au fond (detection sur herbe) | `BallDetector.detectByBackground(diffThreshold=...)`, 50 par defaut |
| Seuils couleur (repli si aucun fond fourni) | `BallDetector.matchesDefault` |
| Systeme d'unites par defaut | `UiState.units` dans `SwingViewModel` |
| Modele de spin | `Ballistics.estimateBackspin` |
| Fenetre de suivi tete de club | `MotionProfiler.clubHeadKinematics(framesBefore)` |

## Ecran de placement : apercu camera reel + horizon superpose

Le premier jet de cet ecran n'affichait qu'un schema abstrait (silhouette du
golfeur, position de la balle et du telephone dessinees a la main) - pas
suffisant pour bien cadrer en pratique : impossible de savoir si la balle
sera vraiment dans le champ, ou si le cadrage laisse assez de marge.

L'ecran affiche desormais un **apercu camera reel** (`PreviewSession` - camera
ouverte, flux affiche, aucune analyse en cours), avec l'horizon mesure
superpose directement sur l'image : une ligne pointillee fixe (le vrai
horizontal de l'ecran) et une ligne pleine, coloree selon la tolerance, qui
s'incline avec le roll reel du telephone. L'utilisateur peut ainsi l'aligner
sur un repere visible dans la scene (bord d'un tapis, ligne de terrain), en
plus du niveau numerique (`LevelIndicator`) toujours present dans le panneau
lateral.

`PreviewSession` est volontairement une classe a part de `CalibrationSession` :
elle ouvre juste la camera et affiche le flux, sans `ImageReader` ni aucune
detection. Quand l'utilisateur passe a l'etape suivante, `CalibrationScreen`
ouvre sa propre session (avec, elle, la capture de fond et la detection de
balle) - la camera est donc brievement rechargee lors de la transition entre
les deux ecrans. C'est le compromis retenu pour garder les deux ecrans
independants plutot que de faire persister une session camera unique a
travers plusieurs Composables.

## Niveau a bulle (placement du telephone)

Retour de terrain : un telephone visiblement incline faussait les distances
calculees. Logique - toute la geometrie de l'analyse (angle de lancement,
decomposition de la vitesse en x/y, `Calibration.metersPerPixel`) suppose que
le "haut" de l'image correspond a la verticale reelle. Un telephone penche sur
le cote tourne l'image d'autant, et cette rotation se propage directement dans
les calculs.

`sensors/LevelSensor.kt` lit le capteur `TYPE_GRAVITY` (repli sur
`TYPE_ACCELEROMETER` si absent) - PAS le gyroscope : le gyroscope mesure une
VITESSE de rotation, pas une orientation absolue, et derive avec le temps si on
l'integre seul. Le capteur de gravite, deja filtre par la plateforme, donne
directement "ou est le bas", sans derive, ideal pour un niveau statique.

Seul le **roll** (inclinaison laterale, "l'horizon est-il droit ?") est un
vrai critere pass/fail (tolerance `LevelSensor.TOLERANCE_DEG`, 3° par defaut) :
c'est lui qui tourne l'image et corrompt directement les calculs. Le **pitch**
(inclinaison avant/arriere) reste purement informatif - viser legerement vers
le bas pour cadrer une balle posee au sol est normal et attendu, un pass/fail
dessus aurait ete une fausse bonne idee.

Affiche sur l'ecran de placement, a la fois en superposition sur l'apercu
camera (l'horizon) et sous forme de niveau a bulle numerique dans le panneau
lateral, puis en rappel discret pendant la calibration (le trepied peut avoir
ete heurte entre-temps). Aucune permission requise - les capteurs de
mouvement sont libres d'acces sous Android, contrairement a la camera.

**A verifier sur appareil reel** (indisponible dans cet environnement de
build) : le signe de l'angle affiche. La transformation d'axes repere-capteur
vers repere-ecran (`LevelSensor.toScreenRelative`) est correcte en theorie
pour une activite verrouillee en paysage, mais si la bulle tourne dans le
mauvais sens sur votre telephone, il suffit d'inverser le signe de `rollDeg`.

## Limites connues de ce prototype

- Les clubs couverts vont du driver au putter, fers 3 a 9 inclus (les lofts
  servent au modele de spin : ajustez-les dans `Club` si vos clubs different).
- La tete de club est suivie par simple difference d'images : c'est la mesure la
  plus fragile (ombres, herbe qui vole, autre golfeur dans le champ).
- La courbure draw/fade est calculee sur une portion de vol tres courte : elle
  donne une tendance, pas une valeur.
- Le niveau a bulle n'a pas ete verifie sur appareil physique (indisponible
  dans cet environnement de build) - voir la note de signe ci-dessus.
- Aucun historique de session n'est encore persiste (les .mp4 restent dans
  `Android/data/com.swingsense.app/files/swings/`).
- Les textes d'interface sont ecrits en dur dans les composables ; a extraire
  dans `strings.xml` avant toute traduction.
- Non teste sur appareil : c'est un point de depart a ajuster, en particulier les
  seuils de detection, qui dependent beaucoup de votre practice.

## Arborescence

```
app/src/main/java/com/swingsense/app/
├── MainActivity.kt
├── model/
│   ├── Model.kt                   catalogue des metriques + position requise
│   └── Units.kt                   conversion metrique / imperial
├── camera/
│   ├── CameraCapabilities.kt      decouverte des modes 120/240 im/s
│   ├── PreviewSession.kt          apercu camera seul (ecran de placement)
│   ├── CalibrationSession.kt      session normale + ImageReader
│   └── HighSpeedRecorder.kt       SESSION_HIGH_SPEED + MediaRecorder
├── sensors/
│   └── LevelSensor.kt             niveau a bulle (placement du telephone)
├── audio/
│   └── Announcer.kt               voix + bips
├── vision/
│   ├── YuvFrame.kt                acces CPU aux plans YUV
│   ├── BallDetector.kt            detection par diff au fond + repli couleur
│   ├── VideoFrameExtractor.kt     relecture image par image
│   ├── TrajectoryTracker.kt       suivi + detection d'impact visuel
│   └── MotionProfiler.kt          tempo + tete de club
├── analysis/
│   ├── Calibration.kt             echelle metrique par le diametre de balle
│   ├── Fitting.kt                 regressions lineaire / quadratique
│   ├── Ballistics.kt              vol de balle (trainee + Magnus)
│   ├── FaceOnAnalyzer.kt
│   ├── DownTheLineAnalyzer.kt
│   └── SwingAnalyzer.kt           orchestrateur
├── viewmodel/SwingViewModel.kt    machine a etats complete
└── ui/                            Compose : setup, placement, calibration,
                                   attente, calcul, resultats
```
