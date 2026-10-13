// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import android.content.Context
import androidx.core.content.edit
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.model.Netmap
import com.tailscale.ipn.util.TSLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * WidgetSnapshot is everything the home screen widgets need in order to render. It is persisted so
 * that widgets can be drawn without initializing the Go backend (which could have side effects such
 * as starting the VPN).
 */
@Serializable
data class WidgetSnapshot(
    val vpnRunning: Boolean = false,
    // This device's name and Tailscale IPv4 address.
    val selfName: String? = null,
    val selfAddress: String? = null,
    // The tailnet's name. The app's header shows the login profile's name instead, which is the
    // same for personal tailnets but is not available without the backend.
    val tailnetName: String? = null,
) {
  companion object {
    /**
     * Derives a snapshot from the backend state. [previous] supplies values the backend has not
     * reported yet (the netmap arrives some time after the backend starts).
     */
    fun from(
        previous: WidgetSnapshot,
        state: Ipn.State,
        netmap: Netmap.NetworkMap?,
    ): WidgetSnapshot {
      val vpnRunning = state == Ipn.State.Starting || state == Ipn.State.Running
      if (state == Ipn.State.NeedsLogin) {
        return WidgetSnapshot(vpnRunning = vpnRunning)
      }
      if (netmap == null) {
        return previous.copy(vpnRunning = vpnRunning)
      }
      return WidgetSnapshot(
          vpnRunning = vpnRunning,
          selfName = netmap.SelfNode.displayName.takeIf { it.isNotEmpty() },
          selfAddress = netmap.SelfNode.primaryIPv4Address,
          tailnetName = netmap.Domain.takeIf { it.isNotEmpty() },
      )
    }
  }
}

/** Persists the latest [WidgetSnapshot] in (unencrypted) shared preferences. */
object WidgetSnapshotStore {
  private const val TAG = "WidgetSnapshotStore"
  private const val PREFS_FILE = "widgets"
  private const val SNAPSHOT_KEY = "snapshot"
  private val json = Json { ignoreUnknownKeys = true }

  private var cached: WidgetSnapshot? = null

  @Synchronized
  fun load(context: Context): WidgetSnapshot {
    cached?.let {
      return it
    }
    val stored: String? =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).getString(SNAPSHOT_KEY, null)
    val snapshot: WidgetSnapshot =
        if (stored == null) {
          WidgetSnapshot()
        } else {
          try {
            json.decodeFromString<WidgetSnapshot>(stored)
          } catch (e: Exception) {
            TSLog.e(TAG, "discarding unreadable widget snapshot: $e")
            WidgetSnapshot()
          }
        }
    cached = snapshot
    return snapshot
  }

  /** Stores [snapshot] and reports whether it differs from what was stored before. */
  @Synchronized
  fun save(context: Context, snapshot: WidgetSnapshot): Boolean {
    if (load(context) == snapshot) {
      return false
    }
    cached = snapshot
    context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE).edit {
      putString(SNAPSHOT_KEY, json.encodeToString(snapshot))
    }
    return true
  }
}
