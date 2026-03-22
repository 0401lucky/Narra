import os

filepath = 'd:/code/AndroidStudioprojects/MyApplication/app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsModelScreen.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    lines = f.read().splitlines()

part1 = lines[:194]

role_model_card = """
@Composable
private fun RoleModelCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    currentModelId: String,
    onClick: () -> Unit,
) {
    val palette = rememberSettingsPalette()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = palette.surface.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, palette.border.copy(alpha=0.2f)),
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.title,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = palette.accentSoft,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = palette.accentStrong,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentModelId.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModelIcon(modelName = currentModelId, size = 20.dp)
                        Text(
                            text = currentModelId,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = palette.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                } else {
                    Text(
                        text = "选择模型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.body,
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = palette.surfaceTint,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = "参数设置",
                            tint = palette.accentStrong,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

"""

modelpicker_idx = -1
for i, line in enumerate(lines):
    if "@Composable" in line and "ModelPickerDialog" in (lines[i+1] if i+1 < len(lines) else ""):
        modelpicker_idx = i
        break

if modelpicker_idx == -1:
    modelpicker_idx = 271

part2 = lines[modelpicker_idx:]

with open(filepath, 'w', encoding='utf-8') as f:
    f.write('\n'.join(part1) + '\n')
    f.write(role_model_card)
    f.write('\n'.join(part2) + '\n')
