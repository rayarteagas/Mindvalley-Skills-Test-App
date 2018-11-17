package com.mukoapps.urlloadersample

import android.util.Log
import com.google.gson.JsonObject
import java.io.Serializable
import java.lang.Exception

data class Pin(var id:String ="",
               var createdAt:String = "",
               var color:String = "#000000",
               var width:Int = 0,
               var height:Int = 0,
               var likedByUser:Boolean =false,
               var userName:String = "",
               var url:String =""):Serializable
{
    companion object {
        fun fromJsonObject(obj: JsonObject):Pin
        {
            val pin = Pin()
            try {
                pin.id = obj["id"].asString
                pin.createdAt = obj["created_at"].asString
                pin.color = obj["color"].asString
                pin.width = obj["width"].asInt
                pin.height = obj["height"].asInt
                pin.likedByUser = obj["liked_by_user"].asBoolean
                pin.userName = obj["user"].asJsonObject["name"].asString
                pin.url = obj["urls"].asJsonObject["regular"].asString
            }
            catch (e:Exception)
            {
                //If error return a default pin
                Log.e("e","Json Object contains not valid pin data")
            }

            return pin
        }
    }
}