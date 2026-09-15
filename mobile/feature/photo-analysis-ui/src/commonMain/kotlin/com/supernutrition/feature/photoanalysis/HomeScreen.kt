package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.feature.photoanalysis.resources.Res
import com.supernutrition.feature.photoanalysis.resources.capybara
import com.supernutrition.feature.photoanalysis.resources.fraunces
import com.supernutrition.feature.photoanalysis.resources.home_background
import com.supernutrition.feature.photoanalysis.resources.home_bg_arch
import com.supernutrition.feature.photoanalysis.resources.bubble_tail_vector
import com.supernutrition.feature.photoanalysis.resources.icon_bean_vector
import com.supernutrition.feature.photoanalysis.resources.icon_chevron_right_vector
import com.supernutrition.feature.photoanalysis.resources.icon_droplet_vector
import com.supernutrition.feature.photoanalysis.resources.icon_footprints_vector
import com.supernutrition.feature.photoanalysis.resources.icon_leaf_today_vector
import com.supernutrition.feature.photoanalysis.resources.icon_leaf_vector
import com.supernutrition.feature.photoanalysis.resources.icon_salad_vector
import com.supernutrition.feature.photoanalysis.resources.icon_sun_vector
import com.supernutrition.feature.photoanalysis.resources.icon_target_vector
import com.supernutrition.feature.photoanalysis.resources.logo_sprout_vector
import com.supernutrition.feature.photoanalysis.resources.nunito
import com.supernutrition.feature.photoanalysis.resources.profile_button_vector
import com.supernutrition.feature.photoanalysis.resources.salad
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

private val HomeInk = Color(0xFF1E4A2E)
private val HomeBody = Color(0xFF33302C)
private val HomeMuted = Color(0xFFA08D80)
private val HomeCream = Color(0xFFFAF6F4)
private val HomeCard = Color(0xFFFDFCFB)
private val HomeLine = Color(0xFFF0EDEA)
private val ChipGreen = Color(0xFFDDEAE3)
private val ChipLilac = Color(0xFFEDE7F6)
private val ChipLilacLine = Color(0xFFDFC9E5)
private val Amber = Color(0xFFD98324)

private const val DESIGN_WIDTH = 375f
private const val DESIGN_HEIGHT = 812f
private const val BRAND_NAME = "Super Nutrition"

@Composable
private fun frauncesFont() = FontFamily(Font(Res.font.fraunces))

@Composable
private fun nunitoFont() = FontFamily(Font(Res.font.nunito))

@Immutable
data class HomeMetricModel(
    val label: String,
    val value: String,
)

@Immutable
data class HomeScreenModel(
    val title: String,
    val greeting: String,
    val dailySummaryTitle: String,
    val dailySummaryValue: String,
    val dailySummaryDescription: String,
    val emptyTitle: String,
    val emptyDescription: String,
    val addFoodLabel: String,
    val dateLabel: String,
    val careQuestion: String,
    val rhythmTitle: String,
    val rhythmSubtitle: String,
    val rhythmBadgeLabel: String,
    val fiber: HomeMetricModel,
    val water: HomeMetricModel,
    val protein: HomeMetricModel,
    val movement: HomeMetricModel,
    val nextStepTitle: String,
    val nextStepDescription: String,
    val nextStepAction: String,
    val viewGoalsLabel: String,
    val goalsTitle: String,
    val goalsDescription: String,
    val goalsDoneLabel: String,
    val profileActionLabel: String,
    val mascotDescription: String,
    val saladDescription: String,
)

/** Native Compose rendering of Figma node 14:125 (Home / Main). */
@Composable
fun HomeScreen(
    model: HomeScreenModel,
    modifier: Modifier = Modifier,
    onAddFoodRequested: () -> Unit = {},
    onViewGoalsRequested: () -> Unit = {},
    onProfileRequested: () -> Unit = {},
) {
    var goalsVisible by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF6F1EA))
            .automationId(UiAutomationIds.HOME_TITLE)
            .semantics { contentDescription = model.title },
        contentAlignment = Alignment.TopStart,
    ) {
        val scale = maxOf(
            maxWidth.value / DESIGN_WIDTH,
            maxHeight.value / DESIGN_HEIGHT,
        )
        val designOffsetX = ((DESIGN_WIDTH - maxWidth.value).coerceAtLeast(0f) / 2f).dp
        val designOffsetY = ((DESIGN_HEIGHT - maxHeight.value).coerceAtLeast(0f) / 2f).dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .automationId(UiAutomationIds.HOME_SCREEN),
            contentAlignment = Alignment.TopStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(
                        x = designOffsetX,
                        y = designOffsetY,
                    )
                    .requiredSize(DESIGN_WIDTH.dp, DESIGN_HEIGHT.dp)
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        this.scaleX = scale
                        this.scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .semantics { contentDescription = model.title },
            ) {
                Image(
                    painter = painterResource(Res.drawable.home_bg_arch),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                Image(
                    painter = painterResource(Res.drawable.home_background),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                HomeHeader(model, 1f, onProfileRequested)
                Image(
                    painter = painterResource(Res.drawable.capybara),
                    contentDescription = model.mascotDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .offset(92.8f.dp, 262.4f.dp)
                        .size(182.8f.dp),
                )
                HomeCarePrompt(
                    model = model,
                    scale = 1f,
                    onAddFoodRequested = onAddFoodRequested,
                    onViewGoalsRequested = {
                        goalsVisible = true
                        onViewGoalsRequested()
                    },
                )
                HomeRhythmCard(model, 1f)
                HomeNextStep(model, 1f, onAddFoodRequested)
            }
        }
    }

    if (goalsVisible) {
        AlertDialog(
            onDismissRequest = { goalsVisible = false },
            title = { Text(model.goalsTitle, fontFamily = frauncesFont()) },
            text = { Text(model.goalsDescription, color = HomeBody, fontFamily = nunitoFont()) },
            confirmButton = {
                TextButton(onClick = { goalsVisible = false }) {
                    Text(model.goalsDoneLabel, fontFamily = nunitoFont())
                }
            },
        )
    }
}

@Composable
private fun HomeHeader(
    model: HomeScreenModel,
    scale: Float,
    onProfileRequested: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset(
                104.95f.scaled(scale),
                67.41f.scaled(scale),
            )
            .size(168f.scaled(scale), 32f.scaled(scale))
            .semantics { contentDescription = BRAND_NAME },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Italic,
                    ),
                ) {
                    append("Super ")
                }
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                    ),
                ) {
                    append("Nutrition")
                }
            },
            color = Color(0xFF173A00),
            fontFamily = frauncesFont(),
            fontSize = 23.5f.scaledSp(scale),
            lineHeight = 32f.scaledSp(scale),
            letterSpacing = (-0.5056f * scale).sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Image(
            painter = painterResource(Res.drawable.logo_sprout_vector),
            contentDescription = null,
            modifier = Modifier
                .offset(121.11f.scaled(scale), 6.96f.scaled(scale))
                .size(5.97f.scaled(scale), 4.89f.scaled(scale)),
        )
    }
    Text(
        text = model.dateLabel,
        color = HomeInk,
        fontFamily = frauncesFont(),
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5f.scaledSp(scale),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .offset(142f.scaled(scale), 100f.scaled(scale))
            .width(91f.scaled(scale))
            .automationId(UiAutomationIds.HOME_DATE),
    )
    Row(
        modifier = Modifier
            .offset(140f.scaled(scale), 123f.scaled(scale))
            .height(21f.scaled(scale))
            .clip(RoundedCornerShape(11f.scaled(scale)))
            .background(ChipGreen)
            .border(1f.scaled(scale), Color(0xFFAACB6E), RoundedCornerShape(11f.scaled(scale)))
            .padding(horizontal = 9f.scaled(scale), vertical = 4f.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4f.scaled(scale)),
    ) {
        Image(
            painter = painterResource(Res.drawable.icon_leaf_vector),
            contentDescription = null,
            modifier = Modifier.size(11f.scaled(scale)),
        )
        Text(
            text = model.rhythmBadgeLabel,
            color = HomeInk,
            fontFamily = nunitoFont(),
            fontWeight = FontWeight.Bold,
            fontSize = 10f.scaledSp(scale),
            maxLines = 1,
        )
    }
    Box(
        modifier = Modifier
            .offset(314.3f.scaled(scale), 65.7f.scaled(scale))
            .size(40.5f.scaled(scale))
            .clip(RoundedCornerShape(20f.scaled(scale)))
            .background(Color.White.copy(alpha = 0.95f))
            .border(1f.scaled(scale), Color.White.copy(alpha = 0.7f), RoundedCornerShape(20.25f.scaled(scale)))
            .clickable(onClickLabel = model.profileActionLabel, onClick = onProfileRequested)
            .semantics {
                contentDescription = model.profileActionLabel
                role = Role.Button
            }
            .automationId(UiAutomationIds.HOME_PROFILE),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.profile_button_vector),
            contentDescription = null,
            modifier = Modifier.size(22f.scaled(scale)),
        )
    }
}

@Composable
private fun HomeCarePrompt(
    model: HomeScreenModel,
    scale: Float,
    onAddFoodRequested: () -> Unit,
    onViewGoalsRequested: () -> Unit,
) {
    Column(
        modifier = Modifier
            .offset(84f.scaled(scale), 156f.scaled(scale))
            .width(206f.scaled(scale))
            .height(98f.scaled(scale))
            .shadow(10f.scaled(scale), RoundedCornerShape(20f.scaled(scale)), spotColor = Color(0x2E594D3D))
            .clip(RoundedCornerShape(20f.scaled(scale)))
            .background(Color.White)
            .padding(horizontal = 8f.scaled(scale), vertical = 13f.scaled(scale)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6f.scaled(scale)),
    ) {
        Text(
            text = model.careQuestion,
            color = HomeInk,
            fontFamily = frauncesFont(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14f.scaledSp(scale),
            lineHeight = 18f.scaledSp(scale),
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7f.scaled(scale))) {
            HomeChip(
                label = model.addFoodLabel,
                icon = Res.drawable.icon_salad_vector,
                background = ChipGreen,
                borderColor = Color(0xFFCFDBCA),
                textColor = HomeInk,
                scale = scale,
                automationId = UiAutomationIds.HOME_ADD_FOOD,
                onClick = onAddFoodRequested,
            )
            HomeChip(
                label = model.viewGoalsLabel,
                icon = Res.drawable.icon_target_vector,
                background = ChipLilac,
                borderColor = ChipLilacLine,
                textColor = Color(0xFF7A4FBF),
                scale = scale,
                automationId = UiAutomationIds.HOME_VIEW_GOALS,
                onClick = onViewGoalsRequested,
            )
        }
    }
    Image(
        painter = painterResource(Res.drawable.bubble_tail_vector),
        contentDescription = null,
        modifier = Modifier
            .offset(156f.scaled(scale), 254f.scaled(scale))
            .size(17f.scaled(scale), 16f.scaled(scale)),
    )
}

@Composable
private fun HomeChip(
    label: String,
    icon: DrawableResource,
    background: Color,
    borderColor: Color,
    textColor: Color,
    scale: Float,
    automationId: com.supernutrition.core.ui.UiAutomationId,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(30f.scaled(scale))
            .clip(RoundedCornerShape(16f.scaled(scale)))
            .background(background)
            .border(1f.scaled(scale), borderColor, RoundedCornerShape(16f.scaled(scale)))
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .automationId(automationId)
            .padding(horizontal = 4f.scaled(scale), vertical = 5f.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4f.scaled(scale)),
    ) {
        Box(
            modifier = Modifier
                .size(20f.scaled(scale))
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.65f))
                .padding(2f.scaled(scale)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(16f.scaled(scale)),
            )
        }
        Text(
            text = label,
            color = textColor,
            fontFamily = nunitoFont(),
            fontWeight = FontWeight.Bold,
            fontSize = 10.5f.scaledSp(scale),
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeRhythmCard(model: HomeScreenModel, scale: Float) {
    Column(
        modifier = Modifier
            .offset(24f.scaled(scale), 429f.scaled(scale))
            .width(327f.scaled(scale))
            .height(189f.scaled(scale))
            .shadow(8f.scaled(scale), RoundedCornerShape(24f.scaled(scale)), spotColor = Color(0x1F594D3D))
            .clip(RoundedCornerShape(24f.scaled(scale)))
            .background(HomeCream)
            .padding(start = 8f.scaled(scale), end = 8f.scaled(scale), top = 12f.scaled(scale), bottom = 7f.scaled(scale))
            .automationId(UiAutomationIds.HOME_SUMMARY)
            .semantics {
                contentDescription = listOf(
                    model.rhythmTitle,
                    model.rhythmSubtitle,
                    "${model.fiber.label} ${model.fiber.value}",
                    "${model.water.label} ${model.water.value}",
                    "${model.protein.label} ${model.protein.value}",
                    "${model.movement.label} ${model.movement.value}",
                ).joinToString(". ")
            },
        verticalArrangement = Arrangement.spacedBy(9f.scaled(scale)),
    ) {
        Column(
            modifier = Modifier.padding(start = 10f.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(3f.scaled(scale)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6f.scaled(scale))) {
                Image(
                    painter = painterResource(Res.drawable.icon_leaf_vector),
                    contentDescription = null,
                    modifier = Modifier.size(13f.scaled(scale)),
                )
                Text(
                    text = model.rhythmTitle,
                    color = HomeBody,
                    fontFamily = nunitoFont(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5f.scaledSp(scale),
                )
            }
            Text(
                text = model.rhythmSubtitle,
                color = HomeInk,
                fontFamily = frauncesFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14f.scaledSp(scale),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6f.scaled(scale))) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.5f.scaled(scale))) {
                HomeMetricCard(model.fiber, Res.drawable.icon_leaf_today_vector, Color(0xFFDCEBD3), Color(0xFF7CB342), 61f, scale, Modifier.weight(1f))
                HomeMetricCard(model.water, Res.drawable.icon_droplet_vector, Color(0xFFD9EAF7), Color(0xFF3B8FD4), 77f, scale, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.5f.scaled(scale))) {
                HomeMetricCard(model.protein, Res.drawable.icon_bean_vector, Color(0xFFEADCF5), Color(0xFF8B4FD1), 102f, scale, Modifier.weight(1f))
                HomeMetricCard(model.movement, Res.drawable.icon_footprints_vector, Color(0xFFFBE3D0), Color(0xFFF2812F), 91f, scale, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HomeMetricCard(
    metric: HomeMetricModel,
    icon: DrawableResource,
    iconBackground: Color,
    accent: Color,
    progressWidth: Float,
    scale: Float,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .height(60f.scaled(scale))
            .clip(RoundedCornerShape(14f.scaled(scale)))
            .background(HomeCard)
            .border(1f.scaled(scale), HomeLine, RoundedCornerShape(14f.scaled(scale)))
            .padding(start = 9f.scaled(scale), end = 10f.scaled(scale), top = 9f.scaled(scale), bottom = 8f.scaled(scale)),
        verticalArrangement = Arrangement.spacedBy(10f.scaled(scale)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(25f.scaled(scale))
                    .clip(RoundedCornerShape(13f.scaled(scale)))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(15f.scaled(scale)),
                )
            }
            Spacer(Modifier.width(9f.scaled(scale)))
            Column {
                Text(
                    text = metric.label,
                    color = HomeBody,
                    fontFamily = nunitoFont(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5f.scaledSp(scale),
                    lineHeight = 12f.scaledSp(scale),
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3f.scaled(scale))) {
                    val valueParts = metric.value.split(" / ", limit = 2)
                    Text(
                        text = valueParts.firstOrNull().orEmpty(),
                        color = HomeBody,
                        fontFamily = frauncesFont(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5f.scaledSp(scale),
                        lineHeight = 17f.scaledSp(scale),
                    )
                    if (valueParts.size == 2) {
                        Text(
                            text = "/ ${valueParts[1]}",
                            color = HomeMuted,
                            fontFamily = nunitoFont(),
                            fontSize = 9f.scaledSp(scale),
                            lineHeight = 12f.scaledSp(scale),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4f.scaled(scale))
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFE7E4E0)),
        ) {
            Box(
                modifier = Modifier
                    .width(progressWidth.scaled(scale))
                    .height(4f.scaled(scale))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun HomeNextStep(model: HomeScreenModel, scale: Float, onDoIt: () -> Unit) {
    Row(
        modifier = Modifier
            .offset(24.6f.scaled(scale), 624f.scaled(scale))
            .width(325f.scaled(scale))
            .height(71f.scaled(scale))
            .clip(RoundedCornerShape(20f.scaled(scale)))
            .background(Color(0xFFFDF3DF))
            .border(1.5f.scaled(scale), Color(0xFFF0DCA8), RoundedCornerShape(20f.scaled(scale)))
            .padding(start = 12f.scaled(scale), end = 14f.scaled(scale), top = 10f.scaled(scale), bottom = 10f.scaled(scale)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(Res.drawable.salad),
            contentDescription = model.saladDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(62f.scaled(scale), 48f.scaled(scale)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12f.scaled(scale)),
            verticalArrangement = Arrangement.spacedBy(4f.scaled(scale)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6f.scaled(scale)),
            ) {
                Image(
                    painter = painterResource(Res.drawable.icon_sun_vector),
                    contentDescription = null,
                    modifier = Modifier.size(15f.scaled(scale)),
                )
                Text(
                    text = model.nextStepTitle,
                    color = Amber,
                    fontFamily = nunitoFont(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10f.scaledSp(scale),
                    lineHeight = 13f.scaledSp(scale),
                    maxLines = 1,
                    modifier = Modifier.automationId(UiAutomationIds.HOME_EMPTY_TITLE),
                )
            }
            Text(
                text = model.nextStepDescription,
                color = HomeBody,
                fontFamily = frauncesFont(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13f.scaledSp(scale),
                lineHeight = 16f.scaledSp(scale),
                maxLines = 2,
                modifier = Modifier.automationId(UiAutomationIds.HOME_EMPTY_DESCRIPTION),
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFFEECD3))
                .border(1f.scaled(scale), Color(0xFFFED596), RoundedCornerShape(999.dp))
                .clickable(onClickLabel = model.nextStepAction, onClick = onDoIt)
                .semantics {
                    contentDescription = model.nextStepAction
                    role = Role.Button
                }
                .padding(start = 12f.scaled(scale), end = 10f.scaled(scale), top = 6f.scaled(scale), bottom = 6f.scaled(scale)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4f.scaled(scale)),
        ) {
            Text(
                text = model.nextStepAction,
                color = Amber,
                fontFamily = nunitoFont(),
                fontWeight = FontWeight.Bold,
                fontSize = 11f.scaledSp(scale),
            )
            Image(
                painter = painterResource(Res.drawable.icon_chevron_right_vector),
                contentDescription = null,
                modifier = Modifier.size(16f.scaled(scale)),
            )
        }
    }
}

private fun Float.scaled(scale: Float): Dp = (this * scale).dp

private fun Float.scaledSp(scale: Float) = (this * scale).sp
