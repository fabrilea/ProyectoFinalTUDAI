package com.example.sistemadecomandas.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object RegistroPagos {
    private val pagos: MutableMap<String, MutableMap<String, Int>> = mutableMapOf()

    fun registrarPago(mesa: String, metodo: String, monto: Int) {
        val metodos = pagos.getOrPut(mesa) { mutableMapOf() }
        metodos[metodo] = (metodos[metodo] ?: 0) + monto
    }

    fun obtenerTotalPagado(mesa: String): Int {
        return pagos[mesa]?.values?.sum() ?: 0
    }

    fun obtenerMapaCompleto(): Map<String, Map<String, Int>> {
        return pagos
    }

    fun renombrarEntrada(viejaClave: String, nuevaClave: String) {
        val actual = pagos[viejaClave]
        if (actual != null) {
            pagos.remove(viejaClave)
            pagos[nuevaClave] = actual
        }
    }

    fun guardarEnPreferencias(context: Context) {
        val prefs = context.getSharedPreferences("registro_pagos", Context.MODE_PRIVATE)
        val json = Gson().toJson(pagos)
        prefs.edit().putString("pagos_json", json).apply()
    }

    fun cargarDesdePreferencias(context: Context) {
        val prefs = context.getSharedPreferences("registro_pagos", Context.MODE_PRIVATE)
        val json = prefs.getString("pagos_json", null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<MutableMap<String, MutableMap<String, Int>>>() {}.type
            val cargado: MutableMap<String, MutableMap<String, Int>> = Gson().fromJson(json, type)
            pagos.clear()
            pagos.putAll(cargado)
        }
    }

    fun eliminarEntrada(nombreMesa: String) {
        pagos.remove(nombreMesa)
    }


    fun limpiar(context: Context) {
        pagos.clear()
        context.getSharedPreferences("registro_pagos", Context.MODE_PRIVATE)
            .edit().remove("pagos_json").apply()
    }
}
