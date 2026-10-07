package com.mclauncher.core

object NativeBridge {
    init {
        System.loadLibrary("mlcbridge")
    }

    external fun setEnv(key: String, value: String)
    external fun getEnv(key: String): String
    external fun getAbiCode(): Int
    external fun logNative(message: String)
}
