package nl.giejay.android.tv.immich.shared.db

import com.zeuskartik.mediaslider.SliderItemViewHolder

object LocalStorage {
    // todo find a better way to pass the data to the MediaSliderFragment/ImmichMediaSlider without exceeding parcel max size
    var mediaSliderItems: List<SliderItemViewHolder>? = null
}