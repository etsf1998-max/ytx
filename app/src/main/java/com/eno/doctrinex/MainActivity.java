package com.eno.doctrinex;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.*;
import android.view.View;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;

import com.topjohnwu.superuser.Shell;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private Spinner namespaceSpinner;
    private EditText keyInput, valueInput;
    private TextView commandPreview, statusText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final int SHIZUKU_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        namespaceSpinner = findViewById(R.id.namespaceSpinner);
        keyInput = findViewById(R.id.keyInput);
        valueInput = findViewById(R.id.valueInput);
        commandPreview = findViewById(R.id.commandPreview);
        statusText = findViewById(R.id.statusText);

        String[] namespaces = {"global", "secure", "system"};
        namespaceSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, namespaces));

        keyInput.setText("screen_brightness");
        valueInput.setText("150");

        View.OnClickListener updatePreview = v -> updatePreview();
        namespaceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                updatePreview();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        keyInput.setOnKeyListener((v, keyCode, event) -> { updatePreview(); return false; });
        valueInput.setOnKeyListener((v, keyCode, event) -> { updatePreview(); return false; });

        findViewById(R.id.copyButton).setOnClickListener(v -> copyCommand());
        findViewById(R.id.shizukuButton).setOnClickListener(v -> runWithShizuku());
        findViewById(R.id.rootButton).setOnClickListener(v -> runWithRoot());
        findViewById(R.id.resetButton).setOnClickListener(v -> {
            keyInput.setText("");
            valueInput.setText("");
            updatePreview();
            setStatus("Form reset.");
        });

        if (Shizuku.isPreV11()) {
            setStatus("Shizuku API compatibility mode detected.");
        } else {
            setStatus("Ready. Grant Shizuku permission or use root.");
        }
        updatePreview();
    }

    private String buildCommand() {
        String namespace = String.valueOf(namespaceSpinner.getSelectedItem());
        String key = keyInput.getText().toString().trim();
        String value = valueInput.getText().toString().trim();

        if (!key.matches("[a-zA-Z0-9_\-.]+")) return "";
        if (!value.matches("[a-zA-Z0-9_\-.]+")) return "";
        return "settings put " + namespace + " " + key + " " + value;
    }

    private void updatePreview() {
        String command = buildCommand();
        commandPreview.setText(command.isEmpty() ? "Invalid key or value." : command);
    }

    private void copyCommand() {
        String command = buildCommand();
        if (command.isEmpty()) {
            setStatus("Invalid key or value.");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Doctrinex command", command));
        setStatus("Command copied.");
    }

    private void runWithShizuku() {
        String command = buildCommand();
        if (command.isEmpty()) {
            setStatus("Invalid key or value.");
            return;
        }

        if (!Shizuku.pingBinder()) {
            setStatus("Shizuku service is unavailable. Start Shizuku first.");
            return;
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                setStatus("Shizuku permission is required.");
            }
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE);
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Process process = Shizuku.newProcess(
                        new String[]{"sh", "-c", command}, null, null);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
                int exitCode = process.waitFor();
                showResult("Shizuku exit code: " + exitCode + "\n" + output);
            } catch (Exception e) {
                showResult("Shizuku error: " + e.getMessage());
            }
        });
    }

    private void runWithRoot() {
        String command = buildCommand();
        if (command.isEmpty()) {
            setStatus("Invalid key or value.");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Shell.Result result = Shell.cmd(command).exec();
                showResult("Root result: " + result.getCode() + "\n" +
                        String.join("\n", result.getOut()) + "\n" +
                        String.join("\n", result.getErr()));
            } catch (Exception e) {
                showResult("Root error: " + e.getMessage());
            }
        });
    }

    private void showResult(String result) {
        mainHandler.post(() -> setStatus(result));
    }

    private void setStatus(String text) {
        statusText.setText("Status:\n" + text);
    }
}
