package com.example.sistemadecomandas.model

data class Pedido(
    val nombre: String,
    val precio: Int,
    var cantidad: Int,
    val categoria: String
)
