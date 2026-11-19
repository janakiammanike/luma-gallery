package com.luma.tvremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Persistent navigation keys that stay available on every control screen.
 * Home / Back / Recent / Menu are the fastest way out of an app (e.g. leaving
 * a YouTube video) and must not depend on the customizable button groups.
 */
@Composable
fun NavBar(vm: RemoteViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavKey("⌂  Home", Modifier.weight(1f)) { vm.pressButton("HOME") }
            NavKey("◀  Back", Modifier.weight(1f)) { vm.pressButton("BACK") }
            NavKey("⊞  Recent", Modifier.weight(1f)) { vm.pressButton("RECENT") }
            NavKey("☰  Menu", Modifier.weight(1f)) { vm.pressButton("MENU") }
        }
    }
}

@Composable
private fun NavKey(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}
