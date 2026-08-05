package com.vitalypr.daylog.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vitalypr.daylog.R
import com.vitalypr.daylog.ui.theme.DayLogTheme

/** Root of the app; navigation shell grows here in Phase 3. */
@Composable
fun DayLogRoot() {
    DayLogTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.padding(padding),
            )
        }
    }
}
