package org.schabi.newpipe.videoprime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * VideoPrime: the filter button of every screen (Inicio, Suscripciones, Directos, Búsqueda).
 * Selected = the app's violet (violet means "on"); [dot] is the platform's color.
 * @param small the second row of filters (Búsqueda): a lighter violet with a violet border
 */
@Composable
fun VideoPrimeChip(
    label: String,
    selected: Boolean,
    dot: Color?,
    small: Boolean = false,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
        },
        leadingIcon = dot?.let {
            {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (selected && !small) Color.White else it, CircleShape)
                )
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            selectedContainerColor = if (small) {
                VideoPrimeColors.Accent.copy(alpha = 0.22f)
            } else {
                VideoPrimeColors.Accent
            },
            selectedLabelColor = if (small) MaterialTheme.colorScheme.onSurface else Color.White,
            selectedLeadingIconColor = Color.White
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = if (small) VideoPrimeColors.Accent else Color.Transparent
        ),
        modifier = Modifier.height(if (small) 34.dp else 40.dp)
    )
}
