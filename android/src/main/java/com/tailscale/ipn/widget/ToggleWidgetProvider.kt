// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import com.tailscale.ipn.App
import com.tailscale.ipn.R
import com.tailscale.ipn.UninitializedApp

/** A home screen widget that connects and disconnects Tailscale, like the quick settings tile. */
class ToggleWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    render(context, manager, appWidgetIds)
  }

  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == ACTION_TOGGLE) {
      toggle(context)
    } else {
      super.onReceive(context, intent)
    }
  }

  private fun toggle(context: Context) {
    val app = UninitializedApp.get()
    if (!app.isAbleToStartVPN()) {
      Widgets.openApp(context)
      return
    }
    // Make sure the backend is up before asking it to change state.
    App.get()
    if (WidgetSnapshotStore.load(context).vpnRunning) {
      app.stopVPN()
    } else if (isVpnPermissionGranted(context)) {
      app.startVPN()
    } else {
      Widgets.openApp(context)
    }
  }

  companion object {
    private const val ACTION_TOGGLE = "com.tailscale.ipn.widget.TOGGLE"
    private const val REQUEST_TOGGLE = 0
    private const val REQUEST_OPEN_APP = 1

    private fun isVpnPermissionGranted(context: Context): Boolean =
        VpnService.prepare(context) == null

    fun render(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
      if (appWidgetIds.isEmpty()) {
        return
      }
      val snapshot = WidgetSnapshotStore.load(context)
      val connected = isConnected(context, snapshot)

      val views = RemoteViews(context.packageName, R.layout.widget_toggle)
      val status = context.getString(if (connected) R.string.connected else R.string.not_connected)
      views.setTextViewText(
          R.id.widget_toggle_name,
          snapshot.selfName ?: context.getString(R.string.tile_name),
      )
      views.setTextViewText(R.id.widget_toggle_detail, snapshot.selfAddress ?: status)
      views.setTextViewCompoundDrawablesRelative(
          R.id.widget_toggle_name,
          if (connected) R.drawable.widget_dot_on else R.drawable.widget_dot_off,
          0,
          0,
          0,
      )
      views.setImageViewResource(
          R.id.widget_toggle_switch,
          if (connected) R.drawable.widget_switch_on else R.drawable.widget_switch_off,
      )
      views.setContentDescription(
          android.R.id.background,
          "${context.getString(R.string.tile_name)}, $status",
      )

      views.setOnClickPendingIntent(android.R.id.background, clickIntent(context, connected))
      manager.updateAppWidget(appWidgetIds, views)
    }

    /** Whether the VPN is up, as far as the widgets can tell without starting the backend. */
    fun isConnected(context: Context, snapshot: WidgetSnapshot): Boolean =
        snapshot.vpnRunning && UninitializedApp.get().isAbleToStartVPN()

    /** What tapping a connect / disconnect switch should do, given whether the VPN is up. */
    fun clickIntent(context: Context, connected: Boolean): PendingIntent {
      // Logging in and granting the VPN permission both need the app's UI, and an activity can
      // only be opened reliably straight from the tap. Everything else is handled in onReceive.
      val needsApp =
          !UninitializedApp.get().isAbleToStartVPN() ||
              (!connected && !isVpnPermissionGranted(context))
      return if (needsApp) openAppIntent(context) else toggleIntent(context)
    }

    private fun toggleIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_TOGGLE,
            Intent(context, ToggleWidgetProvider::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_OPEN_APP,
            context.packageManager.getLaunchIntentForPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
  }
}
