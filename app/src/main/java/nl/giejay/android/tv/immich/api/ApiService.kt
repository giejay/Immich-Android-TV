package nl.giejay.android.tv.immich.api

import nl.giejay.android.tv.immich.api.model.Album
import nl.giejay.android.tv.immich.api.model.AlbumDetails
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query


interface ApiService {
    @GET("album")
    fun listAlbums(@Query("shared") shared: Boolean = false): Call<List<Album>>

    @GET("album/{albumId}")
    fun listAssetsFromAlbum(@Path("albumId") albumId: String): Call<AlbumDetails>
}