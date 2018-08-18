package co.samco.mend4droid.mend4;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.IBase64EncodingProvider;
import co.samco.mend4.core.Settings;
import co.samco.mend4.core.crypto.CryptoProvider;
import co.samco.mend4.core.crypto.impl.DefaultJCECryptoProvider;
import co.samco.mend4.core.exception.CorruptSettingsException;
import co.samco.mend4.core.util.LogUtils;

public class MainActivity extends AppCompatActivity implements TextWatcher {
    private final int PERMISSIONS_REQUEST_READ_STORAGE = 1;
    private final int PERMISSIONS_REQUEST_WRITE_STORAGE = 2;

    private AutoCompleteTextView autoCompleteTextView;
    private EditText editText;
    private boolean initialized = false;

    private String mendDir;
    private String currentLog;
    private Settings settings;
    private CryptoProvider cryptoProvider;
    private AndroidEncodingProvider encodingProvider;
    private String version;

    private boolean checkSettingsPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_STORAGE);

            return false;
        }
        return true;
    }

    private boolean checkWritePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_STORAGE);

            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!initialized)
                        tryInitializeMEND();
                }
                return;
            }
            case PERMISSIONS_REQUEST_WRITE_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //just double check the mend dir is there.. although it should be because it needs
                    //a settings file to work anyway.
                    (new File(mendDir)).mkdirs();
                }
                return;
            }
        }
    }

    private void tryInitializeMEND() {
        if (!initialized && checkSettingsPermissions()) {
            try {
                initializeSettings();
                getVersion();
                indexLogFiles();
                initializeEncodingProvider();
                initializeCryptoProvider();
                initialized = true;
            } catch (Exception e) {
                Log.wtf("MainActivity", e.getMessage());
                Toast toast = Toast.makeText(this.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG);
                toast.show();
            }
        }
    }

    private void getVersion() throws PackageManager.NameNotFoundException {
        PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
        version = pInfo.versionName;
        this.setTitle("MEND" + getClass().getPackage().getImplementationVersion());
    }

    private void initializeSettings() throws IOException, CorruptSettingsException {
        mendDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/MEND4/";
        settings = new AndroidSettings(mendDir);
        currentLog = settings.getValue(Settings.Name.CURRENTLOG);
        autoCompleteTextView.setText(currentLog);
    }

    private void initializeEncodingProvider() {
        encodingProvider = new AndroidEncodingProvider();
    }

    private void initializeCryptoProvider() throws IOException, CorruptSettingsException {
        int aesKeySize = AppProperties.PREFERRED_AES_KEY_SIZE;
        int rsaKeySize = AppProperties.PREFERRED_RSA_KEY_SIZE;
        String preferredAES = AppProperties.PREFERRED_AES_ALG;
        String preferredRSA = AppProperties.PREFERRED_RSA_ALG;
        if (settings.valueSet(Settings.Name.AESKEYSIZE)) {
            aesKeySize = Integer.parseInt(settings.getValue(Settings.Name.AESKEYSIZE));
        }
        if (settings.valueSet(Settings.Name.RSAKEYSIZE)) {
            rsaKeySize = Integer.parseInt(settings.getValue(Settings.Name.RSAKEYSIZE));
        }
        if (settings.valueSet(Settings.Name.PREFERREDAES)) {
            preferredAES = settings.getValue(Settings.Name.PREFERREDAES);
        }
        if (settings.valueSet(Settings.Name.PREFERREDRSA)) {
            preferredRSA = settings.getValue(Settings.Name.PREFERREDRSA);
        }
        cryptoProvider = new DefaultJCECryptoProvider(AppProperties.STANDARD_IV, aesKeySize, rsaKeySize,
                preferredAES, preferredRSA, AppProperties.PASSCHECK_SALT, AppProperties.AES_KEY_GEN_ITERATIONS,
                encodingProvider);
    }

    private void indexLogFiles() {
        List<String> names = new ArrayList<>();
        for (File f : new File(mendDir).listFiles()) {
            String name = f.getName();
            if (name.endsWith("." + AppProperties.LOG_FILE_EXTENSION)) {
                names.add(name.substring(0, name.length()-5));
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                names);
        autoCompleteTextView.setAdapter(adapter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        autoCompleteTextView = findViewById(R.id.autoCompleteTextView);
        autoCompleteTextView.addTextChangedListener(this);
        editText = findViewById(R.id.entryText);
        tryInitializeMEND();
    }

    //File button callback
    public void onFileButton(View view) {
        if (!doubleCheckPermissions(view))
            return;

        final Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        //to get image and videos, I used a */"
        fileIntent.setType("*/*");
        fileIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        fileIntent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(fileIntent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Encrypt selected file
        if (requestCode == 1 && resultCode == RESULT_OK) {
            ContentResolver cR = getContentResolver();
            Uri uri = data.getData();
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            String fileExtension = mime.getExtensionFromMimeType(cR.getType(uri));
            String name = new SimpleDateFormat(AppProperties.ENC_FILE_NAME_FORMAT).format(new Date());
            File file = new File(mendDir + File.separator + "Enc", name + "." + AppProperties.ENC_FILE_EXTENSION);
            file.getParentFile().mkdirs();
            try (FileInputStream fis = (FileInputStream) cR.openInputStream(uri);
                FileOutputStream fos = new FileOutputStream(file, false)) {
                file.getParentFile().mkdirs();
                cryptoProvider.encryptEncStream(getPublicKey(), fis, fos, fileExtension);
                Toast toast = Toast.makeText(this.getApplicationContext(), "File encryption complete", Toast
                        .LENGTH_SHORT);
                toast.show();
                editText.append(name);
            } catch (Exception e) {
                    Log.wtf("MainActivity", e.getMessage());
                    Toast toast = Toast.makeText(this.getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG);
                    toast.show();
            }
        }
    }

    private RSAPublicKey getPublicKey() throws IOException, CorruptSettingsException, InvalidKeySpecException, NoSuchAlgorithmException {
        String pubKeyString = settings.getValue(Settings.Name.PUBLICKEY);
        byte[] publicKeyBytes = encodingProvider.decodeBase64(pubKeyString);
        return cryptoProvider.getPublicKeyFromBytes(publicKeyBytes);
    }

    private boolean doubleCheckPermissions(View view) {
        if (!initialized) {
            Toast toast = Toast.makeText(view.getContext(), "Could not read from storage.", Toast.LENGTH_LONG);
            toast.show();
            tryInitializeMEND();
            return false;
        }
        if (!checkWritePermissions()) {
            Toast toast = Toast.makeText(view.getContext(), "Could not write to storage.", Toast.LENGTH_LONG);
            toast.show();
            return false;
        }
        return true;
    }

    //Submit button callback
    public void onSubmitButton(View view) {
        if (!doubleCheckPermissions(view))
            return;

        String logText = editText.getText().toString();
        if (logText == null || logText.length() == 0) {
            Toast toast = Toast.makeText(view.getContext(), "Nothing to log.", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        File file = new File(mendDir, currentLog + "." + AppProperties.LOG_FILE_EXTENSION);
        try (FileOutputStream fos = new FileOutputStream(file, true)){
            settings.setValue(Settings.Name.CURRENTLOG, currentLog);
            file.createNewFile();
            logText = LogUtils.addHeaderToLogText(logText, "DROID", version, "\n");
            cryptoProvider.encryptLogStream(getPublicKey(), logText, fos);
            editText.getText().clear();
            indexLogFiles();
        } catch (Exception e) {
            Log.wtf("MainActivity", e.getMessage());
            Toast toast = Toast.makeText(view.getContext(), e.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
    }


    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        currentLog = autoCompleteTextView.getText().toString();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

    @Override
    public void afterTextChanged(Editable s) { }

    private class AndroidEncodingProvider implements IBase64EncodingProvider {
        public byte[] decodeBase64(String s) {
            return Base64.decode(s, Base64.URL_SAFE);
        }

        public String encodeBase64URLSafeString(byte[] bytes) {
            return Base64.encodeToString(bytes, Base64.URL_SAFE);
        }
    }
}
