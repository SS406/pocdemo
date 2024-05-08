package com.pocdemo.http.config;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JSONUtil {

    private static final Gson g = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateTypeAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
            .create();

    public static <T> T fromJson(InputStream is, Class<T> type) {
        try (Reader r = new InputStreamReader(is)) {
            return g.fromJson(r, type);
        } catch (Exception e) {
            throw asUnchecked(e);
        }
    }

    public static <T> T fromJson(Reader reader, Class<T> type) {
        return g.fromJson(reader, type);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return g.fromJson(json, type);
    }

    public static String toJson(Object obj) {
        return g.toJson(obj);
    }

    public static void writeTo(Object obj, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (var w = new FileWriter(path.toFile())) {
            g.toJson(obj, w);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T asUnchecked(Throwable throwable) throws T {
        return (T) throwable;
    }
}
