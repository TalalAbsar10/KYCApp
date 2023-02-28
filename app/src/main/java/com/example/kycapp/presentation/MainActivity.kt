package com.example.kycapp.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.example.kycapp.R
import com.example.kycapp.`interface`.CallBacksInterface
import com.example.kycapp.presentation.KYCFragment.Companion.RFID_RESULT
import com.example.kycapp.util.Constants.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
import com.example.kycapp.util.Constants.REQUEST_BROWSE_PICTURE
import com.example.kycapp.util.Constants.TAG_UI_FRAGMENT
import com.example.kycapp.util.Utils
import com.regula.documentreader.api.DocumentReader
import com.regula.documentreader.api.completions.IDocumentReaderCompletion
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion
import com.regula.documentreader.api.completions.IDocumentReaderPrepareCompletion
import com.regula.documentreader.api.enums.DocReaderAction
import com.regula.documentreader.api.enums.Scenario
import com.regula.documentreader.api.errors.DocumentReaderException
import com.regula.documentreader.api.params.DocReaderConfig
import com.regula.documentreader.api.results.DocumentReaderResults


class MainActivity : AppCompatActivity(), CallBacksInterface {

    private var loadingDialog: AlertDialog? = null
    private lateinit var kycFragment: KYCFragment
    private lateinit var documentReaderResults: DocumentReaderResults

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fragment = findFragmentByTag(TAG_UI_FRAGMENT)
        if (fragment == null) {
            kycFragment = KYCFragment()
            replaceFragment(kycFragment, false)
        } else {
            kycFragment = fragment as KYCFragment
        }

        if (DocumentReader.Instance().isReady) {
            documentReaderSetupInit()
            return
        }

        showDialog("Preparing database")

        //preparing database files, it will be downloaded from network only one time and stored on user device
        DocumentReader.Instance().prepareDatabase(
            this@MainActivity,
            "Full",
            object : IDocumentReaderPrepareCompletion {
                override fun onPrepareProgressChanged(progress: Int) {
                    setTitleDialog("Downloading database: $progress%")
                }

                override fun onPrepareCompleted(
                    status: Boolean,
                    error: DocumentReaderException?
                ) {
                    if (status) {
                        onPrepareDbCompleted()
                    } else {
                        dismissDialog()
                        Toast.makeText(
                            this@MainActivity,
                            "Prepare DB failed:$error",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
    }

    fun onPrepareDbCompleted() {
        initializeDocumentReader()
    }

    private fun initializeDocumentReader() {
        val license = Utils.getLicense(this) ?: return

        showDialog("Initializing")
        val config = DocReaderConfig(license)
        config.setLicenseUpdate(true)

        //Initializing the reader
        DocumentReader.Instance().initializeReader(this@MainActivity, config, docReaderInitCompletion)
    }

    protected val docReaderInitCompletion =
        IDocumentReaderInitCompletion { result: Boolean, error: DocumentReaderException? ->
            dismissDialog()
            if (!result) { //Initialization was not successful
                kycFragment?.disableUiElements()
                Toast.makeText(this@MainActivity, "Init failed:$error", Toast.LENGTH_LONG).show()
                return@IDocumentReaderInitCompletion
            }
            documentReaderSetupInit()
        }

    protected fun documentReaderSetupInit() {
        setupCustomization()
        setupFunctionality()
        setupProcessParams()
        setupScenarios()
    }

    fun setupScenarios() {
        if (DocumentReader.Instance().processParams().scenario.isEmpty())
            DocumentReader.Instance().processParams().scenario = Scenario.SCENARIO_OCR
    }

    private fun setupCustomization() {
        DocumentReader.Instance().customization().edit().setShowHelpAnimation(false).apply()
    }

    private fun setupFunctionality() {
        DocumentReader.Instance().functionality().edit()
            .setShowCameraSwitchButton(true)
            .apply()
    }

    private fun setupProcessParams() {
        DocumentReader.Instance().processParams().multipageProcessing = true
    }

    //Open the camera and scans the document
    override fun scanDocument() {
        if (!DocumentReader.Instance().isReady) return
        DocumentReader.Instance().showScanner(this@MainActivity, completion)
    }

    @SuppressLint("MissingPermission")
    private val completion =
        IDocumentReaderCompletion { action, results, error ->
            //Processing is finished, all results are ready
            if (action == DocReaderAction.COMPLETE || action == DocReaderAction.TIMEOUT) {
                dismissDialog()
                kycFragment.displayResults(results)
            } else {
                //something happened before all results were ready
                if (action == DocReaderAction.CANCEL) {
                    Toast.makeText(this@MainActivity, "Scanning was cancelled", Toast.LENGTH_LONG)
                        .show()
                } else if (action == DocReaderAction.ERROR) {
                    Toast.makeText(this@MainActivity, "Error:$error", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun recognizeImage() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
            )
        } else {
            //start image browsing
            createImageBrowsingRequest()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RFID_RESULT) {
            if (documentReaderResults != null)
                kycFragment.displayResults(documentReaderResults)
        }

        //Image browsing intent processed successfully
        if (resultCode != RESULT_OK || requestCode != REQUEST_BROWSE_PICTURE || data!!.data == null) return

        val selectedImage = data.data
        val bmp: Bitmap? = Utils.getBitmap(contentResolver, selectedImage, 1920, 1080)
        showDialog("Processing image")
        DocumentReader.Instance().recognizeImage(bmp!!, completion)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    //access to gallery is allowed
                    createImageBrowsingRequest()
                } else {
                    Toast.makeText(this, "Permission required, to browse images", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    }

    // Creates and starts image browsing intent
    // Results will be handled in onActivityResult method
    private fun createImageBrowsingRequest() {
        val intent = Intent()
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select Picture"),
            REQUEST_BROWSE_PICTURE
        )
    }

    protected fun setTitleDialog(msg: String?) {
        if (loadingDialog != null) {
            loadingDialog!!.setTitle(msg)
        } else {
            showDialog(msg)
        }
    }

    protected fun dismissDialog() {
        if (loadingDialog != null) {
            loadingDialog!!.dismiss()
        }
    }

    protected fun showDialog(msg: String?) {
        dismissDialog()
        val builderDialog = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.progress_bar, null)
        builderDialog.setTitle(msg)
        builderDialog.setView(dialogView)
        builderDialog.setCancelable(false)
        loadingDialog = builderDialog.show()
    }

    private fun findFragmentByTag(tag: String?): Fragment? {
        val fm = supportFragmentManager
        return fm.findFragmentByTag(tag)
    }

    private fun replaceFragment(fragment: Fragment, addFragmentInBackstack: Boolean) {
        val backStateName = fragment.javaClass.name
        val manager = supportFragmentManager
        val fragmentPopped = manager.popBackStackImmediate(backStateName, 0)
        if (!fragmentPopped && manager.findFragmentByTag(backStateName) == null) {
            val ft = manager.beginTransaction()
            ft.replace(R.id.main_fragment_container, fragment, backStateName)
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
            if (addFragmentInBackstack) ft.addToBackStack(backStateName)
            ft.commit()
        }
    }

    override fun onPause() {
        super.onPause()
        if (loadingDialog != null) {
            loadingDialog!!.dismiss()
            loadingDialog = null
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) supportFragmentManager.popBackStack() else super.onBackPressed()
    }
}