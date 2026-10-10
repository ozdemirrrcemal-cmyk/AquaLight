package com.aqua.aqualight.ui.tabs.aquarium.detail.groups

import android.content.ClipData
import android.view.DragEvent
import android.view.View
import com.aqua.aqualight.R
import com.aqua.aqualight.databinding.FragmentTankControlGroupCreateBinding

/** Local gesture payloads never change a tank assignment or authorize device commands. */
internal class TankControlGroupDragController(
    private val binding: FragmentTankControlGroupCreateBinding,
    private val viewModel: TankControlGroupCreateViewModel
) {
    private data class Payload(val uid: String, val fromGroup: Boolean, val session: Any)
    private val session = Any()
    private val scroller = GroupDragAutoScroller(binding.contentScroll)
    private val targets = listOf(binding.groupDropZone, binding.rvGroup, binding.devicesDropZone, binding.rvDevices)

    init {
        targets.forEachIndexed { index, target ->
            target.setOnDragListener { view, event -> handle(view, event, index < GROUP_TARGETS) }
        }
        binding.contentScroll.setOnDragListener { view, event ->
            val local = (event.localState as? Payload)?.session === session
            if (local) scroller.handle(view, event)
            local
        }
    }

    fun start(view: View, uid: String, fromGroup: Boolean): Boolean {
        if (!viewModel.canMove(uid, !fromGroup)) return false
        val label = view.context.getString(R.string.tank_group_title)
        return view.startDragAndDrop(ClipData.newPlainText(label, uid), View.DragShadowBuilder(view),
            Payload(uid, fromGroup, session), 0)
    }

    private fun handle(view: View, event: DragEvent, toGroup: Boolean): Boolean {
        val payload = event.localState as? Payload
        val local = payload?.session === session
        if (!local || payload == null) return false
        scroller.handle(view, event)
        val destination = payload.fromGroup != toGroup
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> destination && viewModel.canMove(payload.uid, toGroup)
            DragEvent.ACTION_DROP -> destination && viewModel.move(payload.uid, toGroup)
            else -> destination
        }
    }

    fun close() {
        targets.forEach { it.setOnDragListener(null) }
        binding.contentScroll.setOnDragListener(null)
        scroller.close()
    }

    private companion object {
        const val GROUP_TARGETS = 2
    }
}
