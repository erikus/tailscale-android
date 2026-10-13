// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import android.content.Context
import androidx.core.content.edit
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.model.Netmap
import com.tailscale.ipn.ui.model.StableNodeID
import com.tailscale.ipn.util.TSLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** An exit node as shown in the exit node list of the status widget. */
@Serializable
data class WidgetExitNode(val id: StableNodeID, val label: String, val online: Boolean)

/**
 * WidgetSnapshot is everything the home screen widgets need in order to render. It is persisted so
 * that widgets can be drawn without initializing the Go backend (which could have side effects such
 * as starting the VPN).
 */
@Serializable
data class WidgetSnapshot(
    val vpnRunning: Boolean = false,
    // Exit nodes in the user's tailnet. Mullvad nodes are excluded: there are too many of them for
    // a widget, so they are picked in the app instead.
    val exitNodes: List<WidgetExitNode> = emptyList(),
    val activeExitNodeId: StableNodeID? = null,
    val activeExitNodeName: String? = null,
    // The exit node that was in use before exit node use was switched off, if any. Switching exit
    // node use back on returns to it.
    val priorExitNodeId: StableNodeID? = null,
    val priorExitNodeName: String? = null,
    // This device's name and Tailscale IPv4 address.
    val selfName: String? = null,
    val selfAddress: String? = null,
    // The tailnet's name. The app's header shows the login profile's name instead, which is the
    // same for personal tailnets but is not available without the backend.
    val tailnetName: String? = null,
) {
  /** The exit node the Enable / Disable button acts on: the one in use, else the one last used. */
  val configuredExitNodeId: StableNodeID?
    get() = activeExitNodeId ?: priorExitNodeId

  val configuredExitNodeName: String?
    get() = if (activeExitNodeId != null) activeExitNodeName else priorExitNodeName

  companion object {
    /**
     * Derives a snapshot from the backend state. [previous] supplies values the backend has not
     * reported yet (the netmap and prefs arrive some time after the backend starts).
     */
    fun from(
        previous: WidgetSnapshot,
        state: Ipn.State,
        prefs: Ipn.Prefs?,
        netmap: Netmap.NetworkMap?,
    ): WidgetSnapshot {
      val vpnRunning = state == Ipn.State.Starting || state == Ipn.State.Running
      if (state == Ipn.State.NeedsLogin) {
        return WidgetSnapshot(vpnRunning = vpnRunning)
      }

      val activeId: StableNodeID? =
          if (prefs != null) prefs.activeExitNodeID else previous.activeExitNodeId
      val priorId: StableNodeID? =
          if (prefs != null) prefs.selectedExitNodeID else previous.priorExitNodeId
      if (netmap == null) {
        return previous.copy(
            vpnRunning = vpnRunning,
            activeExitNodeId = activeId,
            priorExitNodeId = priorId,
        )
      }

      val peers = netmap.Peers.orEmpty()
      val exitNodes =
          peers
              .filter { it.isExitNode && !it.isMullvadNode }
              .map {
                WidgetExitNode(id = it.StableID, label = it.displayName, online = it.Online == true)
              }
              .sortedBy { it.label.lowercase() }
      // An exit node that is missing from the netmap is shown by its ID, like the app does.
      fun nameOf(id: StableNodeID?): String? = id?.let {
        peers.find { peer -> peer.StableID == it }?.exitNodeName ?: it
      }
      return WidgetSnapshot(
          vpnRunning = vpnRunning,
          exitNodes = exitNodes,
          activeExitNodeId = activeId,
          activeExitNodeName = nameOf(activeId),
          priorExitNodeId = priorId,
          priorExitNodeName = nameOf(priorId),
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
