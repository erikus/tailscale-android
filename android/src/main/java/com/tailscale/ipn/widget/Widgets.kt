// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.model.Netmap
import com.tailscale.ipn.util.TSLog

/** Entry points used by the app to keep the home screen widgets in sync with the backend. */
object Widgets {
  private const val TAG = "Widgets"

  /**
   * Must be called when the process starts. The VPN cannot outlive the process, so a snapshot that
   * still says "running" is left over from a process that died and has to be corrected.
   */
  fun onProcessStart(context: Context) {
    val snapshot = WidgetSnapshotStore.load(context)
    if (
        snapshot.vpnRunning && WidgetSnapshotStore.save(context, snapshot.copy(vpnRunning = false))
    ) {
      redrawAll(context)
    }
  }

  /** Records the current backend state and redraws the widgets if anything they show changed. */
  fun publish(context: Context, state: Ipn.State, netmap: Netmap.NetworkMap?) {
    val snapshot = WidgetSnapshot.from(WidgetSnapshotStore.load(context), state, netmap)
    if (WidgetSnapshotStore.save(context, snapshot)) {
      redrawAll(context)
    }
  }

  /** Redraws every placed widget from the stored snapshot. */
  fun redrawAll(context: Context) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    ToggleWidgetProvider.render(
        context,
        manager,
        manager.getAppWidgetIds(ComponentName(context, ToggleWidgetProvider::class.java)),
    )
  }

  /** Brings up the main activity, for the cases a widget cannot handle on its own. */
  fun openApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      context.startActivity(intent)
    } catch (e: Exception) {
      TSLog.e(TAG, "unable to open the app from a widget: $e")
    }
  }
}
