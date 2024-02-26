package nl.giejay.android.tv.immich.shared.prefs

import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.schedulers.Schedulers

class MultiPreference constructor(private val updates: Observable<String>,
                                     private val preferences: SharedPreferences,
                                     private val keys: List<String>) : MutableLiveData<Map<String, Any?>>() {

    private var disposable: Disposable? = null
    private val values = mutableMapOf<String, Any?>()

    init {
        for (key in keys)
            values[key] = preferences.all[key]
    }

    override fun onActive() {
        super.onActive()
        value = values

        disposable = updates.filter { t -> keys.contains(t) }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread()).subscribeWith(object: DisposableObserver<String>() {
                override fun onComplete() {

                }

                override fun onNext(t: String) {
                    values[t] = preferences.all[t] as Any
                    postValue(values)
                }

                override fun onError(e: Throwable) {

                }
            })
    }

    override fun onInactive() {
        super.onInactive()
        disposable?.dispose()
    }
}