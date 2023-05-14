package com.the123saurav.common;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Logger {

    private static TimeZone tz = TimeZone.getDefault();
    static DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    static {
        df.setTimeZone(tz);
    }

    public static void log(String message) {
        System.err.println(df.format(new Date()) + " " + message);
//        System.err.flush();
    }
}
