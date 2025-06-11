package com.example.sistemadecomandas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.sistemadecomandas.repository.ProductoRepository

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ProductoRepository.inicializar(applicationContext)

        findViewById<Button>(R.id.btn_iniciar).setOnClickListener {
            startActivity(Intent(this, PantallaPrincipal::class.java))
        }

        findViewById<Button>(R.id.btnDelivery).setOnClickListener {
            startActivity(Intent(this, DeliveryActivity::class.java))
        }

        findViewById<Button>(R.id.btnAccion).setOnClickListener {
            startActivity(Intent(this, Configuracion::class.java))
        }
    }
}
