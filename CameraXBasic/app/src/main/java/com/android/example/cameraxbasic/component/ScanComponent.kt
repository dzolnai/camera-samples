/*
 * Copyright (c) 2010-2019 SURFnet bv
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of SURFnet bv nor the names of its contributors
 *    may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.example.cameraxbasic.component

import android.content.Context
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.android.example.cameraxbasic.analyzer.ScanAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Component to scan a QR code using [androidx.camera].
 * The processing of the camera image to a QR code is delegated to [ScanAnalyzer].
 */
class ScanComponent(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val viewFinder: PreviewView,
    viewFinderRatio: Float,
    private val scanResult: (result: String) -> Unit,
)  {
    //region Camera
    private lateinit var camera: Camera
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraPreview: Preview
    private val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
    private lateinit var cameraAnalysis: ImageAnalysis
    private val cameraAnalyzer = ScanAnalyzer(lifecycleOwner, viewFinderRatio, ::onDetected)
    //endregion

    private val lifecycleScope = lifecycleOwner.lifecycleScope

    init {
        lifecycleScope.launch {
            cameraProvider = initCameraProvider()
            startCamera(cameraProvider)
        }
    }

    /**
     * Initialize the [ProcessCameraProvider]
     */
    private suspend fun initCameraProvider(): ProcessCameraProvider =
        withContext(Dispatchers.Default) {
            ProcessCameraProvider.getInstance(context).await()
        }


    /**
     * Start the camera and QR code detection
     */
    private fun startCamera(cameraProvider: ProcessCameraProvider) {
        fun aspectRatio(width: Int, height: Int): Int {
            val previewRatio = max(width, height).toDouble() / min(width, height)
            return if (abs(previewRatio - 4.0 / 3.0) <= abs(previewRatio - 16.0 / 9.0)) {
                AspectRatio.RATIO_4_3
            } else {
                AspectRatio.RATIO_16_9
            }
        }

        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        cameraPreview = Preview.Builder()
            .setTargetName("tiqr QR scanner")
            .setResolutionSelector(ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy(aspectRatio(metrics.widthPixels, metrics.heightPixels), AspectRatioStrategy.FALLBACK_RULE_AUTO))
                .build()
            ).setTargetRotation(viewFinder.display.rotation)
            .build()

        cameraAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().apply {
            setAnalyzer(ContextCompat.getMainExecutor(context), cameraAnalyzer)
        }

        camera = cameraProvider.run {
            unbindAll()
            bindToLifecycle(lifecycleOwner, cameraSelector, cameraPreview, cameraAnalysis)
        }.apply {
            cameraPreview.setSurfaceProvider(viewFinder.surfaceProvider)
            Toast.makeText(context, "Camera started.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Stop the camera and QR code detection
     */
    private fun stopCamera() {
        cameraProvider.unbindAll()
    }

    /**
     * A QR code has been detected
     */
    private fun onDetected(qrCode: String) {
        stopCamera()
        scanResult.invoke(qrCode)
    }
}


