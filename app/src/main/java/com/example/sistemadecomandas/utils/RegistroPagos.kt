package com.example.sistemadecomandas.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RegistroPagos {
    private val mapaPagos = mutableMapOf<String, MutableMap<String, Int>>()

    fun registrarPago(mesa: String, metodo: String, monto: Int) {
        val pagos = mapaPagos.getOrPut(mesa) { mutableMapOf() }
        pagos[metodo] = pagos.getOrDefault(metodo, 0) + monto
    }

    fun obtenerTotalPagado(mesa: String): Int {
        return mapaPagos[mesa]?.values?.sum() ?: 0
    }

    fun obtenerMapaCompleto(): Map<String, Map<String, Int>> {
        return mapaPagos
    }

    fun guardarEnPreferencias(context: Context) {
        val prefs = context.getSharedPreferences("registro_pagos", Context.MODE_PRIVATE)
        val json = Gson().toJson(mapaPagos)
        prefs.edit().putString("pagos", json).apply()
    }

    fun cargarDesdePreferencias(context: Context) {
        val prefs = context.getSharedPreferences("registro_pagos", Context.MODE_PRIVATE)
        val json = prefs.getString("pagos", null)
        if (!json.isNullOrEmpty()) {
            val tipo = object : TypeToken<MutableMap<String, MutableMap<String, Int>>>() {}.type
            val data: MutableMap<String, MutableMap<String, Int>> = Gson().fromJson(json, tipo)
            mapaPagos.clear()
            mapaPagos.putAll(data)
        }
    }

    fun eliminarEntrada(mesa: String) {
        mapaPagos.remove(mesa)
    }

    fun renombrarEntrada(vieja: String, nueva: String) {
        mapaPagos[nueva] = mapaPagos.remove(vieja) ?: mutableMapOf()
    }

    fun limpiar(context: Context ){
        mapaPagos.clear()
        context.getSharedPreferences("registro_pagos", Context.MODE_PRIVATE)
            .edit()
            .remove("pagos")
            .apply()
    }
}
