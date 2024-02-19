package nl.giejay.android.tv.immich.shared.guidedstep

import androidx.leanback.widget.GuidedAction

object GuidedStepUtil {
    fun addAction(actions: MutableList<GuidedAction>, id: Long, title: String, desc: String) {
        actions.add(
            GuidedAction.Builder()
                .id(id)
                .title(title)
                .description(desc)
                .build()
        )
    }

    fun addEditableAction(
        actions: MutableList<GuidedAction>,
        id: Long,
        title: String,
        desc: String?,
        inputType: Int
    ) {
        actions.add(
            GuidedAction.Builder()
                .id(id)
                .title(title)
                .descriptionEditable(true)
                .descriptionInputType(inputType)
                .description(desc)
                .build()
        )
    }

    fun addCheckedAction(
        actions: MutableList<GuidedAction>, id: Long, title: String, desc: String,
        checked: Boolean, checkSetId: Int = -1
    ) {
        val guidedAction: GuidedAction = GuidedAction.Builder()
            .title(title)
            .description(desc)
            .id(id)
            .checkSetId(checkSetId)
            .build()
        guidedAction.isChecked = checked
        actions.add(guidedAction)
    }
}