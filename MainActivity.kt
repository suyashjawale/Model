package com.example.model

import android.Manifest
import android.R.string
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.edit_text_layout.view.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@Suppress("NAME_SHADOWING", "DEPRECATION")
class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
        camera_capture_button.setOnClickListener { takePhoto() }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,"demo.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException)
                {
//
                }

                @SuppressLint("SdCardPath")
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                    val savedUri = Uri.fromFile(photoFile)
                    val output=savedUri.toString().replace("file://","")
                    alert(output)


                }
            })
    }

    private fun mark(src: Bitmap, watermark: String): Bitmap? {
        var w = src.width
        var h = src.height
        if(w>h) {

            val x = h
            h = w
            w = x
        }

        val result = Bitmap.createBitmap(w, h, src.config)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        val rect = RectF(0F, 200F, (w - 0).toFloat(), (h - 650).toFloat())
        val borderWidth = 500.0f // ditto
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.strokeWidth = borderWidth
        paint.style = Paint.Style.STROKE
        canvas.drawRect(rect,paint)
        paint.strokeWidth = 60F
        paint.style = Paint.Style.FILL
        canvas.drawRect(0F, (h-650).toFloat(), w.toFloat(), h.toFloat(),paint)
        paint.color = Color.BLACK
        val wmm= watermark.toLowerCase(Locale.ROOT)
        paint.textSize= 258F
        var ln=paint.measureText(wmm)
        while (w-ln<123)
        {
            paint.textSize=paint.textSize-1
            ln=paint.measureText(wmm)
        }
        canvas.drawText(wmm,(w-ln)/2.toFloat(), h-500F, paint)
        paint.textSize= 200F
        paint.color = Color.rgb(120,120,120)
        val txt1="Model Number"
        canvas.drawText(txt1, (w-paint.measureText(txt1))/2, 320F, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(0,116,217)
        canvas.drawRect(10F, 10F, w-10.toFloat(), h-10.toFloat(),paint)
        return result
    }

    @SuppressLint("SdCardPath")
    private fun alert(output: String) {
        val builder = AlertDialog.Builder(this)
        val elan=layoutInflater.inflate(R.layout.edit_text_layout,null)
        elan.findViewById<EditText>(R.id.username)
        builder.setTitle("Enter Model No.")
        builder.setPositiveButton(string.yes) { _, _ ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
            }

            val exif = ExifInterface(output)
            val orientation: Int = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
            val matrix = Matrix()
            when (orientation) {
                6 -> {
                    matrix.postRotate(90F)
                }
                3 -> {
                    matrix.postRotate(180F)
                }
                8 -> {
                    matrix.postRotate(270F)
                }
            }

            val bitmap: Bitmap = BitmapFactory.decodeFile(output, options)
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            val result = mark(rotatedBitmap,"${elan.username.text}")
            val path=getOutputDirectory()
            val file = File("/${path}","${elan.username.text}.jpg")
            val out = FileOutputStream(file)
            result?.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()


            Toast.makeText(baseContext,"Saved", Toast.LENGTH_SHORT).show()
            sendBroadcast(
                Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(File("/${path}/${elan.username.text}.jpg"))
                )
            )
        }
        builder.setNegativeButton(string.no) { _, _ ->
            //aa
        }
        builder.setView(elan)
        builder.show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }
            imageCapture = ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                //
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA,Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}