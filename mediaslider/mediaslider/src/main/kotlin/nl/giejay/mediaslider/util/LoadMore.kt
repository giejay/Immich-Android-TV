package nl.giejay.mediaslider.util

import nl.giejay.mediaslider.model.SliderItemViewHolder

typealias LoadMore = suspend () -> List<SliderItemViewHolder>
