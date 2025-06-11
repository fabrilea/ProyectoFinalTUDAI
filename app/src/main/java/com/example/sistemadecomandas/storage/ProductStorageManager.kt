package com.example.sistemadecomandas.storage

import android.content.Context
import com.example.sistemadecomandas.model.Producto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ProductoStorageManager(context: Context) {

    private val prefs = context.getSharedPreferences("productos", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val key = "lista_productos"

    fun guardarLista(productos: List<Producto>) {
        val json = gson.toJson(productos)
        prefs.edit().putString(key, json).apply()
    }

    fun cargarLista(): List<Producto> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val tipo = object : TypeToken<List<Producto>>() {}.type
        return gson.fromJson(json, tipo)
    }
}
