package org.dp.facedetection

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.Camera
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.Utils
import org.opencv.core.MatOfRect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream


class MainActivity : AppCompatActivity() {

    val TAG = MainActivity::class.java.simpleName

    private var mCamera: Camera? = null
    private val mContext = this

    // Let's keep track of the display rotation and orientation also:
    private var mDisplayRotation: Int = 0
    private var mDisplayOrientation: Int = 0

    // Holds the Face Detection result:
    private val mFaces: Array<Camera.Face>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        // 权限是否已经 授权 GRANTED---授权  DINIED---拒绝
        if (permission != PackageManager.PERMISSION_GRANTED) {
            // 如果没有授予该权限，就去提示用户请求
            AlertDialog.Builder(this)
                .setTitle("权限")//标题
                .setMessage("")//内容
                //                        .setView()
                .setPositiveButton("确定") { dialog, which ->
                    val permissions = arrayOf(Manifest.permission.CAMERA)
                    ActivityCompat.requestPermissions(mContext, permissions, 321)
                }
                .setNegativeButton("取消", null)
                .show()
        }

//        testFacedetect()
    }

    fun testFacedetect(bmp:Bitmap) {
//        val bmp = getImageFromAssets("test3.jpg") ?: return
        var str = "image size = ${bmp.width}x${bmp.height}\n"
        ivCamera.setImageBitmap(bmp)
        val mat = MatOfRect()
        val bmp2 = bmp.copy(bmp.config, true)
        Utils.bitmapToMat(bmp, mat)
        val FACE_RECT_COLOR = Scalar(0.0, 0.0, 255.0)
        val FACE_RECT_THICKNESS = 3
        val startTime = System.currentTimeMillis()
        val facesArray = facedetect(mat.nativeObjAddr).filter { face -> face.faceConfidence > 93 }
        Log.e("aaa",facesArray.toString())
        str = str + "face num = ${facesArray.size}\n"
        for (face in facesArray) {
            Imgproc.rectangle(mat, face.faceRect, FACE_RECT_COLOR, FACE_RECT_THICKNESS)
        }
        str = str + "detectTime = ${System.currentTimeMillis() - startTime}ms\n"
        Utils.matToBitmap(mat, bmp2)
        ivCamera.setImageBitmap(bmp2)
        textView.text = str
    }

    /**
     * A native method that is implemented by the 'libfacedetection' native library,
     * which is packaged with this application.
     */
    external fun facedetect(matAddr: Long): Array<Face>


    companion object {

        // Used to load the 'facedetection' library on application startup.
        init {
            System.loadLibrary("facedetection")
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val holder = svCamera.getHolder()
        holder.addCallback(object : SurfaceHolder.Callback{
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
                // We have no surface, return immediately:
                if (holder?.surface == null) {
                    return
                }
                // Try to stop the current preview:
                try {
                    mCamera?.stopPreview()
                } catch (e: Exception) {
                    // Ignore...
                }

                configureCamera(width, height)
                setDisplayOrientation()
                setErrorCallback()

                // Everything is configured! Finally start the camera preview again:
                mCamera?.startPreview()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                mCamera?.setPreviewCallback(null)
                mCamera?.setFaceDetectionListener(null)
                mCamera?.setErrorCallback(null)
                mCamera?.release()
                mCamera = null
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                mCamera = Camera.open()
//        mCamera?.setFaceDetectionListener(faceDetectionListener)
                mCamera?.startFaceDetection()
                mCamera?.setPreviewCallback { data, camera ->
                    val size = camera.parameters.previewSize
                    try {
                        val image = YuvImage(data, ImageFormat.NV21, size.width, size.height, null)
                        if (image != null) {
                            val stream = ByteArrayOutputStream()
                            image.compressToJpeg(Rect(0, 0, size.width, size.height), 80, stream)

                            val bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size())

                            //**********************
                            //因为图片会放生旋转，因此要对图片进行旋转到和手机在一个方向上
                            rotateMyBitmap(bmp)
                            //**********************************

                            stream.close()
                        }
                    } catch (ex: Exception) {
                        Log.e("Sys", "Error:" + ex.message)
                    }

                }
                try {
                    mCamera?.setPreviewDisplay(holder)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not preview the image.", e)
                }

            }

        })
    }
    fun rotateMyBitmap(bmp: Bitmap) {
        Log.e("ddd","刷新了")
        //*****旋转一下
        val matrix = Matrix()
        matrix.postRotate(90F)

        val bitmap = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888)

        val nbmp2 = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true)

        //*******显示一下
//        ivCamera.setImageBitmap(nbmp2)
        testFacedetect(nbmp2)
    }

    override fun onPause() {
//        mOrientationEventListener?.disable()
        super.onPause()
    }

    override fun onResume() {
//        mOrientationEventListener?.enable()
        super.onResume()
    }

    private fun setErrorCallback() {
        mCamera?.setErrorCallback(object :Camera.ErrorCallback{
            override fun onError(error: Int, camera: Camera?) {

            }

        })
    }

    private fun setDisplayOrientation() {
        // Now set the display orientation:
        mDisplayRotation = Util.getDisplayRotation(this)
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, 0)

        mCamera?.setDisplayOrientation(mDisplayOrientation)

//        mFaceView?.setDisplayOrientation(mDisplayOrientation)
    }

    private fun configureCamera(width: Int, height: Int) {
        val parameters = mCamera?.getParameters()
        // Set the PreviewSize and AutoFocus:
        if (parameters != null) {
            setOptimalPreviewSize(parameters, width, height)
            setAutoFocus(parameters)
        }
        // And set the parameters:
        mCamera?.setParameters(parameters)
    }

    private fun setOptimalPreviewSize(cameraParameters: Camera.Parameters, width: Int, height: Int) {
        val previewSizes = cameraParameters.supportedPreviewSizes
        val targetRatio = width.toFloat() / height
        val previewSize = Util.getOptimalPreviewSize(this, previewSizes, targetRatio.toDouble())
        cameraParameters.setPreviewSize(previewSize!!.width, previewSize.height)
    }

    private fun setAutoFocus(cameraParameters: Camera.Parameters) {
        cameraParameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
    }


//    /**
//     * We need to react on OrientationEvents to rotate the screen and
//     * update the views.
//     */
//    private inner class SimpleOrientationEventListener(context: Context) :
//        OrientationEventListener(context, SensorManager.SENSOR_DELAY_NORMAL) {
//
//        override fun onOrientationChanged(orientation: Int) {
//            // We keep the last known orientation. So if the user first orient
//            // the camera then point the camera to floor or sky, we still have
//            // the correct orientation.
//            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
//            mOrientation = Util.roundOrientation(orientation, mOrientation)
//            // When the screen is unlocked, display rotation may change. Always
//            // calculate the up-to-date orientationCompensation.
//            val orientationCompensation = mOrientation + Util.getDisplayRotation(mContext)
//            if (mOrientationCompensation != orientationCompensation) {
//                mOrientationCompensation = orientationCompensation
//                mFaceView?.setOrientation(mOrientationCompensation)
//            }
//        }
//    }
}