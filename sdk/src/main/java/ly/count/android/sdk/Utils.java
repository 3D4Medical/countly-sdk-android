package ly.count.android.sdk;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static android.content.Context.UI_MODE_SERVICE;

public class Utils {
    private static final ExecutorService bg = Executors.newSingleThreadExecutor();

    public static Future<?> runInBackground(Runnable runnable) {
        return bg.submit(runnable);
    }

    public static <T> Future<T> runInBackground(Callable<T> runnable) {
        return bg.submit(runnable);
    }

    /**
     * Joins objects with a separator
     *
     * @param objects objects to join
     * @param separator separator to use
     * @return resulting string
     */
    static <T> String join(Collection<T> objects, String separator) {
        StringBuilder sb = new StringBuilder();
        Iterator<T> iter = objects.iterator();
        while (iter.hasNext()) {
            sb.append(iter.next());
            if (iter.hasNext()) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    /**
     * StringUtils.isEmpty replacement.
     *
     * @param str string to check
     * @return true if null or empty string, false otherwise
     */
    public static boolean isEmpty(String str) {
        return str == null || "".equals(str);
    }

    /**
     * StringUtils.isNotEmpty replacement.
     *
     * @param str string to check
     * @return false if null or empty string, true otherwise
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    /**
     * Returns true if the version you are checking is at or below the build version
     *
     * @param version
     * @return
     */
    public static boolean API(int version) {
        return Build.VERSION.SDK_INT >= version;
    }

    /**
     * Read stream into a byte array
     *
     * @param stream input to read
     * @return stream contents or {@code null} in case of error
     */
    public static byte[] readStream(InputStream stream) {
        if (stream == null) {
            return null;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[1024];
            int len = 0;
            while ((len = stream.read(buffer)) != -1) {
                bytes.write(buffer, 0, len);
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.e(Countly.TAG, "Couldn't read stream: " + e);
            }
            return null;
        } finally {
            try {
                bytes.close();
                stream.close();
            } catch (Throwable ignored) {
            }
        }
    }

    static String inputStreamToString(InputStream stream) {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));

        StringBuilder sbRes = new StringBuilder();

        while (true) {
            String streamLine;
            try {
                streamLine = br.readLine();
            } catch (IOException e) {
                if (Countly.sharedInstance().isLoggingEnabled()) {
                    e.printStackTrace();
                }
                break;
            }

            if (streamLine == null) {
                break;
            }

            if (sbRes.length() > 0) {
                //if it's not empty then there has been a previous line
                sbRes.append("\n");
            }

            sbRes.append(streamLine);
        }

        return sbRes.toString();
    }

    static Map<String, Object> removeKeysFromMap(Map<String, Object> data, String[] keys) {
        if (data == null || keys == null) {
            return data;
        }

        for (String key : keys) {
            data.remove(key);
        }

        return data;
    }

    static <T> boolean isUnsupportedDataType(T value) {
        if (value.getClass().isArray()) {
            return isUnsupportedType(value.getClass().getComponentType());
        }

        return isUnsupportedType(value.getClass());
    }

    /**
     * Removes unsupported data types
     *
     * @param data
     * @return returns true if any entry had been removed
     */
    static boolean removeUnsupportedDataTypes(Map<String, Object> data) {
        if (data == null) {
            return false;
        }

        boolean removed = false;

        for (Iterator<Map.Entry<String, Object>> it = data.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key == null || key.isEmpty()) {

            }

            if (key == null || key.isEmpty() || isUnsupportedDataType(value)) {
                //found unsupported data type or null key or value, removing
                it.remove();
                removed = true;
            }
        }

        if (removed) {
            if (Countly.sharedInstance().isLoggingEnabled()) {
                Log.w(Countly.TAG, "Unsupported data types were removed from provided segmentation");
            }
        }

        return removed;
    }

    /**
     * Serialize Map implementation with supported types
     * @param map Implementation of Map interface which holds allowed data types
     * @return serialization result as JSONObject
     */
    static JSONObject serialize(Map map) throws JSONException {
        JSONObject serializedMap = new JSONObject();
        for (Object key : map.keySet()) {
            Object value = map.get(key);
            if (value.getClass().isArray()) {
                serializedMap.put((String)key, serialize((Object[])value));
            } else if (value instanceof List) {
                serializedMap.put((String)key, serialize((List)value));
            } else {
                putActualType(serializedMap, (String)key, value);
            }
        }

        return serializedMap;
    }

    /**
     * Serialize List implementation with supported types
     * @param list Implementation of List interface which holds allowed data types
     * @return serialization result as JSONArray
     */
    static JSONArray serialize(List list) throws JSONException {
        if (list.isEmpty()) return new JSONArray();
        // Check type of list elements
        if (isUnsupportedType(list.get(0).getClass())) {
            throw new JSONException("List constructed from " + list.get(0).getClass().getName() +
                                        " is unsupported for proper serialization");
        }

        JSONArray serializedList = new JSONArray();
        for (Object element : list) {
            putActualType(serializedList, element);
        }

        return serializedList;
    }

    /**
     * Serialize array of supported types
     * @param array array of allowed data type
     * @return serialization result as JSONArray
     */
    static JSONArray serialize(Object[] array) throws JSONException {
        // Type of array should be already checked
        JSONArray serializedArray = new JSONArray();
        for (Object element : array) {
            putActualType(serializedArray, element);
        }

        return serializedArray;
    }

    /**
     * Wrapper around JSONObject.opt(String) which perform special handling of JSONArray type (decoded as ArrayList)
     * @param json JSONObject for opt(String) call
     * @param key key for accessing value
     * @return value for specified key
     * @throws JSONException
     */
    static Object optActualType(JSONObject json, String key) throws JSONException {
        Object value = json.opt(key);
        if (value instanceof JSONArray) {
            ArrayList actualValue = new ArrayList();
            for (int index = 0; index < ((JSONArray) value).length(); ++index) {
                actualValue.add(((JSONArray) value).get(index));
            }

            return actualValue;
        }

        // For now I think we don't need further decoding
        return value;
    }

    /**
     * Used for quickly sorting segments into their respective data type
     *
     * @param allSegm
     * @param segmStr
     * @param segmInt
     * @param segmDouble
     * @param segmBoolean
     */
    protected static synchronized void fillInSegmentation(Map<String, Object> allSegm, Map<String, Object> segmObj, Map<String, Integer> segmInt, Map<String, Double> segmDouble, Map<String, Boolean> segmBoolean,
        Map<String, Object> reminder) {
        for (Map.Entry<String, Object> pair : allSegm.entrySet()) {
            String key = pair.getKey();
            Object value = pair.getValue();

            if (value instanceof Integer) {
                segmInt.put(key, (Integer) value);
            } else if (value instanceof Double) {
                segmDouble.put(key, (Double) value);
            } else if (value instanceof Boolean) {
                segmBoolean.put(key, (Boolean) value);
            } else if (!isUnsupportedDataType(value)) {
                segmObj.put(key, value);
            } else {
                if (reminder != null) {
                    reminder.put(key, value);
                }
            }
        }
    }

    //https://stackoverflow.com/a/40310535

    /**
     * Used for detecting if current device is a tablet of phone
     */
    static boolean isDeviceTablet(Context context) {
        if (context == null) {
            return false;
        }

        return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;
    }

    /**
     * Used for detecting if device is a tv
     *
     * @return
     */
    @SuppressWarnings("RedundantIfStatement")
    static boolean isDeviceTv(Context context) {
        if (context == null) {
            return false;
        }

        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(UI_MODE_SERVICE);

        if (uiModeManager == null) {
            return false;
        }

        if (uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Used for checking if value type can't be serialized
     *
     * @return
     */
    protected static boolean isUnsupportedType(Class type) {
        if (!(Boolean.class.isAssignableFrom(type) ||
              Character.class.isAssignableFrom(type) ||
              Short.class.isAssignableFrom(type) ||
              Integer.class.isAssignableFrom(type) ||
              Long.class.isAssignableFrom(type) ||
              String.class.isAssignableFrom(type) ||
              List.class.isAssignableFrom(type))) {
            return true;
        }

        return false;
    }

    protected static void putActualType(JSONObject json, String key, Object value) throws JSONException {
        if (value instanceof Boolean) {
            json.put(key, (boolean) value);
        } else if (value instanceof Character) {
            json.put(key, Character.toString((char) value));
        } else if (value instanceof Short) {
            json.put(key, (int) value);
        } else if (value instanceof Integer) {
            json.put(key, (int) value);
        } else if (value instanceof Long) {
            json.put(key, (long) value);
        } else if (value instanceof Float || value instanceof Double) {
            json.put(key, (double) value);
        } else if (value instanceof String) {
            json.put(key, (String) value);
        } else {
            throw new JSONException("Unsupported for proper serialization type " + value.getClass().getName());
        }
    }

    protected static void putActualType(JSONArray json, Object value) throws JSONException {
        if (value instanceof Boolean) {
            json.put((boolean) value);
        } else if (value instanceof Character) {
            json.put(Character.toString((char) value));
        } else if (value instanceof Short || value instanceof Integer) {
            json.put((int) value);
        } else if (value instanceof Long) {
            json.put((long) value);
        } else if (value instanceof Float || value instanceof Double) {
            json.put((double) value);
        } else if (value instanceof String) {
            json.put((String) value);
        } else {
            throw new JSONException("Unsupported for proper serialization type " + value.getClass().getName());
        }
    }
}
