package com.quietkeeper.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.quietkeeper.app.R
import com.quietkeeper.app.billing.ProStatus
import com.quietkeeper.app.ui.theme.GlassCard
import com.quietkeeper.app.ui.theme.Primary
import com.quietkeeper.app.ui.theme.ScreenScaffold
import com.quietkeeper.app.ui.theme.TextPrimary
import com.quietkeeper.app.ui.theme.TextSecondary

/**
 * Plan-selection paywall. [isPro] is observed by the caller from [ProStatus].
 * A long-press on the title toggles the debug Pro override so testers can flip
 * Pro without a configured Play Console product.
 */
@Composable
fun PaywallScreen(onBack: () -> Unit, onSubscribe: () -> Unit, isPro: Boolean) {
    val context = LocalContext.current
    ScreenScaffold {
        TextButton(onClick = onBack) { Text("← " + stringResource(R.string.back)) }
        Spacer(Modifier.height(8.dp))

        // Title — in DEBUG builds, a long-press toggles the debug Pro override
        // (unobtrusive dev affordance). In release builds there is no UI path
        // to flip Pro, so the long-press handler and hint are compiled out.
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (com.quietkeeper.app.BuildConfig.DEBUG) {
                Text(
                    text = stringResource(R.string.paywall_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = { ProStatus.toggleDebugPro(context) },
                        )
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.paywall_debug_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            } else {
                Text(
                    text = stringResource(R.string.paywall_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.paywall_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(20.dp))

        // Free plan.
        PlanCard(
            name = stringResource(R.string.plan_free_name),
            price = stringResource(R.string.plan_free_price),
            highlighted = false,
            badge = null,
            features = listOf(
                Feature(stringResource(R.string.plan_free_f1)),
                Feature(stringResource(R.string.plan_free_f2)),
                Feature(stringResource(R.string.plan_free_f3)),
                Feature(stringResource(R.string.plan_free_f4)),
                Feature(stringResource(R.string.plan_free_f5), struck = true),
                Feature(stringResource(R.string.plan_free_f6), struck = true),
            ),
        )
        Spacer(Modifier.height(16.dp))

        // Pro plan (highlighted, recommended).
        PlanCard(
            name = stringResource(R.string.plan_pro_name),
            price = stringResource(R.string.plan_pro_price),
            highlighted = true,
            badge = stringResource(R.string.plan_pro_badge),
            features = listOf(
                Feature(stringResource(R.string.plan_pro_f1)),
                Feature(stringResource(R.string.plan_pro_f2)),
                Feature(stringResource(R.string.plan_pro_f3)),
                Feature(stringResource(R.string.plan_pro_f4)),
            ),
        )
        Spacer(Modifier.height(24.dp))

        if (isPro) {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.paywall_active))
            }
        } else {
            Button(
                onClick = onSubscribe,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(stringResource(R.string.paywall_subscribe))
            }
        }
    }
}

private data class Feature(val text: String, val struck: Boolean = false)

@Composable
private fun PlanCard(
    name: String,
    price: String,
    highlighted: Boolean,
    badge: String?,
    features: List<Feature>,
) {
    val cardModifier = if (highlighted) {
        Modifier
            .fillMaxWidth()
            .border(2.dp, Primary, RoundedCornerShape(16.dp))
    } else {
        Modifier.fillMaxWidth()
    }
    GlassCard(cardModifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Primary, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            price,
            style = MaterialTheme.typography.headlineSmall,
            color = if (highlighted) Primary else TextPrimary,
        )
        Spacer(Modifier.height(12.dp))
        features.forEach { f ->
            Text(
                text = (if (f.struck) "✕ " else "✓ ") + f.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (f.struck) TextSecondary else TextPrimary,
                textDecoration = if (f.struck) TextDecoration.LineThrough else null,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}
