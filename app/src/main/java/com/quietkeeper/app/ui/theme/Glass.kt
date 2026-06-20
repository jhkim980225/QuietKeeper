package com.quietkeeper.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Translucent "glass" surface: soft shadow + translucent white fill + thin border. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(16.dp)): Modifier =
    this
        .shadow(2.dp, shape, clip = false)
        .background(GlassFill, shape)
        .border(1.dp, GlassBorder, shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .glass()
            .padding(20.dp),
        content = content,
    )
}

/** Full-screen scaffold with the light-blue background, status-bar inset, optional centered title. */
@Composable
fun ScreenScaffold(
    title: String? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgLight)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(20.dp),
        ) {
            if (title != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                }
                Column(Modifier.padding(top = 16.dp).fillMaxSize(), content = content)
            } else {
                content()
            }
        }
    }
}
