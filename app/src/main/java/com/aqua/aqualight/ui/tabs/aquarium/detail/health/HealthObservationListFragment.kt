package com.aqua.aqualight.ui.tabs.aquarium.detail.health

import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.composition.requireAppContainer
import com.aqua.aqualight.databinding.FragmentHealthObservationListBinding
import com.aqua.aqualight.i18n.LocaleFormatter
import com.aqua.aqualight.ui.common.header.AquaHeaderConfig
import com.aqua.aqualight.ui.common.header.setupAquaHeader
import com.aqua.aqualight.ui.tabs.aquarium.navigation.navigateSafelyFrom

abstract class HealthObservationListFragment(@IdRes private val route: Int, @StringRes private val title: Int) :
    Fragment(R.layout.fragment_health_observation_list) {
    private val model: HealthObservationViewModel by viewModels {
        requireContext().requireAppContainer().defaultViewModelFactory
    }
    private var binding: FragmentHealthObservationListBinding? = null
    protected abstract fun newDirections(): NavDirections
    protected abstract fun detailDirections(id: Long): NavDirections

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = FragmentHealthObservationListBinding.bind(view).also { binding = it }
        ui.appHeader.setupAquaHeader(this, AquaHeaderConfig(titleOverride = getString(title),
            onBackClick = { findNavController().navigateUp() }))
        val adapter = HealthObservationListAdapter { id ->
            findNavController().navigateSafelyFrom(route, detailDirections(id))
        }
        ui.historyList.layoutManager = LinearLayoutManager(requireContext())
        ui.historyList.adapter = adapter
        ui.historyList.itemAnimator = null
        ui.add.setOnClickListener { findNavController().navigateSafelyFrom(route, newDirections()) }
        ui.latest.setOnClickListener { model.page(null) }
        ui.retry.setOnClickListener { model.retryPreparation() }
        model.history.observe(viewLifecycleOwner) { state ->
            ui.retry.isVisible = state is HealthLoadState.Failed
            ui.older.isEnabled = false
            if (state is HealthLoadState.Content) {
                adapter.submit(state.value.records)
                ui.status.text = if (state.value.records.isEmpty()) getString(R.string.health_empty)
                    else getString(R.string.health_count,
                        LocaleFormatter.formatInteger(requireContext(), state.value.totalCount))
                ui.older.isEnabled = state.value.next != null
                ui.older.setOnClickListener { state.value.next?.let(model::page) }
            } else {
                adapter.submit(emptyList())
                ui.status.setText(if (state is HealthLoadState.Failed)
                    R.string.health_failed else R.string.health_loading)
            }
        }
    }

    override fun onDestroyView() {
        binding?.historyList?.adapter = null
        binding = null
        super.onDestroyView()
    }
}
