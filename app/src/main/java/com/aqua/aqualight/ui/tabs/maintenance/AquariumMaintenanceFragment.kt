package com.aqua.aqualight.ui.tabs.maintenance

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentAquariumMaintenanceBinding
import com.aqua.aqualight.ui.tabs.aquarium.AquariumTankViewModel
import com.aqua.aqualight.ui.tabs.maintenance.model.CareTaskUi
import com.aqua.aqualight.ui.tabs.maintenance.model.MaintenanceTab
import com.aqua.aqualight.utils.DialogManager
import com.aqua.aqualight.utils.DialogType
import kotlinx.coroutines.launch

class AquariumMaintenanceFragment :
  Fragment(R.layout.fragment_aquarium_maintenance) {

  private var _binding: FragmentAquariumMaintenanceBinding? = null
  private val binding get() = _binding!!

  private val maintenanceViewModel: MaintenanceViewModel by viewModels()
  private val aquariumTankViewModel: AquariumTankViewModel by activityViewModels()

  private lateinit var adapter: CareTaskAdapter

  override fun onViewCreated(
    view: View,
    savedInstanceState: Bundle?
  ) {
    super.onViewCreated(
      view,
      savedInstanceState
    )

    _binding = FragmentAquariumMaintenanceBinding.bind(view)

    setupRecycler()
    setupClickListeners()
    setupSystemBackButton()
    observeTanks()
    observeSelectedTab()
    observeCareTasks()
  }

  private fun setupRecycler() {
    adapter = CareTaskAdapter(
      onCompleteClick = { task ->
        showCompleteTaskDialog(task)
      },
      onTaskClick = { task ->
        showTaskDetailsPlaceholder(task)
      }
    )

    binding.rvCareTasks.layoutManager = LinearLayoutManager(
      requireContext()
    )

    binding.rvCareTasks.adapter = adapter
  }

  private fun setupClickListeners() {
    binding.tabAll.setOnClickListener {
      maintenanceViewModel.selectTab(
        MaintenanceTab.ALL
      )
    }

    binding.tabToday.setOnClickListener {
      maintenanceViewModel.selectTab(
        MaintenanceTab.TODAY
      )
    }

    binding.tabUpcoming.setOnClickListener {
      maintenanceViewModel.selectTab(
        MaintenanceTab.UPCOMING
      )
    }

    binding.tabHistory.setOnClickListener {
      maintenanceViewModel.selectTab(
        MaintenanceTab.HISTORY
      )
    }

    binding.btnAddCareTask.setOnClickListener {
      openAddCareTaskFlow()
    }

    binding.btnEmptyAddCareTask.setOnClickListener {
      openAddCareTaskFlow()
    }
  }

  private fun setupSystemBackButton() {
    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (handleAddCareTaskBack()) {
            return
          }

          isEnabled = false
          requireActivity().onBackPressedDispatcher.onBackPressed()
          isEnabled = true
        }
      }
    )
  }

  private fun observeTanks() {
    aquariumTankViewModel.tanks.observe(viewLifecycleOwner) { tanks ->
      maintenanceViewModel.setTanks(tanks)
    }
  }

  private fun observeSelectedTab() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.selectedTab.collect { selectedTab ->
          renderSelectedTab(selectedTab)
          renderEmptyText(selectedTab)
        }
      }
    }
  }

  private fun observeCareTasks() {
    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        maintenanceViewModel.taskItems.collect { tasks ->
          adapter.submitList(tasks)
          renderTaskListState(tasks)
        }
      }
    }
  }

  private fun renderTaskListState(
    tasks: List<CareTaskUi>
  ) {
    val hasTasks = tasks.isNotEmpty()

    binding.rvCareTasks.isVisible = hasTasks
    binding.emptyStateContainer.isVisible = !hasTasks
  }

  private fun renderSelectedTab(
    selectedTab: MaintenanceTab
  ) {
    renderTab(
      tabView = binding.tabAll,
      selected = selectedTab == MaintenanceTab.ALL
    )

    renderTab(
      tabView = binding.tabToday,
      selected = selectedTab == MaintenanceTab.TODAY
    )

    renderTab(
      tabView = binding.tabUpcoming,
      selected = selectedTab == MaintenanceTab.UPCOMING
    )

    renderTab(
      tabView = binding.tabHistory,
      selected = selectedTab == MaintenanceTab.HISTORY
    )
  }

  private fun renderTab(
    tabView: TextView,
    selected: Boolean
  ) {
    tabView.setBackgroundResource(
      if (selected) {
        R.drawable.bg_maintenance_tab_selected
      } else {
        R.drawable.bg_maintenance_tab_unselected
      }
    )

    tabView.setTextColor(
      if (selected) {
        android.graphics.Color.WHITE
      } else {
        android.graphics.Color.parseColor("#8FA4BE")
      }
    )

    tabView.setTypeface(
      null,
      if (selected) {
        android.graphics.Typeface.BOLD
      } else {
        android.graphics.Typeface.NORMAL
      }
    )
  }

  private fun renderEmptyText(
    selectedTab: MaintenanceTab
  ) {
    when (selectedTab) {
      MaintenanceTab.ALL -> {
        binding.tvEmptyMaintenanceTitle.text = "No care tasks yet"
        binding.tvEmptyMaintenanceMessage.text =
          "Add manual reminders or let AquaLight create smart care tasks based on your aquarium setup."
      }

      MaintenanceTab.TODAY -> {
        binding.tvEmptyMaintenanceTitle.text = "Nothing due today"
        binding.tvEmptyMaintenanceMessage.text =
          "There are no care tasks scheduled for today."
      }

      MaintenanceTab.UPCOMING -> {
        binding.tvEmptyMaintenanceTitle.text = "No upcoming tasks"
        binding.tvEmptyMaintenanceMessage.text =
          "Future care reminders will appear here."
      }

      MaintenanceTab.HISTORY -> {
        binding.tvEmptyMaintenanceTitle.text = "No completed tasks"
        binding.tvEmptyMaintenanceMessage.text =
          "Completed maintenance tasks will be listed here."
      }
    }
  }

  private fun showCompleteTaskDialog(
    task: CareTaskUi
  ) {
    DialogManager.showConfirmDialog(
      context = requireContext(),
      type = DialogType.SUCCESS,
      title = "Complete Task?",
      message = "\"${task.title}\" will be marked as completed.",
      confirmTextResId = R.string.confirm,
      cancelTextResId = R.string.cancel,
      onConfirm = {
        maintenanceViewModel.completeTask(
          taskId = task.id
        )
      }
    )
  }

  private fun openAddCareTaskFlow() {
    binding.addCareTaskFlowContainer.isVisible = true

    childFragmentManager.commit {
      replace(
        R.id.addCareTaskFlowContainer,
        AddCareTaskFragment(),
        "ADD_CARE_TASK_FRAGMENT"
      )
    }
  }

  fun closeAddCareTaskFlow() {
    val fragment = childFragmentManager.findFragmentById(
      R.id.addCareTaskFlowContainer
    )

    if (fragment != null) {
      childFragmentManager.commit {
        remove(fragment)
      }
    }

    binding.addCareTaskFlowContainer.isVisible = false
  }

  private fun handleAddCareTaskBack(): Boolean {
    if (!binding.addCareTaskFlowContainer.isVisible) {
      return false
    }

    closeAddCareTaskFlow()
    return true
  }

  private fun showTaskDetailsPlaceholder(
    task: CareTaskUi
  ) {
    Toast.makeText(
      requireContext(),
      task.title,
      Toast.LENGTH_SHORT
    ).show()
  }

  override fun onDestroyView() {
    binding.rvCareTasks.adapter = null
    _binding = null

    super.onDestroyView()
  }
}