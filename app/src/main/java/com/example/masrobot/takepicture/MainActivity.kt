package com.example.masrobot.takepicture

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.progressDialog
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.toast
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.karumi.dexter.Dexter
import com.karumi.dexter.listener.PermissionRequest


class MainActivity : AppCompatActivity() {

    var filePath: Uri? = null
    val CAMERA_REQUEST_CODE = 0
    val PICK_PHOTO_CODE = 1046
    var imageFilePath: String? = null

    internal var storage: FirebaseStorage? = null
    internal var storageReference: StorageReference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        validatePermissions()

        // init firebase
        storage = FirebaseStorage.getInstance()
        storageReference = storage?.reference

        btnChoose.onClick {
            alert("Take Photo or Choose from Gallery") {
                positiveButton("Choose from Gallery") {
                    showFileChooser()
                }
                negativeButton("Take from Camera") {
                    takePhoto()
                }
            }.show()
        }

        btnUpload.onClick {
            uploadFile()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when(requestCode) {
            CAMERA_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) imageView.setImageBitmap(setScaledBitmap())
            }
            PICK_PHOTO_CODE -> {
                if (data != null) {
                    filePath = data.data

                    val selectedImage = MediaStore.Images.Media.getBitmap(this.contentResolver, filePath)

                    imageView.setImageBitmap(selectedImage)
                }
            }
            else -> toast("Unrecognized request code")
        }
    }

    // permissions use dexter plugin
    private fun validatePermissions() {
        Dexter.withActivity(this)
                .withPermissions(
                        Manifest.permission.CAMERA,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                ).withListener(object : MultiplePermissionsListener {
            override fun onPermissionsChecked(report: MultiplePermissionsReport) {

            }

            override fun onPermissionRationaleShouldBeShown(permissions: List<PermissionRequest>,
                                                            token: PermissionToken) {
                alert("Permissions for this Application") {
                    title = "Permissions Required"
                    positiveButton("Ok") {
                        dialog -> dialog.dismiss()
                        token?.continuePermissionRequest()
                    }
                    negativeButton("Cancel") {
                        dialog -> dialog.dismiss()
                        token?.cancelPermissionRequest()
                    }
                }
            }
        }).check()
    }

    // call File Manager or Gallery Internal / External Storage
    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, PICK_PHOTO_CODE)
        }
    }

    // call camera intent
    private fun takePhoto() {
        try {
            val imageFile = createImageFile()
            val callCameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

            if (callCameraIntent.resolveActivity(packageManager) != null) {
                val authorities = packageName + ".fileprovider"

                filePath = FileProvider.getUriForFile(this@MainActivity,
                        authorities, imageFile)
                callCameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, filePath)
                startActivityForResult(callCameraIntent, CAMERA_REQUEST_CODE)
            }
        } catch (e: IOException) {
            toast("Could not create file")
        }
    }

    // Temporary for preview image/bitmap not save to local storage (internal / external)
    @Throws(IOException::class)
    fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName: String = "JPEG_" + timeStamp + "_"
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        if(!storageDir.exists()) storageDir.mkdirs()
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        imageFilePath = imageFile.absolutePath
        return imageFile
    }

    // set scaled image/bitmap
    fun setScaledBitmap(): Bitmap {
        val imageViewWidth = imageView.width
        val imageViewHeight = imageView.height

        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(imageFilePath, bmOptions)
        val bitmapWidth = bmOptions.outWidth
        val bitmapHeight = bmOptions.outHeight

        val scaleFactor = Math.min(bitmapWidth/imageViewWidth, bitmapHeight/imageViewHeight)

        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor

        return BitmapFactory.decodeFile(imageFilePath, bmOptions)

    }

    // function upload to firebase
    private fun uploadFile() {
        if (filePath != null) {
            val progressDialog = progressDialog(title = "Uploading...")
            progressDialog.show()

            val imageRef = storageReference?.child("images/${UUID.randomUUID().toString()}")
            imageRef?.putFile(filePath!!)
                    ?.addOnSuccessListener {
                        progressDialog.dismiss()
                        toast("File Uploaded")
                    }
                    ?.addOnFailureListener {
                        progressDialog.dismiss()
                        toast("Failed")
                    }
                    ?.addOnProgressListener { taskSnapshot ->
                        val progress = 100.0 * taskSnapshot.bytesTransferred/taskSnapshot.totalByteCount
                        progressDialog.setMessage("Uploaded" + progress.toInt() + "%...")
                    }
        }
    }
}
