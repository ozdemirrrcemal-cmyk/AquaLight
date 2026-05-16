package com.aqua.aqualight.ui.tabs.aquarium

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.aqua.aqualight.R
import com.google.android.material.button.MaterialButton
import android.widget.ProgressBar
import androidx.fragment.app.commit
import androidx.fragment.app.replace

class CreateTankFragment : Fragment() {

    private var currentStep = 1
    private val totalSteps = 5

    private lateinit var progressBar: ProgressBar
    private lateinit var btnNext: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_create_tank, container, false)

        progressBar = view.findViewById(R.id.progressBar)
        btnNext = view.findViewById(R.id.btnNext)

        showStep(currentStep)

        btnNext.setOnClickListener {
            if(currentStep < totalSteps) {
                currentStep++
                showStep(currentStep)
            }
        }

        return view
    }

    private fun showStep(step: Int) {
        val fragment = when(step) {
            1 -> StepTankNameFragment()
            2 -> StepTankInfoFragment()
            3 -> StepTankPhotoFragment()
            4 -> StepMaterialsFragment()
            5 -> StepTankDetailFragment()
            else -> StepTankNameFragment()
        }

        childFragmentManager.commit {
            replace<Fragment>(R.id.stepFragmentContainer, fragment::class.java.name)
        }

        // Progress bar
        val progress = (step.toFloat() / totalSteps * 100).toInt()
        progressBar.progress = progress
    }
}