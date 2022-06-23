package com.faytian.hotfixdemo;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String str = "askss";
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();

        Utils.readyException();
        Log.v("tyh", "有没有崩溃呀");
    }
}
