package com.faytian.utils;


import org.apache.commons.codec.digest.DigestUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Util {

    public static String capitalize(String self) {
        return self.length() == 0 ? "" :
                "" + Character.toUpperCase(self.charAt(0)) + self.subSequence(1, self.length());
    }

    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }


    /**
     * 得到md5
     * @param byteCode
     * @return
     */
    public static String hex(byte[] byteCode) {
        try {
            return DigestUtils.md5Hex(byteCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static byte[] readFile(File file) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            int len;
            byte[] buffer = new byte[4096];
            while ((len = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }  finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        byte[] bytes = bos.toByteArray();
        try {
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bytes;
    }


    public static Map<String, String> readHex(File hexFile) {
        Map<String, String> hashMap = new HashMap<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(hexFile)));
            String line;
            while ((line = br.readLine()) != null) {
                String[] list = line.split(":");
                if (list != null && list.length == 2) {
                    hashMap.put(list[0], list[1]);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return hashMap;
    }
}
