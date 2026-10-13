// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import com.tailscale.ipn.R

/**
 * A home screen widget laid out like the top of the app's main screen: the connect switch, the
 * tailnet and connection state, this device's name and address, and the exit node with its Enable /
 * Disable button. The taps are handled by [ToggleWidgetProvider] and [ExitNodeActionReceiver].
 */
class StatusWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
    render(context, manager, appWidgetIds)
  }

  companion object {
    // Below this height (in dp) the device row is left out so that the rest still fits.
    private const val FULL_LAYOUT_MIN_HEIGHT_DP = 112f
    private const val MIN_WIDTH_DP = 100f

    fun render(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
      if (appWidgetIds.isEmpty()) {
        return
      }
      val compact = build(context, R.layout.widget_status_compact)
      val views =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The launcher picks the largest layout that fits the widget's current size.
            RemoteViews(
                mapOf(
                    SizeF(MIN_WIDTH_DP, 0f) to compact,
                    SizeF(MIN_WIDTH_DP, FULL_LAYOUT_MIN_HEIGHT_DP) to
                        build(context, R.layout.widget_status),
                )
            )
          } else {
            compact
          }
      manager.updateAppWidget(appWidgetIds, views)
    }

    private fun build(context: Context, layout: Int): RemoteViews {
      val snapshot = WidgetSnapshotStore.load(context)
      val connected = ToggleWidgetProvider.isConnected(context, snapshot)
      val views = RemoteViews(context.packageName, layout)

      // Connection.
      val status = context.getString(if (connected) R.string.connected else R.string.not_connected)
      views.setImageViewResource(
          R.id.widget_status_switch,
          if (connected) R.drawable.widget_switch_on else R.drawable.widget_switch_off,
      )
      views.setContentDescription(
          R.id.widget_status_switch_target,
          "${context.getString(R.string.tile_name)}, $status",
      )
      views.setOnClickPendingIntent(
          R.id.widget_status_switch_target,
          ToggleWidgetProvider.clickIntent(context, connected),
      )
      views.setTextViewText(
          R.id.widget_status_tailnet,
          snapshot.tailnetName ?: context.getString(R.string.tile_name),
      )
      views.setTextViewText(R.id.widget_status_state, status)

      // This device. Only the full layout has this row; setting views a layout lacks is an error.
      if (layout == R.layout.widget_status) {
        views.setTextViewText(
            R.id.widget_status_device,
            snapshot.selfName ?: context.getString(R.string.tile_name),
        )
        views.setTextViewCompoundDrawablesRelative(
            R.id.widget_status_device,
            if (connected) R.drawable.widget_dot_on else R.drawable.widget_dot_off,
            0,
            0,
            0,
        )
        views.setTextViewText(R.id.widget_status_address, snapshot.selfAddress.orEmpty())
      }

      // Exit node. Like the app, the box turns the accent color while an exit node is in use.
      val inUse = snapshot.activeExitNodeId != null
      val primaryText =
          context.getColor(if (inUse) R.color.widget_on_accent else R.color.widget_text_primary)
      val secondaryText =
          context.getColor(if (inUse) R.color.widget_on_accent else R.color.widget_text_secondary)
      views.setInt(
          R.id.widget_status_exit,
          "setBackgroundResource",
          if (inUse) R.drawable.widget_container_active else R.drawable.widget_container,
      )
      views.setTextColor(R.id.widget_status_exit_label, secondaryText)
      views.setTextColor(R.id.widget_status_exit_name, primaryText)
      views.setTextViewText(
          R.id.widget_status_exit_name,
          snapshot.configuredExitNodeName ?: context.getString(R.string.none),
      )
      views.setOnClickPendingIntent(
          R.id.widget_status_exit,
          ExitNodeActionReceiver.openDialogIntent(context),
      )
      if (snapshot.configuredExitNodeId == null) {
        views.setViewVisibility(R.id.widget_status_exit_button, android.view.View.GONE)
      } else {
        views.setViewVisibility(R.id.widget_status_exit_button, android.view.View.VISIBLE)
        views.setTextViewText(
            R.id.widget_status_exit_button,
            context.getString(if (inUse) R.string.disable else R.string.enable),
        )
        views.setOnClickPendingIntent(
            R.id.widget_status_exit_button,
            ExitNodeActionReceiver.toggleIntent(context),
        )
      }
      return views
    }
  }
}
