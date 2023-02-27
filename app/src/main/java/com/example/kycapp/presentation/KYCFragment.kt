package com.example.kycapp.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.kycapp.`interface`.CallBacksInterface
import com.example.kycapp.R
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

    override fun onResume() { //used to show scenarios after fragments transaction
        super.onResume()
        if (activity != null && DocumentReader.Instance().isReady
            && DocumentReader.Instance().availableScenarios.isNotEmpty())
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
            val name = results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME_AND_GIVEN_NAMES)
            if (name != null) {
                binding.nameTv.text = name
            }

            // through all text fields
            results.textResult?.fields?.forEach {
                val value = results.getTextFieldValueByType(it.fieldType, it.lcid)
                Log.d("MainActivity", "Text Field: " + context?.let { it1 -> it.getFieldName(it1) } + " value: " + value);
            }

            results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT)?.let {
                binding.portraitIv.setImageBitmap(it)
            }
            results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT, eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA)?.let {
                binding.portraitIv.setImageBitmap(it)
            }
            results.getGraphicFieldImageByType(eGraphicFieldType.GF_DOCUMENT_IMAGE)?.let {
                val aspectRatio = it.width.toDouble() / it.height.toDouble()
                val documentImage = Bitmap.createScaledBitmap(it, (480 * aspectRatio).toInt(), 480, false)
                binding.documentImageIv.setImageBitmap(documentImage)
            }
        }
    }

    private fun clearResults() {
        binding.nameTv.text = ""
        binding.portraitIv.setImageResource(R.drawable.portrait)
        binding.documentImageIv.setImageResource(R.drawable.id)
    }
}