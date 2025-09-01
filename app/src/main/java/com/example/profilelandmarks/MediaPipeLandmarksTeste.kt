package com.example.profilelandmarks

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import android.util.Log
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mlkit.vision.common.InputImage

class MediaPipeLandmarksTeste : ComponentActivity() {

    public fun getMediaPipeLandmarks(image: InputImage, bitmap: Bitmap){
        // Configurações do detector
        val options = FaceDetector.FaceDetectorOptions.builder()
            .setRunningMode(RunningMode.IMAGE) // processa uma imagem estática
            .build()

        val detector = FaceDetector.createFromOptions(this, options)

        // Converte o Bitmap para MediaPipe Image
        val mpImage = BitmapImageBuilder(bitmap).build()
        // Executa a detecção
        val result: FaceDetectorResult = detector.detect(mpImage)

        // Percorre os rostos detectados
        for ((i, face) in result.detections().withIndex()) {
            Log.d("MediaPipe", "Face $i bbox: ${face.boundingBox()}")

            // Pontos de referência principais
            val cocos = face.keypoints()
            for(coco in cocos.get()) {
                Log.d("MediaPipe", "  Ponto $: (${coco.x()}, ${coco.y()})")
            }
        }

        detector.close()


    }

}