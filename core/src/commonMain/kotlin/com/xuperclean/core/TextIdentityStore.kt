package com.xuperclean.core

/**
 * `IdentityStore` sobre un almacén de texto (prefs de cada app).
 *
 * Formato JSON plano; tolera ausencias y datos viejos devolviendo `null`.
 */
public class TextIdentityStore(
    private val read: () -> String?,
    private val write: (String) -> Unit,
) : IdentityStore {

    override fun load(): PortalIdentity? {
        val texto = read()?.takeIf { it.isNotBlank() } ?: return null
        val campos = HashMap<String, String>()
        for (m in Regex("\"([a-zA-Z]+)\"\\s*:\\s*(\"([^\"]*)\"|(\\d+))").findAll(texto)) {
            val valor = if (m.groupValues[3].isNotEmpty()) m.groupValues[3] else m.groupValues[4]
            campos[m.groupValues[1]] = valor
        }
        val sn = campos["sn"].orEmpty()
        if (sn.isEmpty()) return null
        return PortalIdentity(
            sn = sn,
            macAddr = campos["macAddr"].orEmpty(),
            androidId = campos["androidId"].orEmpty(),
            snToken = campos["snToken"].orEmpty(),
            openNum = campos["openNum"]?.toIntOrNull() ?: 0,
            userId = campos["userId"].orEmpty(),
            userToken = campos["userToken"].orEmpty(),
        )
    }

    override fun save(identity: PortalIdentity) {
        write(
            buildString {
                append("{\"sn\":\"").append(identity.sn).append('"')
                append(",\"macAddr\":\"").append(identity.macAddr).append('"')
                append(",\"androidId\":\"").append(identity.androidId).append('"')
                append(",\"snToken\":\"").append(identity.snToken).append('"')
                append(",\"openNum\":").append(identity.openNum)
                append(",\"userId\":\"").append(identity.userId).append('"')
                append(",\"userToken\":\"").append(identity.userToken).append('"')
                append('}')
            },
        )
    }
}
