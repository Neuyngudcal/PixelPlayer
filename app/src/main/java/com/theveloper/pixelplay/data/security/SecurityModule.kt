package com.theveloper.pixelplay.data.security

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiSecretsStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TelegramDbKeyStore

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    @AiSecretsStore
    fun provideAiSecretsStore(
        @ApplicationContext context: Context
    ): SecretStore = KeystoreSecretStore(
        context = context,
        prefsName = "pixelplay_ai_secrets_enc",
        keyAlias = "pixelplay_ai_secrets_aes_key"
    )

    @Provides
    @Singleton
    @TelegramDbKeyStore
    fun provideTelegramDbKeyStore(
        @ApplicationContext context: Context
    ): SecretStore = KeystoreSecretStore(
        context = context,
        prefsName = "telegram_db_key_enc",
        keyAlias = "pixelplay_telegram_db_aes_key"
    )
}
