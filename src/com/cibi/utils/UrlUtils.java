package com.cibi.utils;

import android.util.Log;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author morswin
 */
public class UrlUtils {

    public static String getParams(String url, Map<String, String> paramMap) {
           List<NameValuePair> params = new LinkedList<NameValuePair>();
           for (Map.Entry<String, String> entry : paramMap.entrySet()) {
               params.add(new BasicNameValuePair(
                       entry.getKey(), entry.getValue()
               ));
           }
           if (!url.endsWith("?"))
               url += "?";

           String paramString = URLEncodedUtils.format(params, "utf-8");
           url += paramString;
           Log.i(UrlUtils.class.getName(), url);
           return url;
       }

}
