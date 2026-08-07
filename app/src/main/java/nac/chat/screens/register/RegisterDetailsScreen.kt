package nac.chat.screens.register

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel
import nac.chat.R
import nac.chat.NACApplication
import nac.chat.api.StoatAPI
import nac.chat.api.routes.account.RegistrationBody
import nac.chat.api.routes.account.negotiateAuthentication
import nac.chat.api.routes.account.register
import nac.chat.api.routes.misc.getRootRoute
import nac.chat.api.routes.onboard.needsOnboarding
import nac.chat.callbacks.PendingInvite
import nac.chat.composables.generic.FormTextField
import nac.chat.persistence.KVStorage
import com.hcaptcha.sdk.HCaptcha
import com.hcaptcha.sdk.HCaptchaConfig
import com.hcaptcha.sdk.HCaptchaSize
import com.hcaptcha.sdk.HCaptchaTheme
import kotlinx.coroutines.launch

class RegisterDetailsScreenViewModel(
    private val kvStorage: KVStorage
) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    private var captchaToken by mutableStateOf<String?>(null)

    private var _navigateTo by mutableStateOf<String?>(null)
    val navigateTo: String?
        get() = _navigateTo

    fun navigationComplete() {
        _navigateTo = null
    }

    fun initCaptcha(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val root = try {
                getRootRoute()
            } catch (e: Exception) {
                error = if (e.message?.startsWith("Expected response body of the type") == true) {
                    NACApplication.instance.getString(R.string.service_health_alert_body_default)
                } else e.message
                return@launch
            }

            if (!root.features.captcha.enabled) {
                onSuccess()
                return@launch
            }

            val config = HCaptchaConfig.builder().apply {
                siteKey(root.features.captcha.key)
                theme(HCaptchaTheme.DARK)
                size(HCaptchaSize.INVISIBLE)
            }.build()

            HCaptcha.getClient(context).apply {
                addOnSuccessListener {
                    captchaToken = it.tokenResult
                    onSuccess()
                }

                addOnFailureListener {
                    error = it.message
                }

                setup(config)
                verifyWithHCaptcha()
            }
        }
    }

    fun doRegistration() {
        // The server is invite_only: /auth/account/create rejects a body with no invite.
        // Read the pending code without clearing it — ChatRouterScreen still needs it to
        // perform the separate POST /invites/<code> that actually joins the server.
        val body = RegistrationBody(
            email = email,
            password = password,
            invite = PendingInvite.code,
            captcha = captchaToken ?: ""
        )

        viewModelScope.launch {
            val result = register(body)

            if (!result.ok) {
                val type = result.unwrapError().type
                error = when (type) {
                    "MissingInvite" -> NACApplication.instance.getString(R.string.register_error_missing_invite)
                    "InvalidInvite" -> NACApplication.instance.getString(R.string.register_error_invalid_invite)
                    else -> type
                }
                return@launch
            }

            // Account created. If the server requires email verification, show the
            // verification screen. Otherwise (verification disabled server-side) auto-log
            // the user in so they never hit a dead-end "check your email" wall.
            val needsEmailVerification = try {
                getRootRoute().features.email
            } catch (e: Exception) {
                true // be safe: fall back to the verification screen
            }

            if (needsEmailVerification) {
                _navigateTo = "verify"
                return@launch
            }

            try {
                val response = negotiateAuthentication(email, password)
                if (response.error != null || response.proceedMfa || response.firstUserHints == null) {
                    // Couldn't auto-login (or unexpected MFA on a brand-new account);
                    // send the user to the login screen to sign in manually.
                    _navigateTo = "login"
                    return@launch
                }

                val token = response.firstUserHints.token
                val id = response.firstUserHints.id

                kvStorage.set("sessionToken", token)
                kvStorage.set("sessionId", id)

                if (needsOnboarding(token)) {
                    _navigateTo = "onboarding"
                    return@launch
                }

                StoatAPI.loginAs(token)
                StoatAPI.setSessionId(id)
                _navigateTo = "home"
            } catch (e: Exception) {
                error = e.message ?: "Could not sign in"
            }
        }
    }
}

@Composable
fun RegisterDetailsScreen(
    navController: NavController,
    viewModel: RegisterDetailsScreenViewModel = koinViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(viewModel.navigateTo) {
        when (viewModel.navigateTo) {
            "verify" -> navController.navigate("register/verify/${viewModel.email}")
            "onboarding" -> navController.navigate("register/onboarding") {
                popUpTo(0) { inclusive = true }
            }
            "home" -> navController.navigate("chat") {
                popUpTo(0) { inclusive = true }
            }
            "login" -> navController.navigate("login/login")
        }
        if (viewModel.navigateTo != null) {
            viewModel.navigationComplete()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .imePadding()
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.register_form_heading),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.register_data),
                color = MaterialTheme.colorScheme.onBackground.copy(
                    alpha = 0.5f
                ),
                style = MaterialTheme.typography.titleMedium.copy(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Normal
                ),
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FormTextField(
                    value = viewModel.email,
                    onChange = { viewModel.email = it },
                    label = stringResource(R.string.register_email),
                    type = KeyboardType.Email,
                    action = ImeAction.Next,
                    modifier = Modifier.semantics { contentType = ContentType.EmailAddress }
                )
                Text(
                    text = stringResource(R.string.register_email_verification_hint),
                    color = MaterialTheme.colorScheme.onBackground.copy(
                        alpha = 0.5f
                    ),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                FormTextField(
                    value = viewModel.password,
                    onChange = { viewModel.password = it },
                    label = stringResource(R.string.register_password),
                    type = KeyboardType.Password,
                    action = ImeAction.Done,
                    modifier = Modifier.semantics { contentType = ContentType.NewPassword }
                )
                Text(
                    text = stringResource(R.string.register_password_rules),
                    color = MaterialTheme.colorScheme.onBackground.copy(
                        alpha = 0.5f
                    ),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp)
                )

                if (!viewModel.error.isNullOrBlank()) {
                    Text(
                        text = viewModel.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row {
            TextButton(onClick = {
                navController.popBackStack()
            }) {
                Text(text = stringResource(R.string.back))
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    viewModel.initCaptcha(context) {
                        viewModel.doRegistration()
                    }
                },
                enabled = viewModel.email.isNotBlank() && viewModel.password.isNotBlank()
            ) {
                Text(text = stringResource(R.string.signup))
            }
        }
    }
}
