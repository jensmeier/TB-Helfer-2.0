package de.tbhelfer.server

import android.content.Context
import de.tbhelfer.ui.einstellungen.nutzer.NutzerProfil
import de.tbhelfer.ui.einstellungen.nutzer.NutzerSpeicher
import java.io.File

/**
 * Spiegelt das zentrale Firmenprofil in den bereits vorhandenen lokalen Nutzerprofil-Ordner.
 * Dadurch verwenden bestehende BTB-/PDF-Funktionen weiterhin dieselbe lokale Quelle und bleiben
 * vollständig offline-fähig. Die persönliche Einstellung maxFotosInPdf wird nicht überschrieben.
 */
object CompanyProfileSync {
    sealed interface Result {
        data object Success : Result
        data object Unsupported : Result
        data object SessionInvalid : Result
        data class Error(val message: String) : Result
    }

    fun synchronize(context: Context, token: String, api: TeamManagementApi = TeamManagementApi()): Result {
        val profile = when (val result = api.loadCompanyProfile(token)) {
            is TeamManagementApi.CompanyProfileResult.Success -> result.profile
            TeamManagementApi.CompanyProfileResult.Unsupported -> return Result.Unsupported
            TeamManagementApi.CompanyProfileResult.SessionInvalid -> return Result.SessionInvalid
            is TeamManagementApi.CompanyProfileResult.Error -> return Result.Error(result.message)
        }

        val current = NutzerSpeicher.laden(context)
        val logoFileName = when {
            profile.hasLogo -> {
                when (val logo = api.downloadCompanyLogo(token)) {
                    is TeamManagementApi.LogoResult.Success -> {
                        val extension = when (logo.file.mimeType?.substringBefore(';')?.lowercase()) {
                            "image/png" -> "png"
                            "image/webp" -> "webp"
                            else -> "jpg"
                        }
                        val directory = NutzerSpeicher.ordner(context)
                        directory.listFiles()
                            ?.filter { it.isFile && it.name.startsWith("firma_server_logo.") }
                            ?.forEach { runCatching { it.delete() } }
                        val target = File(directory, "firma_server_logo.$extension")
                        target.writeBytes(logo.file.bytes)
                        target.name
                    }
                    TeamManagementApi.LogoResult.NotConfigured -> clearServerLogoIfPresent(context, current?.logoDateiname)
                    TeamManagementApi.LogoResult.Unsupported -> current?.logoDateiname
                    TeamManagementApi.LogoResult.SessionInvalid -> return Result.SessionInvalid
                    is TeamManagementApi.LogoResult.Error -> return Result.Error(logo.message)
                }
            }
            else -> clearServerLogoIfPresent(context, current?.logoDateiname)
        }

        val postalCity = listOfNotNull(
            profile.postalCode?.trim()?.takeIf { it.isNotBlank() },
            profile.city?.trim()?.takeIf { it.isNotBlank() }
        ).joinToString(" ").takeIf { it.isNotBlank() }
        val secondAddress = listOfNotNull(
            profile.addressLine2?.trim()?.takeIf { it.isNotBlank() },
            postalCity
        ).joinToString(", ").takeIf { it.isNotBlank() }

        return runCatching {
            NutzerSpeicher.speichern(
                context,
                NutzerProfil(
                    firmenname = profile.name,
                    anschrift1 = profile.addressLine1,
                    anschrift2 = secondAddress,
                    telefon = profile.phone,
                    email = profile.email,
                    logoDateiname = logoFileName,
                    maxFotosInPdf = current?.maxFotosInPdf ?: 60
                )
            )
        }.fold(
            onSuccess = { Result.Success },
            onFailure = { Result.Error(it.message ?: "Firmenprofil konnte lokal nicht gespeichert werden.") }
        )
    }

    private fun clearServerLogoIfPresent(context: Context, currentName: String?): String? {
        if (currentName.isNullOrBlank() || !currentName.startsWith("firma_server_logo.")) {
            return currentName
        }
        runCatching { File(NutzerSpeicher.ordner(context), currentName).delete() }
        return null
    }
}
