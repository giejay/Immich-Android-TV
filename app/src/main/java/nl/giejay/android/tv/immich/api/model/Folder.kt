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
        if (folder.parent != null) {
            val parentPath = getInnerFullPath(folder.parent)
            // If the parent is the root (empty path), and the current folder is also an empty path (from leading slash)
            if (parentPath.isEmpty() && folder.path.isEmpty()) {
                return "/"
            }
            // If parent path ends with slash, just append the path.
            if (parentPath.endsWith('/')) {
                return parentPath + folder.path
            }
            // Otherwise, add a slash.
            return "$parentPath/${folder.path}"
        }
        return folder.path
    }
}
