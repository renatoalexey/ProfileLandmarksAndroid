package com.example.profilelandmarks

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import java.io.InputStream

class MainActivity : ComponentActivity() {
    private lateinit var overlayView: OverlayView
    private lateinit var imageView: ImageView
    private lateinit var bitmap: Bitmap
    private lateinit var detector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val assetManager = assets
        val foldersPath = "new_cfp/right"
        val folders = assetManager.list(foldersPath)

        if (folders != null) {
            for (folder in folders) {

                Log.d("Assets", "Encontrei: $folder")

                val files = assetManager.list("minhaPasta/$folder")
                    ?.filter { it.endsWith(".jpg", ignoreCase = true) }  // 🔑 só pega JPG
                    ?: emptyList()

                for (file in files) {
                    val path = "$foldersPath/$folder/$file"

                    // 3) Abrir a imagem
                    val inputStream = assetManager.open(path)
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    Log.d("Assets", "Carreguei $path com tamanho: ${bitmap.width}x${bitmap.height}")
            }
        }
            }

        imageView = findViewById(R.id.imageView)
        overlayView = findViewById(R.id.overlayView)

        // Carregar imagem .jpg da pasta assets
        val inputStream: InputStream = assets.open("01_f.jpg")
        bitmap = BitmapFactory.decodeStream(inputStream)
        imageView.setImageBitmap(bitmap)

        val image = InputImage.fromBitmap(bitmap, 0)

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        detector = FaceDetection.getClient(options)

        // Usar post {} para garantir que o ImageView já foi medido
        imageView.post {
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (!isFinishing && !isDestroyed) {
                        processFaces(faces)
                    }
                }
                .addOnFailureListener { e ->
                    if (!isFinishing && !isDestroyed) {
                        Log.e("MLKit", "Erro: ${e.message}")
                    }
                }
        }
    }

    private fun processFaces(faces: List<Face>) {
        for ((faceIndex, face) in faces.withIndex()) {
            Log.d("MLKit", "Face #$faceIndex detectada")

            val landmarks = listOf(
                FaceLandmark.MOUTH_BOTTOM,
                FaceLandmark.MOUTH_LEFT,
                FaceLandmark.MOUTH_RIGHT,
                FaceLandmark.LEFT_EYE,
                FaceLandmark.RIGHT_EYE,
                FaceLandmark.LEFT_EAR,
                FaceLandmark.RIGHT_EAR,
                FaceLandmark.LEFT_CHEEK,
                FaceLandmark.RIGHT_CHEEK,
                FaceLandmark.NOSE_BASE
            )

            val points = mutableListOf<Triple<Float, Float, Int>>()
            var count = 1
            /*for (landmarkType in landmarks) {
                val landmark = face.getLandmark(landmarkType)
                landmark?.let {
                    val (mappedX, mappedY) = mapPointToImageView(
                        imageView,
                        bitmap,
                        it.position.x,
                        it.position.y
                    )
                    points.add(Triple(mappedX, mappedY, count))
                    count++
                }
            }*/

            val allContours = face.allContours

            for(contour in allContours) {
                //if(contour.faceContourType in intArrayOf(FaceContour.FACE)) {
                    for(contourPoint in contour.points) {
                        //if(count !in intArrayOf(100, 93, 107, 119, 123)) {
                        if(count < 19 ) {
                                count ++
                                continue
                        }
                        val (mappedX, mappedY) = mapPointToImageView(
                            imageView,
                            bitmap,
                            contourPoint.x,
                            contourPoint.y
                        )
                        points.add(Triple(mappedX, mappedY, count))
                        count++
                    }
                //}
                //else count += contour.points.size
            }

            if (!isFinishing && !isDestroyed) {
                overlayView.setPoints(points)
            }
        }
    }

    // Função para converter coordenadas do bitmap para coordenadas reais no ImageView
    private fun mapPointToImageView(
        imageView: ImageView,
        bitmap: Bitmap,
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

    override fun onDestroy() {
        super.onDestroy()
        detector.close() // Libera recursos do ML Kit
    }
}