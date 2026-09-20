package com.swingsense.app.vision

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.sqrt

/**
 * Détecteur de balle utilisant OpenCV Hough Circle Detection.
 * Supporte à la fois la détection automatique et le raffinement manuel.
 */
class BallDetector {
    
    companion object {
        private const val MIN_CIRCLE_RADIUS = 5
        private const val MAX_CIRCLE_RADIUS = 100
        private const val HOUGH_DP = 1.0
        private const val HOUGH_MIN_DIST = 50.0
        private const val HOUGH_PARAM1 = 50.0  // Canny upper threshold
        private const val HOUGH_PARAM2 = 30.0  // Accumulator threshold
    }
    
    /**
     * Détecte les cercles (balles) dans l'image en utilisant HoughCircles.
     * @param frame Image Mat en BGR
     * @return Liste des cercles détectés (x, y, radius)
     */
    fun detectBalls(frame: Mat): List<Ball> {
        val grayFrame = Mat()
        Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY)
        
        // Appliquer un flou pour réduire le bruit
        Imgproc.GaussianBlur(grayFrame, grayFrame, Size(5.0, 5.0), 0.0)
        
        // Détecter les cercles
        val circles = Mat()
        Imgproc.HoughCircles(
            grayFrame,
            circles,
            Imgproc.CV_HOUGH_GRADIENT,
            HOUGH_DP,
            HOUGH_MIN_DIST,
            HOUGH_PARAM1,
            HOUGH_PARAM2,
            MIN_CIRCLE_RADIUS,
            MAX_CIRCLE_RADIUS
        )
        
        val balls = mutableListOf<Ball>()
        for (i in 0 until circles.cols()) {
            val data = circles.get(0, i)
            if (data != null && data.size >= 3) {
                balls.add(
                    Ball(
                        x = data[0].toFloat(),
                        y = data[1].toFloat(),
                        radius = data[2].toFloat()
                    )
                )
            }
        }
        
        grayFrame.release()
        circles.release()
        
        return balls.sortedByDescending { it.radius }  // Trier par taille décroissante
    }
    
    /**
     * Raffine la détection dans une région d'intérêt (ROI) restreinte.
     * Utilisé après une sélection manuelle de la balle.
     * @param frame Image Mat en BGR
     * @param centerX Coordonnée X du centre du ROI
     * @param centerY Coordonnée Y du centre du ROI
     * @param roiRadius Rayon du ROI (zone de recherche)
     * @return Balle détectée ou null si aucune trouvée
     */
    fun refineAround(
        frame: Mat,
        centerX: Float,
        centerY: Float,
        roiRadius: Float
    ): Ball? {
        // Définir les limites du ROI
        val roiLeft = maxOf(0, (centerX - roiRadius).toInt())
        val roiTop = maxOf(0, (centerY - roiRadius).toInt())
        val roiWidth = minOf(frame.width() - roiLeft, (roiRadius * 2).toInt())
        val roiHeight = minOf(frame.height() - roiTop, (roiRadius * 2).toInt())
        
        // Extraire le ROI
        val roi = frame.submat(Rect(roiLeft, roiTop, roiWidth, roiHeight))
        val grayRoi = Mat()
        Imgproc.cvtColor(roi, grayRoi, Imgproc.COLOR_BGR2GRAY)
        
        // Appliquer un flou
        Imgproc.GaussianBlur(grayRoi, grayRoi, Size(5.0, 5.0), 0.0)
        
        // Détecter les cercles dans le ROI avec des paramètres affinés
        val circles = Mat()
        Imgproc.HoughCircles(
            grayRoi,
            circles,
            Imgproc.CV_HOUGH_GRADIENT,
            HOUGH_DP / 2,  // Plus fin
            30.0,           // Distance minimale réduite
            HOUGH_PARAM1,
            HOUGH_PARAM2 * 1.5f,  // Seuil du cumul augmenté
            MIN_CIRCLE_RADIUS,
            MAX_CIRCLE_RADIUS / 2
        )
        
        // Récupérer le meilleur cercle (plus grand)
        val ball = if (circles.cols() > 0) {
            val data = circles.get(0, 0)
            if (data != null && data.size >= 3) {
                Ball(
                    x = roiLeft + data[0].toFloat(),  // Compenser l'offset du ROI
                    y = roiTop + data[1].toFloat(),
                    radius = data[2].toFloat()
                )
            } else null
        } else null
        
        roi.release()
        grayRoi.release()
        circles.release()
        
        return ball
    }
    
    /**
     * Filtre les balles basée sur la couleur (blanc ou jaune).
     */
    fun filterByColor(frame: Mat, balls: List<Ball>): List<Ball> {
        val hsvFrame = Mat()
        Imgproc.cvtColor(frame, hsvFrame, Imgproc.COLOR_BGR2HSV)
        
        return balls.filter { ball ->
            // Créer un masque de couleur blanche ou jaune
            val ballRegion = hsvFrame.submat(
                Rect(
                    (ball.x - ball.radius).toInt().coerceAtLeast(0),
                    (ball.y - ball.radius).toInt().coerceAtLeast(0),
                    (ball.radius * 2).toInt().coerceAtMost(hsvFrame.width()),
                    (ball.radius * 2).toInt().coerceAtMost(hsvFrame.height())
                )
            )
            
            val whiteLower = Scalar(0.0, 0.0, 200.0)
            val whiteUpper = Scalar(180.0, 30.0, 255.0)
            val yellowLower = Scalar(20.0, 100.0, 100.0)
            val yellowUpper = Scalar(30.0, 255.0, 255.0)
            
            val whiteMask = Mat()
            val yellowMask = Mat()
            Core.inRange(ballRegion, whiteLower, whiteUpper, whiteMask)
            Core.inRange(ballRegion, yellowLower, yellowUpper, yellowMask)
            
            val whitePixels = Core.countNonZero(whiteMask)
            val yellowPixels = Core.countNonZero(yellowMask)
            
            ballRegion.release()
            whiteMask.release()
            yellowMask.release()
            
            // Accepter si au moins 10% des pixels sont blanc ou jaune
            val totalPixels = (ball.radius * 2 * ball.radius * 2).toInt()
            (whitePixels + yellowPixels) > (totalPixels * 0.1)
        }
        
        hsvFrame.release()
        return balls
    }
}

/**
 * Représente une balle détectée.
 */
data class Ball(
    val x: Float,
    val y: Float,
    val radius: Float
)