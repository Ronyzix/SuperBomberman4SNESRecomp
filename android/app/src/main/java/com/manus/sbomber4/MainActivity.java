package com.manus.sbomber4;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

public final class MainActivity extends SDLActivity {
    private static final String ROM_ASSET = "rom.sfc";
    private static final String ROM_SHA256 = "bdd90b49bf08d0d0a80e4b4ec811084d470355c648c49a28a0e838cbf1ea8f92";
    private static final long ROM_SIZE = 2097152L;
    private String romPath;
    private String startupError;

    static native void nativePadButton(int button, boolean pressed);

    @Override
    protected String[] getLibraries() {
        // O runtime C++ compartilhado deve preceder SDL3 e a biblioteca do jogo.
        return new String[] { "c++_shared", "SDL3", "main" };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            File appFiles = getExternalFilesDir(null);
            if (appFiles == null) {
                throw new IOException("O armazenamento específico do app não está disponível.");
            }
            if (!appFiles.isDirectory() && !appFiles.mkdirs()) {
                throw new IOException("Não foi possível preparar o armazenamento privado do jogo.");
            }

            File rom = new File(appFiles, ROM_ASSET);
            ensureVerifiedRom(rom);
            ensureDefaultConfig(new File(appFiles, "config.ini"));
            romPath = rom.getAbsolutePath();
        } catch (Exception error) {
            startupError = error.getMessage() == null ? error.toString() : error.getMessage();
        }

        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (startupError != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Não foi possível iniciar o jogo")
                    .setMessage(startupError)
                    .setPositiveButton("Fechar", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }

        if (mLayout != null) {
            TouchControls controls = new TouchControls(this);
            RelativeLayout.LayoutParams controlParams = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT);
            mLayout.addView(controls, controlParams);
            addAboutButton();
        }
        applyImmersiveMode();
    }

    @Override
    protected String[] getArguments() {
        if (startupError != null) {
            return new String[] { "--sb4-startup-error" };
        }
        if (romPath == null) {
            return new String[0];
        }
        return new String[] { "--no-launcher", romPath };
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private void ensureVerifiedRom(File destination) throws Exception {
        if (destination.isFile() && destination.length() == ROM_SIZE
                && ROM_SHA256.equals(sha256(destination))) {
            return;
        }

        File temporary = new File(destination.getParentFile(), ROM_ASSET + ".tmp");
        try (InputStream input = getAssets().open(ROM_ASSET);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
        }

        if (temporary.length() != ROM_SIZE || !ROM_SHA256.equals(sha256(temporary))) {
            temporary.delete();
            throw new IOException("A ROM empacotada não passou na verificação de tamanho e SHA-256.");
        }
        if (destination.exists() && !destination.delete()) {
            temporary.delete();
            throw new IOException("Não foi possível substituir a cópia privada da ROM inválida.");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IOException("Não foi possível instalar a ROM no armazenamento privado do app.");
        }
    }

    private void ensureDefaultConfig(File destination) throws IOException {
        if (destination.isFile()) {
            return;
        }
        File temporary = new File(destination.getParentFile(), "config.ini.tmp");
        try (InputStream input = getAssets().open("config.ini");
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            throw new IOException("Não foi possível gravar a configuração inicial do controle.");
        }
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = getAssetsOrFileInput(file)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    private InputStream getAssetsOrFileInput(File file) throws IOException {
        return new java.io.FileInputStream(file);
    }

    private void addAboutButton() {
        TextView about = new TextView(this);
        about.setText("i");
        about.setTextColor(Color.WHITE);
        about.setTextSize(18);
        about.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        about.setGravity(Gravity.CENTER);
        about.setContentDescription("Sobre o port e as licenças");
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.argb(190, 18, 26, 38));
        background.setStroke(dp(1), Color.argb(200, 235, 240, 245));
        about.setBackground(background);
        about.setOnClickListener(view -> showAbout());

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(dp(42), dp(42));
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_START);
        params.setMargins(dp(12), dp(12), 0, 0);
        mLayout.addView(about, params);
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("Super Bomberman 4 — Android nativo")
                .setMessage("Progresso AOT no escopo atual: 82,2% das instâncias de instrução elegíveis a AOT (405/493 em cinco raízes analisadas); 21/23 variantes (91,3%). Estes números não representam a porcentagem do jogo/ROM concluída.\n\nApp Android SDL3/NDK: alguns trechos 65C816 são recompilados em C; o código não mapeado usa o fallback interpretado do runtime SNESRecomp. A paridade completa de gameplay ainda não foi validada.\n\nA ROM fornecida pelo usuário e os saves ficam no armazenamento específico do app. O manifesto não solicita permissão de rede.\n\nControles: direcional, A/B/X/Y, L/R, Select e Start. Gamepads compatíveis também podem ser usados. Licenças e avisos estão incluídos neste APK.")
                .setNeutralButton("Licenças", (dialog, which) -> showLicenseIndex())
                .setPositiveButton("Voltar ao jogo", null)
                .show();
    }

    private void showLicenseIndex() {
        String[] labels = {
                "Super Bomberman 4 — aviso de procedência da ROM",
                "SNESRecomp — PolyForm Noncommercial",
                "SNESRecomp — atribuições de terceiros",
                "SDL3 — licença zlib",
                "GNU GPL v3.0 — texto (core de referência não incluído)",
                "Escopo GPL — aviso de dependências não incluídas",
                "Android NDK — NOTICE",
                "Android NDK toolchain — NOTICE"
        };
        new AlertDialog.Builder(this)
                .setTitle("Licenças e avisos incluídos")
                .setItems(labels, (dialog, which) -> showLicenseDocument(which, labels[which]))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showLicenseDocument(int index, String title) {
        String[] assets = {
                "licenses/SuperBomberman4-ROM-NOTICE.txt",
                "licenses/SNESRecomp-LICENSE.txt",
                "licenses/SNESRecomp-THIRD_PARTY_ATTRIBUTION.md",
                "licenses/SDL3-LICENSE.txt",
                "licenses/GPL-3.0.txt",
                "licenses/GPL-3.0-APPLICABILITY.txt",
                "licenses/ANDROID_NDK_NOTICE.txt",
                "licenses/ANDROID_NDK_TOOLCHAIN_NOTICE.txt"
        };
        try (java.io.InputStreamReader reader = new java.io.InputStreamReader(
                getAssets().open(assets[index]), java.nio.charset.StandardCharsets.UTF_8)) {
            StringBuilder contents = new StringBuilder();
            char[] buffer = new char[8192];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                contents.append(buffer, 0, count);
            }

            TextView document = new TextView(this);
            document.setText(contents.toString());
            document.setTextSize(12);
            document.setTextIsSelectable(true);
            document.setPadding(dp(16), dp(12), dp(16), dp(12));
            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            scroll.addView(document, new android.widget.ScrollView.LayoutParams(
                    android.widget.ScrollView.LayoutParams.MATCH_PARENT,
                    android.widget.ScrollView.LayoutParams.WRAP_CONTENT));
            new AlertDialog.Builder(this)
                    .setTitle(title)
                    .setView(scroll)
                    .setPositiveButton("Fechar", null)
                    .show();
        } catch (IOException error) {
            new AlertDialog.Builder(this)
                    .setTitle("Aviso de licença indisponível")
                    .setMessage(error.getMessage())
                    .setPositiveButton("Fechar", null)
                    .show();
        }
    }

    private void applyImmersiveMode() {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
