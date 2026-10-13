// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.widget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tailscale.ipn.MainActivity
import com.tailscale.ipn.R
import com.tailscale.ipn.ui.model.StableNodeID
import com.tailscale.ipn.ui.theme.AppTheme

private const val SCRIM_ALPHA = 0.4f
private const val EXIT_NODE_PICKER_DEEP_LINK = "tailscale://navigate/main/exit-nodes"
private val DIALOG_MARGIN = 32.dp
private val DIALOG_MAX_WIDTH = 360.dp
private val DIALOG_CORNER_RADIUS = 28.dp
private val LIST_MAX_HEIGHT = 360.dp
private val ROW_PADDING_HORIZONTAL = 24.dp
private val ROW_PADDING_VERTICAL = 14.dp

/**
 * The "drop down" of the status widget's exit node box: a dialog over the home screen that lists
 * the tailnet's exit nodes. It is drawn from the stored [WidgetSnapshot] and hands the choice to
 * [ExitNodeActionReceiver], so opening it does not start the backend.
 */
class ExitNodeDialogActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // Let the scrim cover the system bars too.
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val snapshot = WidgetSnapshotStore.load(this)
    setContent {
      AppTheme {
        ExitNodeDialog(
            snapshot = snapshot,
            onSelect = { id ->
              sendBroadcast(ExitNodeActionReceiver.selectIntent(this, id))
              finish()
            },
            onMore = {
              startActivity(
                  Intent(
                      Intent.ACTION_VIEW,
                      EXIT_NODE_PICKER_DEEP_LINK.toUri(),
                      this,
                      MainActivity::class.java,
                  )
              )
              finish()
            },
            onDismiss = { finish() },
        )
      }
    }
  }
}

@Composable
private fun ExitNodeDialog(
    snapshot: WidgetSnapshot,
    onSelect: (StableNodeID?) -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
  Box(
      modifier =
          Modifier.fillMaxSize()
              .background(Color.Black.copy(alpha = SCRIM_ALPHA))
              .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = onDismiss,
              )
              .padding(DIALOG_MARGIN),
      contentAlignment = Alignment.Center,
  ) {
    Surface(
        shape = RoundedCornerShape(DIALOG_CORNER_RADIUS),
        color = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier.widthIn(max = DIALOG_MAX_WIDTH)
                .fillMaxWidth()
                // Swallow taps so that only taps outside the dialog dismiss it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
    ) {
      Column(modifier = Modifier.padding(vertical = ROW_PADDING_VERTICAL)) {
        Text(
            text = stringResource(R.string.widget_exit_node_title),
            style = MaterialTheme.typography.titleLarge,
            modifier =
                Modifier.padding(
                    horizontal = ROW_PADDING_HORIZONTAL,
                    vertical = ROW_PADDING_VERTICAL,
                ),
        )
        LazyColumn(modifier = Modifier.heightIn(max = LIST_MAX_HEIGHT)) {
          item {
            ExitNodeRow(
                label = stringResource(R.string.none),
                selected = snapshot.activeExitNodeId == null,
                onClick = { onSelect(null) },
            )
          }
          items(snapshot.exitNodes, key = { it.id }) { node ->
            ExitNodeRow(
                label =
                    if (node.online) node.label
                    else stringResource(R.string.widget_exit_node_offline, node.label),
                selected = snapshot.activeExitNodeId == node.id,
                onClick = { onSelect(node.id) },
            )
          }
        }
        HorizontalDivider()
        // Mullvad exit nodes and the other exit node settings are only in the app.
        ExitNodeRow(
            label = stringResource(R.string.widget_exit_node_more),
            selected = false,
            onClick = onMore,
        )
      }
    }
  }
}

@Composable
private fun ExitNodeRow(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
          Modifier.fillMaxWidth()
              .clickable(onClick = onClick)
              .padding(horizontal = ROW_PADDING_HORIZONTAL, vertical = ROW_PADDING_VERTICAL),
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(
          imageVector = Icons.Outlined.Check,
          contentDescription = stringResource(R.string.selected),
          tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}
