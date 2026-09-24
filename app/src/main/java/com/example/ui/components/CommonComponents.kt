package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BuildResult
import com.example.model.StageStatus
import com.example.model.ToolStatus
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import com.example.ui.theme.StatusRed

@Composable
fun StageStatusIcon(
    status: StageStatus,
    modifier: Modifier = Modifier
) {
    when (status) {
        StageStatus.NOT_STARTED -> {
            Surface(
                modifier = modifier.size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "○",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        StageStatus.RUNNING -> {
            Box(
                modifier = modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.5.dp,
                    color = PrimaryBlue
                )
            }
        }
        StageStatus.COMPLETED -> {
            Surface(
                modifier = modifier.size(24.dp),
                shape = CircleShape,
                color = StatusGreen.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = StatusGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        StageStatus.FAILED -> {
            Surface(
                modifier = modifier.size(24.dp),
                shape = CircleShape,
                color = StatusRed.copy(alpha = 0.2f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Failed",
                        tint = StatusRed,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ToolStatusBadge(
    status: ToolStatus,
    modifier: Modifier = Modifier
) {
    val (badgeBg, badgeFg, label) = when (status) {
        ToolStatus.NOT_INSTALLED -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Not Installed")
        ToolStatus.DOWNLOADING -> Triple(PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue, "Downloading")
        ToolStatus.EXTRACTING -> Triple(PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue, "Extracting")
        ToolStatus.VERIFYING -> Triple(PrimaryBlue.copy(alpha = 0.15f), PrimaryBlue, "Verifying")
        ToolStatus.READY -> Triple(StatusGreen.copy(alpha = 0.15f), StatusGreen, "✓ Ready")
        ToolStatus.UPDATE_AVAILABLE -> Triple(StatusOrange.copy(alpha = 0.15f), StatusOrange, "Update Available")
        ToolStatus.CORRUPTED -> Triple(StatusRed.copy(alpha = 0.15f), StatusRed, "Corrupted")
        ToolStatus.ERROR -> Triple(StatusRed.copy(alpha = 0.15f), StatusRed, "Error")
    }

    Surface(
        color = badgeBg,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = badgeFg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ProjectPill(
    projectName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = projectName,
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryBlue,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun BuildResultBadge(
    result: BuildResult,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (result) {
        BuildResult.SUCCESS -> StatusGreen to "SUCCESS"
        BuildResult.FAILED -> StatusRed to "FAILED"
        BuildResult.CANCELLED -> StatusOrange to "CANCELLED"
    }

    Surface(
        color = color.copy(alpha = 0.18f),
        contentColor = color,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
