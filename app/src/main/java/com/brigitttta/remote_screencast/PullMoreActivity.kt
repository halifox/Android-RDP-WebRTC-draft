package com.brigitttta.remote_screencast

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class PullMoreActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pull_more)
        val pullFragment1 = PullFragment.newInstance("192.168.8.102", 8888)
        val pullFragment2 = PullFragment.newInstance("192.168.8.102", 8888)
        val pullFragment3 = PullFragment.newInstance("192.168.8.102", 8888)
        val pullFragment4 = PullFragment.newInstance("192.168.8.102", 8888)
        supportFragmentManager.beginTransaction()
                .add(R.id.container1, pullFragment1)
                .add(R.id.container2, pullFragment2)
                .add(R.id.container3, pullFragment3)
                .add(R.id.container4, pullFragment4)
                .commit()

    }
}