// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.model.Netmap
import com.tailscale.ipn.ui.model.Tailcfg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetSnapshotTest {
  private fun netmap() =
      Netmap.NetworkMap(
          SelfNode =
              Tailcfg.Node(
                  StableID = "self",
                  ComputedName = "pixel",
                  Addresses = listOf("100.64.0.9/32", "fd7a:115c:a1e0::9/128"),
              ),
          Peers = emptyList(),
          Domain = "example.com",
          UserProfiles = emptyMap(),
          TKAEnabled = false,
      )

  @Test
  fun reportsThisDevice() {
    val snapshot = WidgetSnapshot.from(WidgetSnapshot(), Ipn.State.Running, netmap())

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

    val snapshot = WidgetSnapshot.from(previous, Ipn.State.Starting, netmap = null)

    assertEquals(previous.copy(vpnRunning = true), snapshot)
  }

  @Test
  fun stoppedIsNotRunning() {
    for (state in listOf(Ipn.State.Stopped, Ipn.State.Stopping, Ipn.State.NeedsMachineAuth)) {
      assertFalse(WidgetSnapshot.from(WidgetSnapshot(), state, null).vpnRunning)
    }
  }

  @Test
  fun loggingOutClearsDevice() {
    val previous = WidgetSnapshot(vpnRunning = true, selfName = "pixel", selfAddress = "100.64.0.9")

    assertEquals(WidgetSnapshot(), WidgetSnapshot.from(previous, Ipn.State.NeedsLogin, null))
  }
}
