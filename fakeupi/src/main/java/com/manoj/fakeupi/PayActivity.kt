package com.manoj.fakeupi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shows what Kaasu sent and replies like a real UPI app would. */
class PayActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val info = TextView(this).apply {
            textSize = 16f
            text = buildString {
                appendLine("FAKE UPI APP (test)")
                appendLine("link: $uri")
                appendLine("pa=${uri?.getQueryParameter("pa")}")
                appendLine("pn=${uri?.getQueryParameter("pn")}")
                appendLine("am=${uri?.getQueryParameter("am")}")
                appendLine("tn=${uri?.getQueryParameter("tn")}")
            }
        }
        val pay = Button(this).apply {
            text = "PAY SUCCESS"
            setOnClickListener { reply("txnId=FAKE${System.currentTimeMillis() % 100000}&responseCode=00&Status=SUCCESS&ApprovalRefNo=630599990000") }
        }
        val fail = Button(this).apply {
            text = "PAY FAIL"
            setOnClickListener { reply("txnId=&responseCode=ZD&Status=FAILURE") }
        }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                addView(info)
                addView(pay)
                addView(fail)
            },
        )
    }

    private fun reply(response: String) {
        setResult(RESULT_OK, Intent().putExtra("response", response))
        finish()
    }
}
