package com.tans.tfiletransporter.ui.activity.qrcodescan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Vibrator
import android.util.Size
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ScanQrcodeActivityBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tfiletransporter.ui.activity.BaseActivity
import com.tbruyelle.rxpermissions3.RxPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean


class ScanQrCodeActivity : BaseActivity<ScanQrcodeActivityBinding, Unit>(
    layoutId = R.layout.scan_qrcode_activity,
    defaultState = Unit
) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun initViews(binding: ScanQrcodeActivityBinding) {
        launch {
            val permissionGrant = RxPermissions(this@ScanQrCodeActivity)
                .request(Manifest.permission.CAMERA)
                .firstOrError()
                .await()
            if (permissionGrant) {

                binding.scanLineView.post {
                    val toY = binding.scanLineView.measuredWidth.toFloat()
                    val animation = TranslateAnimation(0f, 0f, 0f, toY)
                    animation.duration = 1000
                    animation.repeatCount = Animation.INFINITE
                    animation.repeatMode = Animation.REVERSE
                    binding.scanLineView.startAnimation(animation)
                }

                val cameraProvider = withContext(Dispatchers.IO) {
                    ProcessCameraProvider.getInstance(this@ScanQrCodeActivity).get()
                }
                val preview = Preview.Builder()
                    .build()
                binding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                preview.setSurfaceProvider(binding.previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(640, 320))
                    .setBackgroundExecutor(Dispatchers.IO.asExecutor())
                    .build()
                val barcodeOptions = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val barcodeScanningClient = BarcodeScanning.getClient(barcodeOptions)
                val hasFindQRCode = AtomicBoolean(false)
                analysis.setAnalyzer(Dispatchers.IO.asExecutor()) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        if (!hasFindQRCode.get()) {
                            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                            barcodeScanningClient.process(image)
                                .addOnSuccessListener {
                                    if (it != null) {
                                        for (barcode in it) {
                                            AndroidLog.d(TAG, "Find qrcode: ${barcode.rawValue}")
                                        }
                                    }
                                    if (it != null && it.isNotEmpty() && hasFindQRCode.compareAndSet(false, true)) {
                                        runOnUiThread {
                                            val vibrator = this@ScanQrCodeActivity.getSystemService(VIBRATOR_SERVICE) as Vibrator
                                            vibrator.vibrate(100)
                                            val barcodeStrings = it.map { b-> b.rawValue }.toTypedArray()
                                            val i = Intent()
                                            i.putExtra(QR_CODE_RESULT_KEY, barcodeStrings)
                                            setResult(Activity.RESULT_OK, i)
                                            finish()
                                            this@ScanQrCodeActivity.overridePendingTransition(0, 0)
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    AndroidLog.e(TAG, "Find qrcode error: ${it.message}")
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    } else {
                        imageProxy.close()
                    }
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this@ScanQrCodeActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Throwable) {
                    AndroidLog.e(TAG, "Start camera error: ${e.message}", e)
                }

            } else {
                AndroidLog.e(TAG, "Permission not grant.")
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "ScanQrCodeActivity"
        private const val QR_CODE_RESULT_KEY = "QR_CODE_RESULT_KEY"

        fun getResult(data: Intent): List<String> {
            return (data.getStringArrayExtra(QR_CODE_RESULT_KEY) ?: emptyArray()).toList()
        }
    }
}