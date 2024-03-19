package eu.thesimplecloud.clientserverapi.lib.extension

import eu.thesimplecloud.jsonlib.JsonLib
import java.util.UUID

/**
 * @author Niklas Nieberler
 */

fun JsonLib.getUUID(property: String): UUID? {
    return this.getObject(property, UUID::class.java)
}