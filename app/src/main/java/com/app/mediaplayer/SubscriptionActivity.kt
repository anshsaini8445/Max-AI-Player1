package com.app.mediaplayer

import android.graphics.Color
import android.os.Bundle
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView

class SubscriptionActivity : AppCompatActivity() {

    private var isYearlySelected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        val btnClose = findViewById<ImageButton>(R.id.btnCloseVip)
        val cardYearly = findViewById<CardView>(R.id.cardYearly)
        val cardMonthly = findViewById<CardView>(R.id.cardMonthly)
        val layoutYearlyBorder = findViewById<RelativeLayout>(R.id.layoutYearlyBorder)
        val layoutMonthlyBorder = findViewById<RelativeLayout>(R.id.layoutMonthlyBorder)
        val btnContinue = findViewById<AppCompatButton>(R.id.btnContinueVip)

        btnClose.setOnClickListener { finish() }

        cardYearly.setOnClickListener {
            isYearlySelected = true
            layoutYearlyBorder.setBackgroundColor(Color.parseColor("#2E334D"))
            layoutMonthlyBorder.setBackgroundColor(Color.parseColor("#1A1C2B"))
            btnContinue.text = "Continue with Yearly (₹499)"
        }

        cardMonthly.setOnClickListener {
            isYearlySelected = false
            layoutMonthlyBorder.setBackgroundColor(Color.parseColor("#2E334D"))
            layoutYearlyBorder.setBackgroundColor(Color.parseColor("#1A1C2B"))
            btnContinue.text = "Continue with Monthly (₹99)"
        }

        btnContinue.setOnClickListener {
            val plan = if (isYearlySelected) "Yearly (₹499)" else "Monthly (₹99)"
            Toast.makeText(this, "Opening Payment for $plan (AdMob/Billing placeholder)", Toast.LENGTH_LONG).show()
        }
    }
}
