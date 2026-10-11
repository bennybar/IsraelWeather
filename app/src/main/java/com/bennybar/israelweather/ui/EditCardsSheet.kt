package com.bennybar.israelweather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bennybar.israelweather.R
import com.bennybar.israelweather.app
import com.bennybar.israelweather.data.Card

fun cardTitle(card: Card) = when (card) {
    Card.HERO -> R.string.card_hero
    Card.ALERTS -> R.string.card_alerts
    Card.HOURLY -> R.string.card_hourly
    Card.WEEK -> R.string.card_week
    Card.DETAILS -> R.string.card_details
    Card.RADAR -> R.string.card_radar
    Card.SUMMARY -> R.string.card_summary
}

/** Reorder and show/hide the forecast page's sections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCardsSheet(onDismiss: () -> Unit) {
    val settings by app.settings.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.edit_cards), Modifier.weight(1f).padding(start = 4.dp), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = { app.settings.resetCards() }) { Text(stringResource(R.string.reset)) }
            }
            settings.cards.forEachIndexed { i, card ->
                val visible = card !in settings.hiddenCards
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(cardTitle(card)), Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { app.settings.moveCard(card, -1) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up))
                    }
                    IconButton(onClick = { app.settings.moveCard(card, 1) }, enabled = i < settings.cards.size - 1) {
                        Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down))
                    }
                    Switch(checked = visible, onCheckedChange = { app.settings.setCardVisible(card, it) })
                }
            }
        }
    }
}
