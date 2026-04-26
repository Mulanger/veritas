@file:Suppress(
    "FunctionName",
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
)

package com.veritas.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veritas.core.design.BrandMark
import com.veritas.core.design.VeritasButton
import com.veritas.core.design.VeritasButtonVariant
import com.veritas.core.design.VeritasColors
import com.veritas.core.design.VeritasTag
import com.veritas.core.design.VeritasType
import kotlinx.coroutines.flow.collectLatest

@Composable
fun OnboardingRoute(
    statusStore: OnboardingStatusStore,
    supportsNotificationsPermission: Boolean,
    notificationPermissionGrantedAtLaunch: Boolean,
    overlayPermissionGranted: Boolean,
    requestOverlayPermission: ((Boolean) -> Unit) -> Unit,
    requestNotificationsPermission: ((Boolean) -> Unit) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onboardingViewModel: OnboardingViewModel =
        viewModel(
            factory =
                OnboardingViewModel.factory(
                    statusStore = statusStore,
                    supportsNotificationsPermission = supportsNotificationsPermission,
                    notificationPermissionGrantedAtLaunch = notificationPermissionGrantedAtLaunch,
                ),
        )
    val uiState by onboardingViewModel.uiState.collectAsState()

    LaunchedEffect(onboardingViewModel) {
        onboardingViewModel.completionEvents.collectLatest {
            onComplete()
        }
    }

    LaunchedEffect(uiState.currentStep, overlayPermissionGranted) {
        if (uiState.currentStep == OnboardingStep.OverlayPermission && overlayPermissionGranted) {
            onboardingViewModel.handleOverlayPermissionResult(granted = true)
        }
    }

    OnboardingScreen(
        uiState = uiState,
        overlayPermissionGranted = overlayPermissionGranted,
        onContinueFromWelcome = onboardingViewModel::continueFromWelcome,
        onSkipWelcome = onboardingViewModel::completeOnboarding,
        onContinueFromCapabilities = onboardingViewModel::continueFromCapabilities,
        onContinueFromHowItWorks = onboardingViewModel::continueFromHowItWorks,
        onShowPrivacyPolicy = onboardingViewModel::showPrivacyPolicy,
        onHidePrivacyPolicy = onboardingViewModel::hidePrivacyPolicy,
        onContinueFromShareSetup = onboardingViewModel::continueFromShareSetup,
        onShowShareExample = onboardingViewModel::continueFromShareSetup,
        onEnableBubble = {
            onboardingViewModel.chooseOverlayPath(
                wantsBubble = true,
                overlayPermissionAlreadyGranted = overlayPermissionGranted,
            )
        },
        onSkipBubble = {
            onboardingViewModel.chooseOverlayPath(
                wantsBubble = false,
                overlayPermissionAlreadyGranted = overlayPermissionGranted,
            )
        },
        onOpenOverlaySettings = {
            requestOverlayPermission(onboardingViewModel::handleOverlayPermissionResult)
        },
        onContinueWithoutBubble = onboardingViewModel::continueWithoutBubble,
        onContinueFromMediaProjection = onboardingViewModel::continueFromMediaProjection,
        onAllowNotifications = {
            requestNotificationsPermission(onboardingViewModel::handleNotificationPermissionResult)
        },
        onSkipNotifications = onboardingViewModel::skipNotifications,
        onContinueAfterNotificationDenied = onboardingViewModel::continueAfterNotificationDenied,
        onBack = onboardingViewModel::goBack,
        onComplete = onboardingViewModel::completeOnboarding,
        modifier = modifier,
    )
}

@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    overlayPermissionGranted: Boolean,
    onContinueFromWelcome: () -> Unit,
    onSkipWelcome: () -> Unit,
    onContinueFromCapabilities: () -> Unit,
    onContinueFromHowItWorks: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
    onHidePrivacyPolicy: () -> Unit,
    onContinueFromShareSetup: () -> Unit,
    onShowShareExample: () -> Unit,
    onEnableBubble: () -> Unit,
    onSkipBubble: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onContinueWithoutBubble: () -> Unit,
    onContinueFromMediaProjection: () -> Unit,
    onAllowNotifications: () -> Unit,
    onSkipNotifications: () -> Unit,
    onContinueAfterNotificationDenied: () -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = uiState.canGoBack, onBack = onBack)

    if (uiState.privacyPolicyVisible) {
        PrivacyPolicyDialog(onDismiss = onHidePrivacyPolicy)
    }

    when (uiState.currentStep) {
        OnboardingStep.Welcome ->
            WelcomeScreen(
                onContinue = onContinueFromWelcome,
                onSkip = onSkipWelcome,
                modifier = modifier,
            )
        OnboardingStep.WhatVeritasDoes ->
            CapabilitiesScreen(
                onContinue = onContinueFromCapabilities,
                onBack = onBack,
                modifier = modifier,
            )
        OnboardingStep.HowItWorks ->
            HowItWorksScreen(
                onContinue = onContinueFromHowItWorks,
                onShowPrivacyPolicy = onShowPrivacyPolicy,
                onBack = onBack,
                modifier = modifier,
            )
        OnboardingStep.ShareSetup ->
            ShareSetupScreen(
                onContinue = onContinueFromShareSetup,
                onShowExample = onShowShareExample,
                onBack = onBack,
                modifier = modifier,
            )
        OnboardingStep.OverlayIntro ->
            OverlayIntroScreen(
                onSkip = onSkipBubble,
                onEnableBubble = onEnableBubble,
                onBack = onBack,
                modifier = modifier,
            )
        OnboardingStep.OverlayPermission ->
            OverlayPermissionScreen(
                overlayPermissionGranted = overlayPermissionGranted,
                overlayPermissionDenied = uiState.overlayPermissionDenied,
                onOpenSettings = onOpenOverlaySettings,
                onSkip = onContinueWithoutBubble,
                onBack = onBack,
                modifier = modifier,
            )
        OnboardingStep.MediaProjectionEducation ->
            MediaProjectionEducationScreen(
                onContinue = onContinueFromMediaProjection,
                onBack = onBack,
                modifier = modifier,
            )
        OnboardingStep.Notifications ->
            NotificationsScreen(
                notificationPermissionDenied = uiState.notificationPermissionDenied,
                onAllow = onAllowNotifications,
                onSkip = onSkipNotifications,
                onContinueAfterDenied = onContinueAfterNotificationDenied,
                onBack = onBack,
                modifier = modifier,
            )
        OnboardingStep.Ready ->
            ReadyScreen(
                onComplete = onComplete,
                onBack = onBack,
                modifier = modifier,
            )
    }
}

@Composable
private fun WelcomeScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg)
                .testTag(ONBOARDING_WELCOME_TAG)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        BrandHeader()
        Spacer(modifier = Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                text = "Know what's real.",
                style = VeritasType.displayLg.copy(color = VeritasColors.ink),
            )
            Text(
                text = "Veritas checks videos, audio, and images for signs they were made by AI — right on your phone, in about 4 seconds.",
                style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        VeritasButton(
            text = "Get started",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            testTag = ONBOARDING_WELCOME_PRIMARY_TAG,
        )
        Spacer(modifier = Modifier.height(14.dp))
        CenteredTextLink(
            text = "I've used Veritas before",
            onClick = onSkip,
        )
    }
}

@Composable
private fun CapabilitiesScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingFrame(
        modifier = modifier.testTag(ONBOARDING_CAPABILITIES_TAG),
    ) {
        Text(
            text = "Check any video, audio, or image",
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CapabilityCard(
                badge = "01",
                title = "Check any video, audio, or image",
                body = "Share a file from any app — TikTok, WhatsApp, Instagram, anywhere. Or pick one from your phone.",
            )
            CapabilityCard(
                badge = "02",
                title = "See evidence, not a verdict",
                body = "Veritas shows you where the signs are — a heatmap, a timeline, and plain-English reasons. You decide what to trust.",
            )
            CapabilityCard(
                badge = "03",
                title = "Nothing leaves your phone",
                body = "All analysis runs on-device. Your media is never uploaded, never shared, never stored longer than needed.",
            )
        }
        FooterButtons(
            primaryText = "Continue",
            primaryTag = ONBOARDING_CAPABILITIES_PRIMARY_TAG,
            onPrimary = onContinue,
            secondaryText = "Back",
            onSecondary = onBack,
        )
    }
}

@Composable
private fun HowItWorksScreen(
    onContinue: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingFrame(
        modifier = modifier.testTag(ONBOARDING_HOW_IT_WORKS_TAG),
    ) {
        Text(
            text = "Your phone, not a server.",
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
        )
        Text(
            text = "Here's exactly what Veritas does and doesn't do.",
            style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
        )
        HorizontalDivider(color = VeritasColors.line)
        ComparisonColumn(
            label = "Veritas does",
            marker = "+",
            markerColor = VeritasColors.ok,
            items =
                listOf(
                    "Run detection models on your device's NPU",
                    "Look up Content Credentials (C2PA) via signed public directories",
                    "Download signed model updates over the internet",
                ),
        )
        ComparisonColumn(
            label = "Veritas does not",
            marker = "×",
            markerColor = VeritasColors.bad,
            items =
                listOf(
                    "Upload, store, or share the media you check",
                    "Keep your media after you close a verdict",
                    "Have user accounts or track you across apps",
                    "Send your verdicts to advertisers or anyone else",
                ),
        )
        FooterButtons(
            primaryText = "I understand",
            primaryTag = ONBOARDING_HOW_IT_WORKS_PRIMARY_TAG,
            onPrimary = onContinue,
            textLink = "Read full privacy policy",
            onTextLink = onShowPrivacyPolicy,
            secondaryText = "Back",
            onSecondary = onBack,
        )
    }
}

@Composable
private fun ShareSetupScreen(
    onContinue: () -> Unit,
    onShowExample: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingFrame(
        modifier = modifier.testTag(ONBOARDING_SHARE_SETUP_TAG),
    ) {
        Text(
            text = "You'll find Veritas in the share menu.",
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
        )
        AssetPlaceholder(
            label = "ASSET · SHARE SHEET EXAMPLE",
            aspectRatio = 1.2f,
        )
        Text(
            text = "When you see a suspicious video, tap Share → Veritas. We'll take it from there.",
            style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
        )
        Text(
            text = "Some apps only allow sharing as a link. Paste the link into Veritas instead — we'll resolve it for you.",
            style = VeritasType.bodySm.copy(color = VeritasColors.inkMute),
        )
        FooterButtons(
            primaryText = "Got it",
            primaryTag = ONBOARDING_SHARE_SETUP_PRIMARY_TAG,
            onPrimary = onContinue,
            textLink = "Show me an example",
            onTextLink = onShowExample,
            secondaryText = "Back",
            onSecondary = onBack,
        )
    }
}

@Composable
private fun OverlayIntroScreen(
    onSkip: () -> Unit,
    onEnableBubble: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingFrame(
        modifier = modifier.testTag(ONBOARDING_OVERLAY_INTRO_TAG),
    ) {
        Text(
            text = "Want a faster way?",
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
        )
        Text(
            text = "The Veritas bubble floats over any app. One tap to check what's on screen.",
            style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
        )
        AssetPlaceholder(
            label = "ASSET · FLOATING BUBBLE CROPPED",
            aspectRatio = 1.15f,
        )
        DisclosureCard(
            title = "Only captures when you tap.",
            body = "No background recording, ever.",
        )
        DisclosureCard(
            title = "One-second buffer.",
            body = "The bubble grabs what's on screen now, analyzes it, shows the verdict.",
        )
        DisclosureCard(
            title = "You'll see a notification.",
            body = "Android requires a persistent notification when screen capture is active. We cannot hide this.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VeritasButton(
                text = "Skip for now",
                onClick = onSkip,
                modifier = Modifier.weight(1f),
                variant = VeritasButtonVariant.Secondary,
                testTag = ONBOARDING_OVERLAY_SKIP_TAG,
            )
            VeritasButton(
                text = "Enable bubble",
                onClick = onEnableBubble,
                modifier = Modifier.weight(1f),
                testTag = ONBOARDING_OVERLAY_ENABLE_TAG,
            )
        }
        TextLink(
            text = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun OverlayPermissionScreen(
    overlayPermissionGranted: Boolean,
    overlayPermissionDenied: Boolean,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bodyText =
        if (overlayPermissionDenied && !overlayPermissionGranted) {
            "Looks like that didn't get turned on. No worries — you can still use Veritas by sharing files. You can turn this on later in settings."
        } else {
            "Tap the button below, then turn on \"Allow display over other apps\" in Settings. Come right back when you're done."
        }
    val primaryText =
        if (overlayPermissionDenied && !overlayPermissionGranted) {
            "Continue without bubble"
        } else {
            "Open settings"
        }

    OnboardingFrame(
        modifier = modifier.testTag(ONBOARDING_OVERLAY_PERMISSION_TAG),
    ) {
        Text(
            text = "Allow Veritas to display over other apps",
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
        )
        Text(
            text = bodyText,
            style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
        )
        AssetPlaceholder(
            label = "ASSET · OVERLAY SETTINGS TOGGLE",
            aspectRatio = 1.05f,
        )
        FooterButtons(
            primaryText = primaryText,
            primaryTag =
                if (overlayPermissionDenied && !overlayPermissionGranted) {
                    ONBOARDING_OVERLAY_CONTINUE_TAG
                } else {
                    ONBOARDING_OVERLAY_SETTINGS_TAG
                },
            onPrimary =
                if (overlayPermissionDenied && !overlayPermissionGranted) {
                    onSkip
                } else {
                    onOpenSettings
                },
            textLink =
                if (overlayPermissionDenied && !overlayPermissionGranted) {
                    null
                } else {
                    "I changed my mind, skip"
                },
            onTextLink =
                if (overlayPermissionDenied && !overlayPermissionGranted) {
                    null
                } else {
                    onSkip
                },
            secondaryText = "Back",
            onSecondary = onBack,
        )
    }
}

@Composable
private fun MediaProjectionEducationScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingFrame(
        modifier = modifier.testTag(ONBOARDING_MEDIA_PROJECTION_TAG),
    ) {
        Text(
            text = "One more thing about the bubble.",
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
        )
        Text(
            text = "Every time you tap it, Android will ask \"Allow Veritas to record or cast your screen?\" — tap \"Start now\". This happens fresh each time for your security.",
            style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
        )
        AssetPlaceholder(
            label = "ASSET · MEDIA PROJECTION DIALOG",
            aspectRatio = 1.12f,
        )
        FooterButtons(
            primaryText = "Makes sense",
            primaryTag = ONBOARDING_MEDIA_PROJECTION_PRIMARY_TAG,
            onPrimary = onContinue,
            secondaryText = "Back",
            onSecondary = onBack,
        )
    }
}

@Composable
private fun NotificationsScreen(
    notificationPermissionDenied: Boolean,
    onAllow: () -> Unit,
    onSkip: () -> Unit,
    onContinueAfterDenied: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingFrame(
        modifier = modifier.testTag(ONBOARDING_NOTIFICATIONS_TAG),
    ) {
        Text(
            text = "Can we send notifications?",
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
        )
        Text(
            text = "We use notifications for two things: to show that the bubble is active (Android requires this), and to tell you when a model update finishes. Nothing else.",
            style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
        )
        if (notificationPermissionDenied) {
            StatusNotice(
                text = "The bubble will still work, but you won't see update completion alerts.",
            )
        }
        FooterButtons(
            primaryText = if (notificationPermissionDenied) "Continue" else "Allow",
            primaryTag =
                if (notificationPermissionDenied) {
                    ONBOARDING_NOTIFICATIONS_CONTINUE_TAG
                } else {
                    ONBOARDING_NOTIFICATIONS_ALLOW_TAG
                },
            onPrimary = if (notificationPermissionDenied) onContinueAfterDenied else onAllow,
            secondaryText = if (notificationPermissionDenied) "Back" else "Skip",
            onSecondary = if (notificationPermissionDenied) onBack else onSkip,
        )
    }
}

@Composable
private fun ReadyScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingFrame(
        modifier = modifier.testTag(ONBOARDING_READY_TAG),
    ) {
        ReadyOrb()
        Text(
            text = "You're set up.",
            modifier = Modifier.fillMaxWidth(),
            style = VeritasType.displayMd.copy(color = VeritasColors.ink),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Share a video or image to Veritas from any app. Or open one from your phone using the button below.",
            style = VeritasType.bodyLg.copy(color = VeritasColors.inkDim),
            textAlign = TextAlign.Center,
        )
        FooterButtons(
            primaryText = "Open Veritas",
            primaryTag = ONBOARDING_READY_PRIMARY_TAG,
            onPrimary = onComplete,
            secondaryText = "Back",
            onSecondary = onBack,
        )
    }
}

@Composable
private fun OnboardingFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(VeritasColors.bg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        BrandHeader()
        Spacer(modifier = Modifier.height(26.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.Start,
            content = content,
        )
    }
}

@Composable
private fun BrandHeader() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark()
        Text(
            text = "VERITAS",
            style =
                VeritasType.monoSm.copy(
                    color = VeritasColors.ink,
                    fontWeight = FontWeight.W700,
                    fontSize = 12.sp,
                ),
        )
    }
}

@Composable
private fun CapabilityCard(
    badge: String,
    title: String,
    body: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VeritasColors.panel)
                .border(1.dp, VeritasColors.line, RoundedCornerShape(16.dp))
                .padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = badge,
            style =
                VeritasType.monoSm.copy(
                    color = VeritasColors.accent,
                    fontWeight = FontWeight.W700,
                ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = VeritasType.headingSm.copy(color = VeritasColors.ink),
            )
            Text(
                text = body,
                style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
            )
        }
    }
}

@Composable
private fun ComparisonColumn(
    label: String,
    marker: String,
    markerColor: Color,
    items: List<String>,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(VeritasColors.panel)
                .border(1.dp, VeritasColors.line, RoundedCornerShape(16.dp))
                .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VeritasTag(text = label.uppercase())
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = marker,
                    style =
                        VeritasType.headingSm.copy(
                            color = markerColor,
                            fontWeight = FontWeight.W700,
                        ),
                )
                Text(
                    text = item,
                    modifier = Modifier.weight(1f),
                    style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
                )
            }
        }
    }
}

@Composable
private fun DisclosureCard(
    title: String,
    body: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(VeritasColors.panel)
                .border(1.dp, VeritasColors.line2, RoundedCornerShape(14.dp))
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(VeritasColors.accent),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = VeritasType.headingSm.copy(color = VeritasColors.ink),
            )
            Text(
                text = body,
                style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
            )
        }
    }
}

@Composable
private fun StatusNotice(text: String) {
    Text(
        text = text,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(VeritasColors.warnDim.copy(alpha = 0.28f))
                .border(1.dp, VeritasColors.warnDim, RoundedCornerShape(14.dp))
                .padding(16.dp),
        style = VeritasType.bodyMd.copy(color = VeritasColors.ink),
    )
}

@Composable
private fun AssetPlaceholder(
    label: String,
    aspectRatio: Float,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    VeritasColors.panel2,
                                    VeritasColors.panel,
                                ),
                        ),
                )
                .border(1.dp, VeritasColors.line, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = VeritasType.monoSm.copy(color = VeritasColors.inkMute),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReadyOrb() {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(targetValue = if (visible) 1f else 0.4f, label = "readyAlpha")
    val scale by animateFloatAsState(targetValue = if (visible) 1f else 0.86f, label = "readyScale")

    LaunchedEffect(Unit) {
        visible = true
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(112.dp)
                    .alpha(alpha)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    VeritasColors.accent.copy(alpha = 0.35f),
                                    VeritasColors.accent.copy(alpha = 0.1f),
                                    VeritasColors.panel,
                                ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            BrandMark(size = 28.dp)
        }
    }
}

@Composable
private fun FooterButtons(
    primaryText: String,
    primaryTag: String,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    textLink: String? = null,
    onTextLink: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VeritasButton(
            text = primaryText,
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
            testTag = primaryTag,
        )
        if (textLink != null && onTextLink != null) {
            CenteredTextLink(
                text = textLink,
                onClick = onTextLink,
            )
        }
        if (secondaryText != null && onSecondary != null) {
            TextLink(
                text = secondaryText,
                onClick = onSecondary,
            )
        }
    }
}

@Composable
private fun CenteredTextLink(
    text: String,
    onClick: () -> Unit,
) {
    TextLink(
        text = text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.clickable(onClick = onClick),
        style = VeritasType.bodyMd.copy(color = VeritasColors.inkMute),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(VeritasColors.bg)
                    .border(1.dp, VeritasColors.line, RoundedCornerShape(22.dp))
                    .testTag(ONBOARDING_PRIVACY_POLICY_TAG)
                    .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Your data, your device.",
                style = VeritasType.headingLg.copy(color = VeritasColors.ink),
            )
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PolicySection(
                    title = "What Veritas stores",
                    items = listOf("Only history items (thumbnails, verdicts, metadata). Not the original media."),
                )
                PolicySection(
                    title = "What Veritas sends",
                    items = listOf("Signed model updates downloaded. Anonymous telemetry if enabled (see 5.7)."),
                )
                PolicySection(
                    title = "What Veritas never does",
                    items =
                        listOf(
                            "Upload your media.",
                            "Sell your data.",
                            "Track you across apps.",
                            "Create user accounts.",
                        ),
                )
                PolicySection(
                    title = "Privacy rows",
                    items =
                        listOf(
                            "Clear history",
                            "Clear all data",
                            "Read privacy policy",
                        ),
                )
            }
            VeritasButton(
                text = "Done",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    items: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = VeritasType.headingSm.copy(color = VeritasColors.ink),
        )
        items.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "•",
                    style = VeritasType.bodyMd.copy(color = VeritasColors.accent),
                )
                Text(
                    text = item,
                    modifier = Modifier.weight(1f),
                    style = VeritasType.bodyMd.copy(color = VeritasColors.inkDim),
                )
            }
        }
    }
}
