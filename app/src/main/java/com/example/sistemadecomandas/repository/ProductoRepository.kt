package com.example.sistemadecomandas.repository

import android.content.Context
import com.example.sistemadecomandas.model.Producto
import com.example.sistemadecomandas.storage.ProductoStorageManager
import java.util.concurrent.atomic.AtomicInteger

object ProductoRepository {
    private val contadorId = AtomicInteger(0)
    private var storage: ProductoStorageManager? = null
    private val productos = mutableListOf<Producto>()
    private val categoriasBase = listOf("Comida", "Bebida", "Postre", "Vinos", "Sin categoría")


    fun inicializar(context: Context) {
        storage = ProductoStorageManager(context)
        productos.clear()
        productos.addAll(storage?.cargarLista() ?: emptyList())

        if (productos.isEmpty()) {
            // Si no hay productos, agregar ejemplos (o dejar vacío)
            agregarProducto("Ejemplo comida", "Comida", 0)
            agregarProducto("Ejemplo bebida", "Bebida", 0)
            agregarProducto("Ejemplo postre", "Postre", 0)
            agregarProducto("Ejemplo vino", "Vinos", 0)
            agregarProducto("Ejemplo general", "Sin categoría", 0)
        }
    }


    fun obtenerTodos(): List<Producto> = productos.toList()

    fun obtenerCategorias(): List<String> {
        return (productos.map { it.categoria } + categoriasBase)
            .distinct()
            .sorted()
    }

    fun obtenerPorCategoria(cat: String): List<Producto> =
        productos.filter { it.categoria.equals(cat, true) }

    fun buscarPorNombre(nombre: String): List<Producto> =
        productos.filter { it.nombre.contains(nombre, true) }

    fun agregarProducto(nombre: String, categoria: String, precio: Int) {
        val producto = Producto(contadorId.incrementAndGet(), nombre, categoria, precio)
        productos.add(producto)
        guardar()
    }

    fun eliminarProducto(id: Int) {
        productos.removeIf { it.id == id }
        guardar()
    }

    fun vaciarTodo() {
        productos.clear()
        contadorId.set(0)
        guardar()
    }

    private fun guardar() {
        storage?.guardarLista(productos)
    }

}
