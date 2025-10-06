package com.example.profilelandmarks

import android.content.Context
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
import com.example.profilelandmarks.service.CfpManager
import com.example.profilelandmarks.service.client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.OkHttpClient
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private lateinit var overlayView: OverlayView
    private lateinit var imageView: ImageView
    private lateinit var bitmap: Bitmap
    private lateinit var detector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cfpManager = CfpManager()

        //val btn = findViewById<Button>(R.id.btnGoToSecond)
        /*btn.setOnClickListener {
            val intent = Intent(this, MediaPipeLandmarks::class.java)
            startActivity(intent)
        }*/

        imageView = findViewById(R.id.imageView)
        overlayView = findViewById(R.id.overlayView)

        // Carregar imagem .jpg da pasta assets
        val inputStream: InputStream = assets.open("04.jpg")
        bitmap = BitmapFactory.decodeStream(inputStream)

        bitmap = resizeImage(bitmap, 515, 580)

        imageView.setImageBitmap(bitmap)

        val image = InputImage.fromBitmap(bitmap, 0)

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

        //getMediaPipeLandmarks(image, bitmap)
        detector = FaceDetection.getClient(options)

       runMLKit(detector)

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

    private fun resizeImage(
        bitmap: Bitmap,
        width: Int,
        height: Int
    ): Bitmap {
         return Bitmap.createScaledBitmap(
            bitmap,
            width,
            height,
            true // filter = smooth scaling
        )
    }
    override fun onDestroy() {
        super.onDestroy()
        detector.close() // Libera recursos do ML Kit
    }

    fun runMLKit(faceDetector: FaceDetector) {
        try {
            assets.open("cfp.txt").bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val image = getImageFromPath(line)
                    println(line) // aqui você itera linha por linha

                    // 2. Troca extensão para ".txt"
                    val fiducial = line.replace("Images", "Fiducials").replaceAfterLast(".", "txt")

                    callApi(String.format("ground/truth/points?fiducials_folder=%s",
                        String.format("F:\\Bases\\cfp-dataset\\Data\\%s", fiducial))) { resposta ->
                        println("Resposta da soma: $resposta")
                    }
                }
            }
        } catch (e: Exception){
            e.printStackTrace()
        }
        //reader.close()
    }

    fun callApi(endpoint: String, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // coloque o IP do PC onde roda o Python
                val request = Request.Builder()
                    .url(String.format("http://192.168.0.34:5000/%s", endpoint))
                    .build()

                val serverResponse: Response = client.newCall(request).execute()
                val response = serverResponse.body?.string()

                withContext(Dispatchers.Main) {
                    callback(response)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

    private fun getImageFromPath(imagePath: String
    ): InputImage {
        try {

            val inputStream: InputStream = assets.open(imagePath)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            //val bitmap = carregarBitmapWindows(context, imagePath)
            if (bitmap != null)
                return InputImage.fromBitmap(bitmap, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return InputImage.fromBitmap(BitmapFactory.decodeFile(imagePath), 0)
    }
}