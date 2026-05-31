package org.ukky.notitrace.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.ukky.notitrace.data.db.entity.NotificationListItemModel
import org.ukky.notitrace.data.db.entity.NotificationType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotificationListItem(
    item: NotificationListItemModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── ヘッダ行: アプリ名 + タグ ────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.appLabel ?: item.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (item.tag != null) {
                    TagChip(tag = item.tag, modifier = Modifier.padding(start = 4.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── タイトル ────
            if (!item.title.isNullOrBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── 本文（1行省略） ────
            val body = item.bigText ?: item.text
            if (!body.isNullOrBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── フッタ行: 時刻 + 通知種別 ────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dateFormat.format(Date(item.receivedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                NotificationTypeChip(
                    type = NotificationType.fromCode(item.notificationType),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
//  通知種別チップ
// ──────────────────────────────────────────────

/**
 * 通知種別ごとに色・アイコンが変わる小さなチップ。
 */
@Composable
fun NotificationTypeChip(
    type: NotificationType,
    modifier: Modifier = Modifier,
) {
    val style = rememberTypeChipStyle(type)

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = style.containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = type.label,
                tint = style.contentColor,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelSmall,
                color = style.contentColor,
            )
        }
    }
}

private data class TypeChipStyle(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

@Composable
private fun rememberTypeChipStyle(type: NotificationType): TypeChipStyle {
    return when (type) {
        NotificationType.REMOTE_PUSH -> TypeChipStyle(
            icon = Icons.Default.Cloud,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        NotificationType.REMOTE_SILENT -> TypeChipStyle(
            icon = Icons.Default.CloudOff,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        NotificationType.LOCAL -> TypeChipStyle(
            icon = Icons.Default.PhoneAndroid,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NotificationType.LOCAL_SILENT -> TypeChipStyle(
            icon = Icons.Default.NotificationsOff,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NotificationType.FOREGROUND_SERVICE -> TypeChipStyle(
            icon = Icons.Default.PushPin,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        NotificationType.ONGOING -> TypeChipStyle(
            icon = Icons.Default.PlayCircle,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        NotificationType.GROUP_SUMMARY -> TypeChipStyle(
            icon = Icons.Default.Layers,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
