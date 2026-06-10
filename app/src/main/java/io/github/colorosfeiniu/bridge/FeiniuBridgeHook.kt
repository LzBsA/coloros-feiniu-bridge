package io.github.colorosfeiniu.bridge

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.util.zip.ZipFile

class FeiniuBridgeHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        runCatching {
            val tokenDecryptor = XposedHelpers.findClass(TOKEN_DECRYPTOR_CLASS, lpparam.classLoader)
            XposedBridge.hookAllMethods(tokenDecryptor, PREFIX_METHOD, PrefixFallbackHook(lpparam))
            log("installed for ${lpparam.packageName}")
        }.onFailure { error ->
            log("install failed: ${error.javaClass.simpleName}: ${error.message}")
        }
    }

    private class PrefixFallbackHook(
        private val lpparam: XC_LoadPackage.LoadPackageParam,
    ) : XC_MethodHook() {
        override fun afterHookedMethod(param: MethodHookParam) {
            if (param.hasThrowable()) return
            if (!param.result.isNullOrBlankString()) return

            val prefix = PrefixResolver.resolve(lpparam)
            if (prefix.isNullOrBlank()) {
                log("prefix fallback unavailable")
                return
            }

            param.result = prefix
            log("prefix fallback supplied len=${prefix.length}")
        }
    }

    private object PrefixResolver {
        @Volatile
        private var cachedPrefix: String? = null

        fun resolve(lpparam: XC_LoadPackage.LoadPackageParam): String? {
            cachedPrefix?.let { return it }

            val resolved = findFromApkStrings(lpparam)
                ?: KNOWN_PREFIX

            cachedPrefix = resolved
            return resolved
        }

        private fun findFromApkStrings(lpparam: XC_LoadPackage.LoadPackageParam): String? {
            val sourcePaths = buildList {
                add(lpparam.appInfo?.sourceDir)
                lpparam.appInfo?.splitSourceDirs?.let(::addAll)
            }.filterNotNull()

            for (sourcePath in sourcePaths) {
                val prefix = findFromZip(File(sourcePath))
                if (!prefix.isNullOrBlank()) return prefix
            }
            return null
        }

        private fun findFromZip(apk: File): String? {
            if (!apk.isFile) return null
            return runCatching {
                ZipFile(apk).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (!entry.name.endsWith(".dex")) continue
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        val text = bytes.toString(Charsets.ISO_8859_1)
                        extractPrefix(text)?.let { return@use it }
                    }
                    null
                }
            }.getOrNull()
        }

        private fun extractPrefix(text: String): String? {
            return PREFIX_REGEX.find(text)?.value
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.coloros.gallery3d"
        private const val TOKEN_DECRYPTOR_CLASS = "com.oplus.aiunit.vision.erq"
        private const val PREFIX_METHOD = "e"
        private const val KNOWN_PREFIX = "tRiM@2025#GwToken!sEcReT*kEy&vALu"
        private val PREFIX_REGEX = Regex("""[A-Za-z0-9@#_!*&$%+?.-]{8,80}GwToken[A-Za-z0-9@#_!*&$%+?.-]{4,80}""")

        private fun Any?.isNullOrBlankString(): Boolean {
            return (this as? String).isNullOrBlank()
        }

        private fun log(message: String) {
            XposedBridge.log("ColorOSFeiniuBridge: $message")
        }
    }
}
