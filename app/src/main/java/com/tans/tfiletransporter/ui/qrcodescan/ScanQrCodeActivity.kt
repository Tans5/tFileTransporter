package com.tans.tfiletransporter.ui.qrcodescan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.getSystemService
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.tans.tfiletransporter.R
import com.tans.tfiletransporter.databinding.ScanQrcodeActivityBinding
import com.tans.tfiletransporter.logs.AndroidLog
import com.tans.tuiutils.activity.BaseCoroutineStateActivity
import com.tans.tuiutils.permission.permissionsRequestSimplifySuspend
import com.tans.tuiutils.systembar.annotation.FullScreenStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean


@FullScreenStyle
class ScanQrCodeActivity : BaseCoroutineStateActivity<Unit>(defaultState = Unit) {

    override val layoutId: Int =  R.layout.scan_qrcode_activity


    override fun CoroutineScope.firstLaunchInitDataCoroutine() {}

    @Suppress("DEPRECATION")
    override fun CoroutineScope.bindContentViewCoroutine(contentView: View) {
        val viewBinding = ScanQrcodeActivityBinding.bind(contentView)
        launch {
            val granted = permissionsRequestSimplifySuspend(Manifest.permission.CAMERA)
            if (granted) {
                viewBinding.scanLineView.post {
                    val toY = viewBinding.scanLineView.measuredWidth.toFloat()
                    val animation = TranslateAnimation(0f, 0f, 0f, toY)
                    animation.duration = 1000
                    animation.repeatCount = Animation.INFINITE
                    animation.repeatMode = Animation.REVERSE
                    viewBinding.scanLineView.startAnimation(animation)
                }

                val cameraProvider = withContext(Dispatchers.IO) {
                    ProcessCameraProvider.getInstance(this@ScanQrCodeActivity).get()
                }
                val preview = Preview.Builder().build()
                viewBinding.previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                preview.setSurfaceProvider(viewBinding.previewView.surfaceProvider)

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setBackgroundExecutor(Dispatchers.IO.asExecutor())
                    .build()
                val barcodeOptions = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val barcodeScanningClient = BarcodeScanning.getClient(barcodeOptions)
                val hasFindQRCode = AtomicBoolean(false)
                analysis.setAnalyzer(Dispatchers.IO.asExecutor()) { imageProxy ->
                    val imageBitmap = imageProxy.toBitmap()
                    if (!hasFindQRCode.get()) {
                        val barcodeInputImage = InputImage.fromBitmap(imageBitmap, imageProxy.imageInfo.rotationDegrees)
                        barcodeScanningClient.process(barcodeInputImage)
                            .addOnSuccessListener {
                                if (it != null) {
                                    for (barcode in it) {
                                        AndroidLog.d(TAG, "Find qrcode: ${barcode.rawValue}")
                                    }
                                }
                                if (it != null && it.isNotEmpty() && hasFindQRCode.compareAndSet(false, true)) {
                                    runOnUiThread {
                                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            this@ScanQrCodeActivity.getSystemService<VibratorManager>()?.defaultVibrator
                                        } else {
                                            this@ScanQrCodeActivity.getSystemService<Vibrator>()
                                        }
                                        if (vibrator != null) {
                                            try {
                                                val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                                                vibrator.vibrate(effect)
                                            } catch (iae: IllegalArgumentException) {
                                                AndroidLog.e(TAG, "Vibrator error: ${iae.message}", iae)
                                            }
                                        }
                                        val barcodeStrings = it.map { b-> b.rawValue }.toTypedArray()
                                        val i = Intent()
                                        i.putExtra(QR_CODE_RESULT_KEY, barcodeStrings)
                                        setResult(Activity.RESULT_OK, i)
                                        finish()
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                            this@ScanQrCodeActivity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0, 0)
                                        } else {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                this@ScanQrCodeActivity.overridePendingTransition(0, 0, 0)
                                            } else {
                                                this@ScanQrCodeActivity.overridePendingTransition(0, 0)
                                            }
                                        }
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