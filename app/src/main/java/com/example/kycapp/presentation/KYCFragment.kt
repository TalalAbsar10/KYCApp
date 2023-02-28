package com.example.kycapp.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.kycapp.R
import com.example.kycapp.`interface`.CallBacksInterface
import com.example.kycapp.databinding.FragmentKycBinding
import com.regula.documentreader.api.DocumentReader
import com.regula.documentreader.api.enums.eGraphicFieldType
import com.regula.documentreader.api.enums.eRPRM_ResultType
import com.regula.documentreader.api.enums.eVisualFieldType
import com.regula.documentreader.api.results.DocumentReaderResults

class KYCFragment : Fragment() {

      private lateinit var binding: FragmentKycBinding

    @Volatile
    var mCallbacks: CallBacksInterface? = null

    companion object {
        var RFID_RESULT = 100
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mCallbacks = activity as CallBacksInterface?
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentKycBinding.inflate(layoutInflater, container, false)
        val view = binding.root
        initView()
        return view
    }

    override fun onResume() {
        super.onResume()
        if (activity != null && DocumentReader.Instance().isReady
            && DocumentReader.Instance().availableScenarios.isNotEmpty()
        )
            (activity as MainActivity?)!!.setupScenarios()
    }

    override fun onDetach() {
        super.onDetach()
        mCallbacks = null
    }

    private fun initView() {
        binding.recognizeImageLink.setOnClickListener {
            if (!DocumentReader.Instance().isReady) return@setOnClickListener
            clearResults()
            mCallbacks?.recognizeImage()
        }
        binding.showScannerLink.setOnClickListener {
            clearResults()
            mCallbacks!!.scanDocument()
        }
    }

    fun disableUiElements() {
        binding.showScannerLink.isClickable = false
        binding.recognizeImageLink.isClickable = false

        binding.showScannerLink.setTextColor(Color.GRAY)
        binding.recognizeImageLink.setTextColor(Color.GRAY)
    }

    fun displayResults(results: DocumentReaderResults?) {
        if (results != null) {
            val firstName = results.getTextFieldValueByType(eVisualFieldType.FT_GIVEN_NAMES)
            val lastName = results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME)
            val fullName =
                results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME_AND_GIVEN_NAMES)
            val dob = results.getTextFieldValueByType(eVisualFieldType.FT_DATE_OF_BIRTH)
            val nationality = results.getTextFieldValueByType(eVisualFieldType.FT_NATIONALITY)
            val docNumber = results.getTextFieldValueByType(eVisualFieldType.FT_DOCUMENT_NUMBER)
            val dateOfIssue = results.getTextFieldValueByType(eVisualFieldType.FT_DATE_OF_ISSUE)
            val dateOfExpiry = results.getTextFieldValueByType(eVisualFieldType.FT_DATE_OF_EXPIRY)
            val sex = results.getTextFieldValueByType(eVisualFieldType.FT_SEX)
            if (firstName != null || lastName != null) {
                binding.name.text = firstName + " " + lastName
            } else if (fullName != null) {
                binding.name.text = fullName
            }
            if (dob != null) {
                binding.dob.text = dob
            }
            if (sex != null) {
                binding.gender.text = sex
            }
            if (nationality != null) {
                binding.nationality.text = nationality
            }
            if (docNumber != null) {
                binding.docNumber.text = docNumber
            }
            if (dateOfIssue != null) {
                binding.dateOfIssue.text = dateOfIssue
            }
            if (dateOfExpiry != null) {
                binding.dateOfExpiry.text = dateOfExpiry
            }

            results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT)?.let {
                binding.portraitIv.setImageBitmap(it)
            }
            results.getGraphicFieldImageByType(
                eGraphicFieldType.GF_PORTRAIT,
                eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA
            )?.let {
                binding.portraitIv.setImageBitmap(it)
            }
            results.getGraphicFieldImageByType(eGraphicFieldType.GF_DOCUMENT_IMAGE)?.let {
                val aspectRatio = it.width.toDouble() / it.height.toDouble()
                val documentImage =
                    Bitmap.createScaledBitmap(it, (480 * aspectRatio).toInt(), 480, false)
                binding.documentImageIv.setImageBitmap(documentImage)
            }
        }
    }

    private fun clearResults() {
        binding.name.text = ""
        binding.name.text = ""
        binding.dob.text = ""
        binding.gender.text = ""
        binding.nationality.text = ""
        binding.docNumber.text = ""
        binding.dateOfIssue.text = ""
        binding.dateOfExpiry.text = ""
        binding.portraitIv.setImageResource(R.drawable.portrait)
        binding.documentImageIv.setImageResource(R.drawable.id)
    }
}