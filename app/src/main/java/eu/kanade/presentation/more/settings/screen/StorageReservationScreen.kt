package eu.kanade.presentation.more.settings.screen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.storage.StorageReservationPreferences
import eu.kanade.tachiyomi.util.storage.StorageReservationManager
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object StorageReservationScreen : SearchableSettings {

    private val preferences: StorageReservationPreferences = Injekt.get()
    private val storageReservationManager: StorageReservationManager = Injekt.get()

    @Composable
    override fun getTitleRes() = AYMR.strings.pref_storage_reservation

    @Composable
    override fun getPreferences(): List<Preference> {
        return persistentListOf(
            // Storage Reservation Info
            Preference.PreferenceItem.InfoPreference(
                text = stringResource(AYMR.strings.pref_storage_reservation_info),
            ),
            // Storage Info Display
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.label_storage_info),
            ) {
                StorageReservationInfo()
            },
            // GB Allocation Slider
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(AYMR.strings.pref_storage_reservation_gb),
            ) {
                StorageReservationSlider()
            },
        )
    }

    @Composable
    private fun StorageReservationInfo() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val totalBytes = remember { storageReservationManager.getTotalBytes() }
        val reservedBytes = remember { storageReservationManager.getReservedBytes() }
        val apparentFreeBytes = remember { storageReservationManager.getApparentFreeBytes() }
        val systemUsedBytes = remember { storageReservationManager.getSystemUsedBytes() }

        val totalText = remember(totalBytes) { Formatter.formatFileSize(context, totalBytes) }
        val reservedText = remember(reservedBytes) { Formatter.formatFileSize(context, reservedBytes) }
        val freeText = remember(apparentFreeBytes) { Formatter.formatFileSize(context, apparentFreeBytes) }
        val systemUsedText = remember(systemUsedBytes) { Formatter.formatFileSize(context, systemUsedBytes) }

        // Calculate progress
        val usedProgress = if (totalBytes > 0) {
            ((reservedBytes + systemUsedBytes).toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Storage bar
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.small),
                progress = { usedProgress },
            )

            // Storage details
            Text(
                text = stringResource(
                    AYMR.strings.storage_reservation_details,
                    totalText,
                    reservedText,
                    freeText,
                    systemUsedText,
                ),
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    @Composable
    private fun StorageReservationSlider() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var sliderValue by remember {
            mutableFloatStateOf(preferences.reservedGb().get().toFloat())
        }
        var currentReservedGb by remember { mutableStateOf(preferences.reservedGb().get()) }

        // Sync slider with preference
        LaunchedEffect(currentReservedGb) {
            sliderValue = currentReservedGb.toFloat()
        }

        val displayValue = sliderValue.toInt()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Current value display
            Text(
                text = if (displayValue == 0) {
                    stringResource(AYMR.strings.storage_reservation_disabled)
                } else {
                    stringResource(AYMR.strings.storage_reservation_gb_value, displayValue)
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            // Slider
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    val newGb = sliderValue.toInt()
                    scope.launch {
                        val success = storageReservationManager.allocate(newGb)
                        if (success) {
                            preferences.reservedGb().set(newGb)
                            currentReservedGb = newGb
                            context.toast(
                                if (newGb == 0) {
                                    AYMR.strings.storage_reservation_released
                                } else {
                                    AYMR.strings.storage_reservation_allocated
                                },
                            )
                        } else {
                            sliderValue = currentReservedGb.toFloat()
                            context.toast(AYMR.strings.storage_reservation_error)
                            logcat(LogPriority.ERROR) { "Failed to allocate storage reservation" }
                        }
                    }
                },
                valueRange = 0f..100f,
                steps = 99,
                modifier = Modifier.fillMaxWidth(),
            )

            // Min/Max labels
            RowScope {
                Text(
                    text = stringResource(AYMR.strings.label_disabled),
                    modifier = Modifier.secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodySmall,
                )
                // Spacer
                androidx.compose.foundation.layout.Spacer(
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "100 GB",
                    modifier = Modifier.secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
