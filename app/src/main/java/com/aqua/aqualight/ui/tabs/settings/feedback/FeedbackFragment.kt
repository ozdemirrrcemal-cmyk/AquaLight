package com.aqua.aqualight.ui.tabs.settings.feedback

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R

class FeedbackFragment : Fragment(R.layout.fragment_feedback) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}