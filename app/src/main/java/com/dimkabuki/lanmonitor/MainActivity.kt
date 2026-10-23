package com.dimkabuki.lanmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RoleSelectionScreen()
                }
            }
        }
    }
}

@Composable
private fun RoleSelectionScreen() {
    var selectedRole by rememberSaveable { mutableStateOf<AppRole?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Lan Monitor", style = MaterialTheme.typography.headlineLarge)
        Text("Select how this device will be used.")
        AppRole.values().forEach { role ->
            Button(onClick = { selectedRole = role }, modifier = Modifier.fillMaxWidth()) {
                Text(role.label)
            }
        }
        selectedRole?.let { role ->
            Text("${role.label} setup will be implemented in a later milestone.")
        }
    }
}
