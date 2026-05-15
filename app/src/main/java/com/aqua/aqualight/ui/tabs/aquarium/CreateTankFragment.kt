package com.aqua.aqualight.ui.tabs.aquarium

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.aqua.aqualight.databinding.FragmentCreateTankBinding

class CreateTankFragment : Fragment() {

    private var _binding: FragmentCreateTankBinding? = null
    private val binding get() = _binding!!

    // Step kontrolü
    private var currentStep = 1
    private val totalSteps = 5

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateTankBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons()
        showStep(currentStep)
    }

    private fun setupButtons() {
        // 🔙 Geri Butonu (header)
        binding.btnBack.setOnClickListener {
            if(currentStep == 1) {
                requireActivity().onBackPressed()
            } else {
                currentStep--
                showStep(currentStep)
            }
        }

        // Alt Next / Complete Butonları placeholder
        binding.btnNext?.setOnClickListener {
            if(currentStep < totalSteps) {
                currentStep++
                showStep(currentStep)
            } else {
                // Complete basıldı → şimdilik geri dön
                requireActivity().onBackPressed()
            }
        }
    }

    private fun showStep(step: Int) {
        // Önce tüm step layoutları gizle
        binding.step1Layout.visibility = if(step==1) View.VISIBLE else View.GONE
        binding.step2Layout.visibility = if(step==2) View.VISIBLE else View.GONE
        binding.step3Layout.visibility = if(step==3) View.VISIBLE else View.GONE
        binding.step4Layout.visibility = if(step==4) View.VISIBLE else View.GONE
        binding.step5Layout.visibility = if(step==5) View.VISIBLE else View.GONE

        // Üst bar başlığını step’e göre değiştir
        binding.tvTitle.text = when(step) {
            1 -> "Step 1"
            2 -> "Step 2"
            3 -> "Step 3"
            4 -> "Step 4"
            5 -> "Step 5"
            else -> "Create Tank"
        }

        // Next / Complete text değişimi
        binding.btnNext?.text = if(step == totalSteps) "Complete" else "Next"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}