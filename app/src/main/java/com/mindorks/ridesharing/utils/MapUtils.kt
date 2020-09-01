package com.mindorks.ridesharing.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import com.mindorks.ridesharing.R

// we will be creating a car image and then we will be adding that as a marker and it will be shown on the map
object MapUtils {
    fun getCarBitMap(context: Context): Bitmap{
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ic_car)
        return Bitmap.createScaledBitmap(bitmap, 50, 100, false)
    }
}