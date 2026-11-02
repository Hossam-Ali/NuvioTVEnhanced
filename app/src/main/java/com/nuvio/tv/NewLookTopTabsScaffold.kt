package com.nuvio.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.ProfileAvatarCircle
import com.nuvio.tv.ui.util.rememberRawSvgPainter
import com.nuvio.tv.ui.navigation.NuvioNavHost
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.rememberDrawerItemFocusRequesters

private val TopTabsBarHeight = 64.dp
private val TopTabFocusedText = Color(0xFF0A0C10)

/**
 * New-look navigation: the main screens as tabs across the top instead of a side menu.
 * Up from the top of a screen reaches the tabs; Back on a main screen goes to the tabs, then exits.
 */
@Composable
internal fun NewLookTopTabsScaffold(
    longPressBackHeld: MutableState<Boolean>,
    navController: NavHostController,
    startDestination: String,
    currentRoute: String?,
    rootRoutes: Set<String>,
    drawerItems: List<DrawerItem>,
    selectedDrawerRoute: String?,
    activeProfileName: String,
    activeProfileColorHex: String,
    activeProfileAvatarImageUrl: String?,
    showProfileSelector: Boolean,
    onSwitchProfile: () -> Unit,
    onNavigate: (String) -> Unit,
    onExitApp: () -> Unit
) {
    val showTabs = currentRoute in rootRoutes
    val tabFocusRequesters = rememberDrawerItemFocusRequesters(drawerItems)
    val contentFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var tabsHaveFocus by remember { mutableStateOf(false) }

    LaunchedEffect(currentRoute) {
        longPressBackHeld.value = false
    }

    BackHandler(enabled = showTabs && !tabsHaveFocus) {
        val route = selectedDrawerRoute ?: drawerItems.firstOrNull()?.route
        route?.let { tabFocusRequesters[it] }?.let { runCatching { it.requestFocus() } }
    }
    BackHandler(enabled = showTabs && tabsHaveFocus) {
        if (longPressBackHeld.value) return@BackHandler
        onExitApp()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (showTabs) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TopTabsBarHeight)
                    .padding(horizontal = 48.dp)
                    .onFocusChanged { tabsHaveFocus = it.hasFocus },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                drawerItems.forEach { item ->
                    key(item.route) {
                        TopTab(
                            item = item,
                            selected = selectedDrawerRoute == item.route,
                            onClick = {
                                keyboardController?.hide()
                                onNavigate(item.route)
                                val sameRoute = currentRoute == item.route
                                navigateToDrawerRoute(
                                    navController = navController,
                                    currentRoute = currentRoute,
                                    targetRoute = item.route
                                )
                                if (sameRoute) runCatching { contentFocusRequester.requestFocus() }
                            },
                            modifier = Modifier.focusRequester(tabFocusRequesters.getValue(item.route))
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (showProfileSelector && activeProfileName.isNotEmpty()) {
                    Surface(
                        onClick = onSwitchProfile,
                        shape = ClickableSurfaceDefaults.shape(CircleShape),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                        modifier = Modifier.semantics { role = Role.Button }
                    ) {
                        ProfileAvatarCircle(
                            name = activeProfileName,
                            colorHex = activeProfileColorHex,
                            size = 36.dp,
                            avatarImageUrl = activeProfileAvatarImageUrl,
                            imageCrossfade = false,
                            modifier = Modifier.padding(3.dp)
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onPreviewKeyEvent { keyEvent ->
                    // A held Back is the app's "open the menu" gesture; here it jumps to the tabs.
                    if (keyEvent.key != Key.Back) return@onPreviewKeyEvent false
                    if (
                        keyEvent.type == KeyEventType.KeyDown &&
                        showTabs &&
                        keyEvent.nativeKeyEvent.isLongPress
                    ) {
                        if (!longPressBackHeld.value) {
                            longPressBackHeld.value = true
                            val route = selectedDrawerRoute ?: drawerItems.firstOrNull()?.route
                            route?.let { tabFocusRequesters[it] }?.let { runCatching { it.requestFocus() } }
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (longPressBackHeld.value) {
                        if (keyEvent.type == KeyEventType.KeyUp) longPressBackHeld.value = false
                        return@onPreviewKeyEvent true
                    }
                    false
                }
        ) {
            CompositionLocalProvider(
                LocalSidebarExpanded provides false,
                LocalContentFocusRequester provides contentFocusRequester
            ) {
                NuvioNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    hideBuiltInHeaders = false
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TopTab(
    item: DrawerItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = if (selected) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = TopTabFocusedText
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.semantics {
            role = Role.Tab
            this.selected = selected
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                item.icon != null -> Icon(item.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                item.iconRes != null -> Icon(
                    painter = rememberRawSvgPainter(item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = item.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
        }
    }
}
