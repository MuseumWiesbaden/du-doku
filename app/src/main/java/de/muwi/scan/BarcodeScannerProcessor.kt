package de.muwi.scan

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeScannerProcessor(private val listener: BarcodeListener) :
    ImageAnalysis.Analyzer {

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        val img = image.image
        if (img != null) {
            val inputImage = InputImage.fromMediaImage(img, image.imageInfo.rotationDegrees)

            val options =
                BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128).build()

            val scanner = BarcodeScanning.getClient(options)

            scanner.process(inputImage).addOnSuccessListener { barcodes ->
                for (code in barcodes) {
                    listener(code)
                }
            }.addOnFailureListener {
                Log.e("muwi", "Barcode failure $it")
            }.addOnCompleteListener {
                //Log.v("muwi", "Completed Processing image " + System.currentTimeMillis())
                image.close()
            }
        }
    }
}