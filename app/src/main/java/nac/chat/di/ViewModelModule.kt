package nac.chat.di

import nac.chat.activities.MainActivityViewModel
import nac.chat.activities.ShareTargetScreenViewModel
import nac.chat.screens.chat.ChatRouterViewModel
import nac.chat.screens.chat.views.channel.ChannelScreenViewModel
import nac.chat.screens.login.LoginViewModel
import nac.chat.screens.login.MfaScreenViewModel
import nac.chat.screens.register.RegisterDetailsScreenViewModel
import nac.chat.screens.settings.AccountSettingsScreenViewModel
import nac.chat.screens.settings.AppearanceSettingsScreenViewModel
import nac.chat.screens.settings.DebugSettingsScreenViewModel
import nac.chat.screens.settings.NotificationsSettingsScreenViewModel
import nac.chat.screens.settings.ProfileSettingsScreenViewModel
import nac.chat.screens.settings.SettingsScreenViewModel
import nac.chat.screens.settings.channel.ChannelSettingsOverviewViewModel
import nac.chat.sheets.MemberListSheetViewModel
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { MainActivityViewModel(get(), androidContext()) }
    viewModel { ChatRouterViewModel(get(), androidContext()) }
    viewModel { MemberListSheetViewModel(androidApplication()) }
    viewModel { ShareTargetScreenViewModel(get()) }
    viewModel { ChannelScreenViewModel(get()) }
    viewModel { MfaScreenViewModel(get()) }
    viewModel { SettingsScreenViewModel(get()) }
    viewModel { DebugSettingsScreenViewModel(get()) }
    viewModel { NotificationsSettingsScreenViewModel(get(), androidContext()) }
    viewModel { LoginViewModel(get()) }
    viewModel { RegisterDetailsScreenViewModel(get()) }
    viewModel { ProfileSettingsScreenViewModel(androidApplication()) }
    viewModel { AppearanceSettingsScreenViewModel(androidApplication()) }
    viewModel { ChannelSettingsOverviewViewModel(androidApplication()) }
    viewModel { AccountSettingsScreenViewModel(androidApplication()) }
}