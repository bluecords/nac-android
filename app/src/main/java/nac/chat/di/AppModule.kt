package nac.chat.di

import nac.chat.persistence.KVStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

//
val appModule = module {
    single { KVStorage(androidContext()) }
}