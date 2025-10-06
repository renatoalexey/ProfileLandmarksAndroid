package com.example.profilelandmarks

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.util.Log
import android.widget.ImageView
import android.net.Uri
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mlkit.vision.common.InputImage
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class MediaPipeLandmarks : ComponentActivity() {

    private lateinit var overlayView: OverlayView
    private lateinit var imageView: ImageView
    private lateinit var bitmap: Bitmap
    private lateinit var detector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mediapipe)

        imageView = findViewById(R.id.imageView)
        overlayView = findViewById(R.id.overlayView)

        // Carregar imagem .jpg da pasta assets
        val inputStream: InputStream = assets.open("01.jpg")
        bitmap = BitmapFactory.decodeStream(inputStream)
        imageView.setImageBitmap(bitmap)

        val image = InputImage.fromBitmap(bitmap, 0)

        // Configurações do detector
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath("blaze_face_short_range.tflite")
        val baseOptions = baseOptionsBuilder.build()

        val optionsBuilder =
            FaceDetector.FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                //.setMinDetectionConfidence(threshold)
                .setRunningMode(RunningMode.IMAGE)

        val options = optionsBuilder.build()

        detector = FaceDetector.createFromOptions(this, options)

        // 3) Aguarda o layout do ImageView para termos sizes e scaleType já definidos
        imageView.post {
            runDetectionAndMap()
        }
    }

    private fun runDetectionAndMap () {
        // Converte o Bitmap para MediaPipe Image
        val mpImage = BitmapImageBuilder(bitmap).build()
        // Executa a detecção

        val result: FaceDetectorResult = detector.detect(mpImage)
        val points = mutableListOf<Triple<Float, Float, Int>>()

        val params = computeBitmapToViewTransform(imageView, bitmap.width, bitmap.height)
        val scaleX = params[0]; val scaleY = params[1]; val transX = params[2]; val transY = params[3]

        // Percorre os rostos detectados
        for ((i, face) in result.detections().withIndex()) {
            Log.d("MediaPipe", "Face $i bbox: ${face.boundingBox()}")

            // Pontos de referência principais
            val keyPoints = face.keypoints()
            for(keypoint in keyPoints.get()) {
                Log.d("MediaPipe", "  Ponto $: (${keypoint.x()}, ${keypoint.y()})")
            }

            var keyPointCount = 1

            for(keypoint in keyPoints.get()) {

                val xRaw = keypoint.x()
                val yRaw = keypoint.y()

                val (px, py) = if (xRaw <= 1.0f && yRaw <= 1.0f) {
                    // normalizado -> converte para pixels no bitmap
                    Pair(xRaw * bitmap.width.toFloat(), yRaw * bitmap.height.toFloat())
                } else {
                    // já está em pixels
                    Pair(xRaw, yRaw)
                }

                // mapeia do espaço do bitmap -> espaço do ImageView/overlay
                val viewX = transX + px * scaleX
                val viewY = transY + py * scaleY

                val (mappedX, mappedY) = mapPointToImageView(
                    imageView,
                    viewX,
                    viewY
                )

                //Log.d(TAG, "kp idx=$index raw=($xRaw,$yRaw) bmpPx=($px,$py) view=($viewX,$viewY)")
                points.add(Triple(viewX, viewY, keyPointCount))
                points.add(Triple(mappedX, mappedY, keyPointCount))
                keyPointCount++
            }
        }

        if (!isFinishing && !isDestroyed) {
            overlayView.setPoints(points)
        }

        detector.close()
    }

    private fun computeBitmapToViewTransform(imageView: ImageView, bmpW: Int, bmpH: Int): FloatArray {
        val viewW = imageView.width.toFloat()
        val viewH = imageView.height.toFloat()
        if (viewW == 0f || viewH == 0f) {
            // fallback para identity
            return floatArrayOf(1f, 1f, 0f, 0f)
        }

        val scaleType = imageView.scaleType
        when (scaleType) {
            ImageView.ScaleType.CENTER_CROP -> {
                val scale = max(viewW / bmpW.toFloat(), viewH / bmpH.toFloat())
                val scaleX = scale; val scaleY = scale
                val transX = (viewW - bmpW * scaleX) / 2f
                val transY = (viewH - bmpH * scaleY) / 2f
                return floatArrayOf(scaleX, scaleY, transX, transY)
            }
            ImageView.ScaleType.FIT_XY -> {
                val scaleX = viewW / bmpW.toFloat()
                val scaleY = viewH / bmpH.toFloat()
                return floatArrayOf(scaleX, scaleY, 0f, 0f)
            }
            ImageView.ScaleType.FIT_CENTER,
            ImageView.ScaleType.CENTER_INSIDE,
            ImageView.ScaleType.FIT_START,
            ImageView.ScaleType.FIT_END -> {
                val scale = min(viewW / bmpW.toFloat(), viewH / bmpH.toFloat())
                val scaleX = scale; val scaleY = scale
                // for FIT_START/END we approximate center for simplicity; if you really need start/end, adapt.
                val transX = (viewW - bmpW * scaleX) / 2f
                val transY = (viewH - bmpH * scaleY) / 2f
                return floatArrayOf(scaleX, scaleY, transX, transY)
            }
            ImageView.ScaleType.CENTER -> {
                val scaleX = 1f; val scaleY = 1f
                val transX = (viewW - bmpW) / 2f
                val transY = (viewH - bmpH) / 2f
                return floatArrayOf(scaleX, scaleY, transX, transY)
            }
            ImageView.ScaleType.MATRIX -> {
                // Use imageMatrix (mais preciso se você aplicou matrix manualmente)
                val m = FloatArray(9)
                imageView.imageMatrix.getValues(m)
                val scaleX = m[Matrix.MSCALE_X]
                val scaleY = m[Matrix.MSCALE_Y]
                val transX = m[Matrix.MTRANS_X]
                val transY = m[Matrix.MTRANS_Y]
                return floatArrayOf(scaleX, scaleY, transX, transY)
            }
            else -> {
                // fallback: tenta imageMatrix
                val m = FloatArray(9)
                imageView.imageMatrix.getValues(m)
                val scaleX = m[Matrix.MSCALE_X]
                val scaleY = m[Matrix.MSCALE_Y]
                val transX = m[Matrix.MTRANS_X]
                val transY = m[Matrix.MTRANS_Y]
                return floatArrayOf(scaleX, scaleY, transX, transY)
            }
        }
    }

    private fun mapPointToImageView(
        imageView: ImageView,
        x: Float,
        y: Float
    ): Pair<Float, Float> {
        val matrix = imageView.imageMatrix
        val values = FloatArray(9)
        matrix.getValues(values)

        val scaleX = values[Matrix.MSCALE_X]
        val scaleY = values[Matrix.MSCALE_Y]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        val mappedX = x * scaleX + transX
        val mappedY = y * scaleY + transY

        return Pair(mappedX, mappedY)
    }
}