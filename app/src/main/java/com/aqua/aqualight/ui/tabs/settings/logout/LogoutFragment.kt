package com.aqua.aqualight.ui.tabs.settings.logout

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.aqua.aqualight.R

class LogoutFragment : Fragment(R.layout.fragment_logout) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }
}