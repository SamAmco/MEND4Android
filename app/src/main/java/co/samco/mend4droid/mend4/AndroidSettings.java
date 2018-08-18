package co.samco.mend4droid.mend4;

import co.samco.mend4.core.AppProperties;
import co.samco.mend4.core.exception.CorruptSettingsException;

import java.io.*;
import java.util.Properties;

import co.samco.mend4.core.Settings;

/**
 * Created by sam on 18/06/17.
 */

public class AndroidSettings implements Settings {
    private final File settingsFile;
    private Properties _propertiesCache;
    private boolean propertiesNotFound = false;

    public AndroidSettings(String mendDir) {
        this.settingsFile = new File(mendDir + File.separator + AppProperties.SETTINGS_FILE_NAME);
    }

    private Properties getProperties() throws IOException {
        if (_propertiesCache == null) {
            _propertiesCache = new Properties();
            try (InputStream fis = new FileInputStream(settingsFile)) {
                _propertiesCache.loadFromXML(fis);
            } catch (FileNotFoundException e) {
                propertiesNotFound = true;
            }
        }
        return _propertiesCache;
    }

    private void saveProperties(Properties properties) throws IOException {
        _propertiesCache = properties;
        try (OutputStream fos = new FileOutputStream(settingsFile)) {
            properties.storeToXML(fos, "");
        } catch (IOException e) {
            throw e;
        }
    }

    @Override
    public void setValue(Name name, String value) throws IOException {
        Properties properties = getProperties();
        properties.setProperty(name.toString(), value);
        saveProperties(properties);
    }

    @Override
    public boolean valueSet(Name name) throws IOException {
        if (propertiesNotFound) {
            return false;
        }
        Properties properties = getProperties();
        String value = properties.getProperty(name.toString());
        return value != null;
    }

    @Override
    public String getValue(Name name) throws IOException, CorruptSettingsException {
        Properties properties = getProperties();
        String value = properties.getProperty(name.toString());
        if (value == null) {
            throw new CorruptSettingsException("get value %s returned null", name.toString());
        }
        return value;
    }
}

