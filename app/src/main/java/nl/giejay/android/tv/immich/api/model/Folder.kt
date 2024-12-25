package nl.giejay.android.tv.immich.api.model

import java.io.Serializable

data class Folder(val path: String, val children: MutableList<Folder>, val parent: Folder?) : Serializable {
    fun hasPath(dir: String): Folder? {
        return children.find { it.path == dir }
    }

    fun getFullPath(): String {
        return getInnerFullPath(this)
    }

    private fun getInnerFullPath(folder: Folder): String {
        if(folder.parent != null){
            return getInnerFullPath(folder.parent) + "/" + folder.path
        }
        return folder.path
    }
}
