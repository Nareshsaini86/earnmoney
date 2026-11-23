package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.signOutUser
import com.example.core.configureAppCheck
import com.example.data.ai.GenesisAiRepository
import com.example.data.firebase.AutomatonRepository
import com.example.data.model.UiState
import com.example.data.remote.ExternalTelemetryService
import com.example.ui.auth.AuthScreen
import com.example.ui.screens.AiStudioScreen
import com.example.ui.screens.PayoutsScreen
import com.example.ui.screens.RevenueDashboardScreen
import com.example.ui.screens.SetupGuideScreen
import com.example.ui.theme.AutomatonDeskTheme
import com.example.ui.viewmodel.AutomatonDeskViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class DeskDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    SETUP("setup", R.string.nav_setup, Icons.Rounded.Terminal, Icons.Outlined.Terminal),
    STUDIO("studio", R.string.nav_studio, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    REVENUE("revenue", R.string.nav_revenue, Icons.Rounded.CurrencyRupee, Icons.Outlined.CurrencyRupee),
    PAYOUTS("payouts", R.string.nav_payouts, Icons.Rounded.Payments, Icons.Outlined.Payments)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure App Check with launch intent before Compose UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            AutomatonDeskTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
private fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = auth.currentUser })
    } else {
        val uid = user.uid
        key(uid) {
            AutomatonDeskAppShell(
                currentUserId = uid,
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutomatonDeskAppShell(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AutomatonDeskViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AutomatonDeskViewModel(
                    repository = AutomatonRepository(db),
                    telemetryService = ExternalTelemetryService(),
                    aiRepository = GenesisAiRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val automatonsState by viewModel.automatonsState.collectAsStateWithLifecycle()
    val eventsState by viewModel.revenueEventsState.collectAsStateWithLifecycle()
    val withdrawalsState by viewModel.withdrawalsState.collectAsStateWithLifecycle()
    val repoState by viewModel.repoState.collectAsStateWithLifecycle()
    val fxRateState by viewModel.fxRateState.collectAsStateWithLifecycle()
    val summary by viewModel.revenueSummary.collectAsStateWithLifecycle()
    val onChainCheckState by viewModel.onChainCheckState.collectAsStateWithLifecycle()
    val aiStreamState by viewModel.aiStreamState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(DeskDestination.REVENUE) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    BackHandler(enabled = currentTab != DeskDestination.REVENUE) {
        currentTab = DeskDestination.REVENUE
    }

    val usdToInrRate = (fxRateState as? UiState.Success)?.data?.rate ?: 84.5

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    ),
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (userEmail.isNotBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        DeskDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        DeskDestination.entries.forEach { dest ->
                            val selected = currentTab == dest
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 880.dp)
                    ) {
                        when (currentTab) {
                            DeskDestination.SETUP -> SetupGuideScreen(
                                repoState = repoState,
                                isBusy = isBusy,
                                onRefreshRepo = { viewModel.refreshExternalTelemetry() },
                                onOpenAiStudio = { currentTab = DeskDestination.STUDIO },
                                onRegisterNode = { name, wallet, model, prompt, endpoint ->
                                    viewModel.createAutomaton(name, wallet, model, prompt, endpoint)
                                },
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )

                            DeskDestination.STUDIO -> AiStudioScreen(
                                aiStreamState = aiStreamState,
                                usdToInrRate = usdToInrRate,
                                isBusy = isBusy,
                                onGenerateStrategy = { niche, budget, idea ->
                                    viewModel.generateAiGenesisStrategy(niche, budget, idea)
                                },
                                onRegisterNode = { name, wallet, model, prompt, endpoint ->
                                    viewModel.createAutomaton(name, wallet, model, prompt, endpoint)
                                },
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )

                            DeskDestination.REVENUE -> RevenueDashboardScreen(
                                automatonsState = automatonsState,
                                eventsState = eventsState,
                                fxRateState = fxRateState,
                                summary = summary,
                                onChainCheckState = onChainCheckState,
                                isBusy = isBusy,
                                onRefreshFx = { viewModel.refreshExternalTelemetry() },
                                onCheckBaseWallet = { addr, node ->
                                    viewModel.checkBaseWalletBalance(addr, node)
                                },
                                onRecordEvent = { node, ch, type, amount, tx, note ->
                                    viewModel.recordRevenueEvent(node, ch, type, amount, tx, note)
                                },
                                onRegisterNode = { name, wallet, model, prompt, endpoint ->
                                    viewModel.createAutomaton(name, wallet, model, prompt, endpoint)
                                },
                                onDeleteNode = { id -> viewModel.deleteAutomaton(id) },
                                onNavigateToPayouts = { currentTab = DeskDestination.PAYOUTS }
                            )

                            DeskDestination.PAYOUTS -> PayoutsScreen(
                                withdrawalsState = withdrawalsState,
                                summary = summary,
                                usdToInrRate = usdToInrRate,
                                isBusy = isBusy,
                                onRequestWithdrawal = { type, dest, name, amount ->
                                    viewModel.requestWithdrawal(type, dest, name, amount)
                                },
                                onUpdateStatus = { id, newStatus ->
                                    viewModel.updateWithdrawalStatus(id, newStatus)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
