package com.example.sistemadecomandas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import java.io.File

class Configuracion : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)

        findViewById<Button>(R.id.btnModificarProductos).setOnClickListener {
            startActivity(Intent(this, ModificarProductos::class.java))
        }

        findViewById<Button>(R.id.btnEmail).setOnClickListener {
            startActivity(Intent(this, Email::class.java))
        }

        findViewById<Button>(R.id.btnCerrarDia).setOnClickListener {
            startActivity(Intent(this, ResumenDiarioActivity::class.java))
        }



    }

}
