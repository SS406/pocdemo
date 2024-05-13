package com.pocdemo.jdbc.orm;

import java.sql.Connection;
import java.sql.DriverManager;

public class ORMHelper {
    public static Connection conn() throws Exception {
        return DriverManager.getConnection("jdbc:aa/bb", "aa", "dd");
    }

    public static void exec(String s) throws Exception {
        conn().createStatement().execute(s);
    }
}
