package com.example.domonapperfect

import android.app.Application
import com.example.domonapperfect.data.network.NetworkModule
import com.example.domonapperfect.data.repository.AuthRepository
import com.example.domonapperfect.data.repository.IntercomRepository

class DomonapApplication : Application() {
    
    lateinit var authRepository: AuthRepository
    lateinit var intercomRepository: IntercomRepository

    override fun onCreate() {
        super.onCreate()
        
        // Initialize dependencies
        authRepository = AuthRepository(
            api = NetworkModule.domonapApi, // We will fix NetworkModule to be lazy or use interceptor
            context = this
        )
        
        // We will initialize NetworkModule with token provider
        NetworkModule.init(
            tokenProvider = { authRepository.token }
        )
        
        val prefs = getSharedPreferences("domonap_custom", android.content.Context.MODE_PRIVATE)
        intercomRepository = IntercomRepository(api = NetworkModule.domonapApi, prefs = prefs)
    }
}
