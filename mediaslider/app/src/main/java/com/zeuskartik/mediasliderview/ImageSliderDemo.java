package com.zeuskartik.mediasliderview;

import android.os.Bundle;

import com.zeuskartik.mediaslider.MediaSliderActivity;
import com.zeuskartik.mediaslider.SliderItem;

import java.util.ArrayList;

public class ImageSliderDemo extends MediaSliderActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ArrayList<SliderItem> list = new ArrayList<>();
        list.add(new SliderItem("https://res.cloudinary.com/kartiksaraf/image/upload/v1564514468/github_MediaSliderView/demo_images/8-phone-wallpaper_gcseap.jpg", "image", "test1"));
        list.add(new SliderItem("https://res.cloudinary.com/kartiksaraf/video/upload/v1564516308/github_MediaSliderView/demo_videos/video2_sn3sek.mp4", "video", "test2"));
        list.add(new SliderItem("https://res.cloudinary.com/kartiksaraf/image/upload/v1564514549/github_MediaSliderView/demo_images/ea0ef44d800aa07722c25b1a6db58800--iphone-backgrounds-phone-wallpapers_cqmbbx.jpg", "image", "test"));
        list.add(new SliderItem("https://res.cloudinary.com/kartiksaraf/image/upload/v1564514590/github_MediaSliderView/demo_images/Quotefancy-20588-3840x2160_msurjx.jpg", "image", "test"));
        list.add(new SliderItem("https://res.cloudinary.com/kartiksaraf/image/upload/v1564514634/github_MediaSliderView/demo_images/Quotefancy-2098-3840x2160_nrez6k.jpg", "image", "test"));
        list.add(new SliderItem("https://res.cloudinary.com/kartiksaraf/image/upload/v1564514699/github_MediaSliderView/demo_images/download_totbb2.jpg", "image", "test"));
        list.add(new SliderItem("https://res.cloudinary.com/kartiksaraf/video/upload/v1564516308/github_MediaSliderView/demo_videos/video1_jetay3.mp4", "video", "test"));
        loadMediaSliderView(list,true,true,false,"Image-Slider","#000000",null,0);
    }
}
