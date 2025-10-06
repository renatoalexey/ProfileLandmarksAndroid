package com.example.profilelandmarks.service
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetector
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

val client = OkHttpClient()

class CfpManager {

    fun callApi(endpoint: String, callback: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // coloque o IP do PC onde roda o Python
                val request = Request.Builder()
                    .url(String.format("http://192.168.0.39:5000/%s", endpoint))
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
    fun runMLKit(context: Context, faceDetector: FaceDetector) {

        try {
            context.assets.open("cfp.txt").bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val image = getImageFromPath(context, line)
                    println(line) // aqui você itera linha por linha

                    // 2. Troca extensão para ".txt"
                    val fiducial = line.replace("Images", "Fiducials").replaceAfterLast(".", "txt")

                    callApi("ground/truth/points") { resposta ->
                        println("Resposta da soma: $resposta")
                    }
                }
            }
        } catch (e: Exception){
            e.printStackTrace()
        }
        //reader.close()
    }

    private fun getImageFromPath(context: Context,
                                 imagePath: String
    ): InputImage {
        try {

            val inputStream: InputStream = context.assets.open(imagePath)
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