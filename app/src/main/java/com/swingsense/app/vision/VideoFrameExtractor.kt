package com.swingsense.app.vision

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat

/**
 * Decodage image par image du fichier enregistre en haute vitesse.
 *
 * Point cle de l'architecture : on N'ANALYSE PAS en direct.
 * Le pipeline temps reel (ImageReader) est plafonne a ~30-60 im/s par le
 * pipeline camera ; la session "constrained high speed" ecrit en revanche
 * 120/240 im/s dans un fichier. On relit donc ce fichier apres coup,
 * en mode buffer (getOutputImage), ce qui donne acces a toutes les images.
 */
class VideoFrameExtractor(private val path: String) {

    /**
     * @param onFrame renvoie false pour arreter le decodage (ex. balle sortie du champ).
     * @return nombre d'images decodees
     */
    fun forEachFrame(
        maxFrames: Int = 4000,
        onFrame: (index: Int, ptsUs: Long, frame: YuvFrame) -> Boolean
    ): Int {
        val extractor = MediaExtractor()
        extractor.setDataSource(path)

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            extractor.release()
            throw IllegalStateException("Aucune piste video dans $path")
        }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        codec.configure(format, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var frameIndex = 0

        try {
            while (!outputDone && frameIndex < maxFrames) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        if (info.size > 0) {
                            val image = codec.getOutputImage(outIdx)
                            if (image != null) {
                                val frame = YuvFrame.from(image)
                                image.close()
                                val keepGoing = onFrame(frameIndex, info.presentationTimeUs, frame)
                                frameIndex++
                                if (!keepGoing) {
                                    codec.releaseOutputBuffer(outIdx, false)
                                    break
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        return frameIndex
    }

    /** Frequence reelle encodee dans le conteneur (peut valoir 30 pour un fichier "slow motion"). */
    fun containerFrameRate(): Double {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(path)
            var fps = 0.0
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        fps = f.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
                    }
                }
            }
            fps
        } catch (t: Throwable) {
            0.0
        } finally {
            extractor.release()
        }
    }
}
