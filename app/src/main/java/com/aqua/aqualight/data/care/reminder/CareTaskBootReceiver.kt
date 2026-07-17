package com.aqua.aqualight.data.care.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aqua.aqualight.data.auth.FirebaseAuthenticatedOwnerProvider

/** Enqueues durable owner reminder restoration after reboot or app replacement. */
class CareTaskBootReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (
            intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val ownerUid = FirebaseAuthenticatedOwnerProvider.create(
            context.applicationContext
        ).currentOwnerUid().orEmpty().trim()

        if (ownerUid.isBlank()) {
            return
        }

        CareReminderReconcileWorker.enqueue(
            context = context.applicationContext,
            ownerUid = ownerUid
        )
    }
}
