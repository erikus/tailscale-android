// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.model.Netmap
import com.tailscale.ipn.ui.model.Tailcfg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {
  private val exitRoutes = listOf("0.0.0.0/0", "::/0")

  private fun node(
      id: String,
      name: String,
      exitNode: Boolean = true,
      online: Boolean = true,
      location: Tailcfg.Location? = null,
  ) =
      Tailcfg.Node(
          StableID = id,
          Name = "$name.example.ts.net.",
          ComputedName = name,
          AllowedIPs = if (exitNode) exitRoutes else listOf("100.64.0.1/32"),
          Online = online,
          Hostinfo = Tailcfg.Hostinfo(Location = location),
      )

  private fun netmap(vararg peers: Tailcfg.Node) =
      Netmap.NetworkMap(
          SelfNode =
              Tailcfg.Node(
                  StableID = "self",
                  ComputedName = "pixel",
                  Addresses = listOf("100.64.0.9/32", "fd7a:115c:a1e0::9/128"),
              ),
          Peers = peers.toList(),
          Domain = "example.com",
          UserProfiles = emptyMap(),
          TKAEnabled = false,
      )

  @Test
  fun reportsThisDevice() {
    val snapshot = WidgetSnapshot.from(WidgetSnapshot(), Ipn.State.Running, Ipn.Prefs(), netmap())

    assertEquals("pixel", snapshot.selfName)
    assertEquals("100.64.0.9", snapshot.selfAddress)
    assertEquals("example.com", snapshot.tailnetName)
  }

  @Test
  fun keepsPreviousDeviceUntilNetmapArrives() {
    val previous =
        WidgetSnapshot(
            vpnRunning = false,
            selfName = "pixel",
            selfAddress = "100.64.0.9",
            tailnetName = "example.com",
        )

    val snapshot = WidgetSnapshot.from(previous, Ipn.State.Starting, prefs = null, netmap = null)

    assertEquals(previous.copy(vpnRunning = true), snapshot)
  }

  @Test
  fun stoppedIsNotRunning() {
    for (state in listOf(Ipn.State.Stopped, Ipn.State.Stopping, Ipn.State.NeedsMachineAuth)) {
      assertFalse(WidgetSnapshot.from(WidgetSnapshot(), state, null, null).vpnRunning)
    }
  }

  @Test
  fun loggingOutClearsDevice() {
    val previous = WidgetSnapshot(vpnRunning = true, selfName = "pixel", selfAddress = "100.64.0.9")

    assertEquals(WidgetSnapshot(), WidgetSnapshot.from(previous, Ipn.State.NeedsLogin, null, null))
  }

  @Test
  fun listsOnlyTailnetExitNodesSortedByName() {
    val snapshot =
        WidgetSnapshot.from(
            WidgetSnapshot(),
            Ipn.State.Running,
            Ipn.Prefs(),
            netmap(
                node("n1", "zebra"),
                node("n2", "laptop", exitNode = false),
                node("n3", "Apple", online = false),
                node("n4", "mullvad", location = Tailcfg.Location(Country = "Sweden")),
            ),
        )

    assertTrue(snapshot.vpnRunning)
    assertEquals(
        listOf(
            WidgetExitNode("n3", "Apple", online = false),
            WidgetExitNode("n1", "zebra", online = true),
        ),
        snapshot.exitNodes,
    )
    assertNull(snapshot.activeExitNodeId)
    assertNull(snapshot.activeExitNodeName)
  }

  @Test
  fun reportsActiveExitNode() {
    val snapshot =
        WidgetSnapshot.from(
            WidgetSnapshot(),
            Ipn.State.Running,
            Ipn.Prefs(ExitNodeID = "n1"),
            netmap(node("n1", "zebra")),
        )

    assertEquals("n1", snapshot.activeExitNodeId)
    assertEquals("zebra", snapshot.activeExitNodeName)
  }

  @Test
  fun emptyExitNodeIdMeansNone() {
    val snapshot =
        WidgetSnapshot.from(
            WidgetSnapshot(),
            Ipn.State.Running,
            Ipn.Prefs(ExitNodeID = ""),
            netmap(node("n1", "zebra")),
        )

    assertNull(snapshot.activeExitNodeId)
  }

  @Test
  fun keepsPreviousExitNodesUntilNetmapArrives() {
    val previous =
        WidgetSnapshot(
            vpnRunning = false,
            exitNodes = listOf(WidgetExitNode("n1", "zebra", online = true)),
            activeExitNodeId = "n1",
            activeExitNodeName = "zebra",
        )

    val snapshot = WidgetSnapshot.from(previous, Ipn.State.Starting, prefs = null, netmap = null)

    assertEquals(previous.copy(vpnRunning = true), snapshot)
  }

  @Test
  fun loggingOutClearsExitNodes() {
    val previous =
        WidgetSnapshot(
            vpnRunning = true,
            exitNodes = listOf(WidgetExitNode("n1", "zebra", online = true)),
            activeExitNodeId = "n1",
            activeExitNodeName = "zebra",
        )

    assertEquals(
        WidgetSnapshot(),
        WidgetSnapshot.from(previous, Ipn.State.NeedsLogin, Ipn.Prefs(ExitNodeID = "n1"), null),
    )
  }

  @Test
  fun switchedOffExitNodeStaysConfigured() {
    val snapshot =
        WidgetSnapshot.from(
            WidgetSnapshot(),
            Ipn.State.Running,
            Ipn.Prefs(ExitNodeID = "", InternalExitNodePrior = "n1"),
            netmap(node("n1", "zebra")),
        )

    assertNull(snapshot.activeExitNodeId)
    assertEquals("n1", snapshot.priorExitNodeId)
    assertEquals("n1", snapshot.configuredExitNodeId)
    assertEquals("zebra", snapshot.configuredExitNodeName)
  }

  @Test
  fun activeExitNodeWinsOverPriorOne() {
    val snapshot =
        WidgetSnapshot.from(
            WidgetSnapshot(),
            Ipn.State.Running,
            Ipn.Prefs(ExitNodeID = "n2", InternalExitNodePrior = "n1"),
            netmap(node("n1", "zebra"), node("n2", "apple")),
        )

    assertEquals("n2", snapshot.configuredExitNodeId)
    assertEquals("apple", snapshot.configuredExitNodeName)
  }
}
