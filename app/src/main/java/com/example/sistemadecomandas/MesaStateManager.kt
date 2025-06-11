package com.example.sistemadecomandas

import android.content.Context
import android.content.SharedPreferences

class MesaStateManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("estado_mesas", Context.MODE_PRIVATE)

    fun guardarEstado(nombreMesa: String, ocupada: Boolean) {
        prefs.edit().putBoolean(nombreMesa, ocupada).apply()
    }

    fun obtenerEstado(nombreMesa: String): Boolean {
        return prefs.getBoolean(nombreMesa, false)
    }

    fun obtenerTodosLosEstados(): Map<String, Boolean> {
        val estados = mutableMapOf<String, Boolean>()
        for (i in 1..10) {
            val nombre = if (i == 10) "Delivery" else "Mesa $i"
            estados[nombre] = prefs.getBoolean(nombre, false)
        }
        return estados
    }
}
