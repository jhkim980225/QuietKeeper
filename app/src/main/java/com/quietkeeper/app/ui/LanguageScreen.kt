package com.quietkeeper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietkeeper.app.R
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.Primary
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary

/** A selectable language. [tag] is a BCP-47 language tag; [native] always renders in its own script. */
private data class Language(val flag: String, val native: String, val tag: String)

private val LANGUAGES = listOf(
    Language("🇰🇷", "한국어", "ko"),
    Language("🇺🇸", "English", "en"),
    Language("🇯🇵", "日本語", "ja"),
    Language("🇨🇳", "中文", "zh"),
    Language("🇪🇸", "Español", "es"),
    Language("🇻🇳", "Tiếng Việt", "vi"),
    Language("🇹🇭", "ภาษาไทย", "th"),
    Language("🇮🇩", "Bahasa Indonesia", "in"),
    Language("🇵🇭", "Filipino", "fil"),
    Language("🇲🇾", "Bahasa Melayu", "ms"),
)

@Composable
fun LanguageScreen(onChosen: (tag: String) -> Unit) {
    var selectedTag by remember { mutableStateOf("ko") }

    ScreenScaffold {
        // Logo tile
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔊", fontSize = 34.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.choose_language),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.language_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Decorative search field
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.search_language),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Scrollable language list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(LANGUAGES, key = { it.tag }) { lang ->
                LanguageRow(
                    language = lang,
                    selected = lang.tag == selectedTag,
                    onClick = { selectedTag = lang.tag },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { onChosen(selectedTag) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            Text(
                text = stringResource(R.string.start_app),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LanguageRow(language: Language, selected: Boolean, onClick: () -> Unit) {
    GlassCard(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(language.flag, fontSize = 24.sp)
            Spacer(Modifier.width(16.dp))
            Text(
                text = language.native,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text("✓", color = Primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
