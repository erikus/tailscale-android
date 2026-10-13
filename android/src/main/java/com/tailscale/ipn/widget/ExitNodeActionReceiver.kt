// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tailscale.ipn.App
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.model.StableNodeID
import com.tailscale.ipn.util.TSLog

/**
 * Carries out the exit node actions of the home screen widgets: choosing an exit node (from
 * [ExitNodeDialogActivity]) and switching its use on and off (from [StatusWidgetProvider]).
 */
class ExitNodeActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
      // An absent or empty ID means "None".
      ACTION_SELECT ->
          selectExitNode(intent.getStringExtra(EXTRA_NODE_ID)?.takeIf { it.isNotEmpty() })
      ACTION_TOGGLE -> toggleExitNode(context)
    }
  }

  // In both cases the widgets redraw when the backend reports the changed prefs; see
  // Widgets.publish.

  private fun selectExitNode(id: StableNodeID?) {
    val pendingResult = goAsync()
    val prefs = Ipn.MaskedPrefs().apply { ExitNodeID = id }
    Client(App.get().applicationScope).editPrefs(prefs) { result ->
      result.onFailure { TSLog.e(TAG, "failed to set exit node from widget: $it") }
      pendingResult.finish()
    }
  }

  /** Stops using the exit node, or goes back to the one last used, like the app's button. */
  private fun toggleExitNode(context: Context) {
    val snapshot = WidgetSnapshotStore.load(context)
    val use = snapshot.activeExitNodeId == null
    if (use && snapshot.priorExitNodeId == null) {
      // Nothing to go back to. The widget hides the button in this state, so this is only reached
      // if the state changed under a widget that has not been redrawn yet.
      return
    }
    val pendingResult = goAsync()
    Client(App.get().applicationScope).setUseExitNode(use) { result ->
      result.onFailure { TSLog.e(TAG, "failed to toggle exit node from widget: $it") }
      pendingResult.finish()
    }
  }

  companion object {
    private const val TAG = "ExitNodeActionReceiver"
    private const val ACTION_SELECT = "com.tailscale.ipn.widget.SELECT_EXIT_NODE"
    private const val ACTION_TOGGLE = "com.tailscale.ipn.widget.TOGGLE_EXIT_NODE"
    private const val EXTRA_NODE_ID = "com.tailscale.ipn.widget.EXIT_NODE_ID"
    private const val REQUEST_TOGGLE = 0
    private const val REQUEST_OPEN_DIALOG = 1

    /** The broadcast that makes [id] the exit node; null means no exit node. */
    fun selectIntent(context: Context, id: StableNodeID?): Intent =
        Intent(context, ExitNodeActionReceiver::class.java)
            .setAction(ACTION_SELECT)
            .putExtra(EXTRA_NODE_ID, id.orEmpty())

    /** Switches exit node use on or off; only meaningful when an exit node is configured. */
    fun toggleIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE,
            Intent(context, ExitNodeActionReceiver::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Opens the exit node list. */
    fun openDialogIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN_DIALOG,
            Intent(context, ExitNodeDialogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
  }
}
