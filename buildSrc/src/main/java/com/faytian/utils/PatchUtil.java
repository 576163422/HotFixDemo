package com.faytian.utils;


import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.transforms.ProGuardTransform;

import org.apache.commons.codec.digest.DigestUtils;
import org.gradle.api.Task;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class PatchUtil {

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

    /**
     * 使用指定的mapping文件进行混淆
     * @param proguardTask
     * @param mappingFile
     */
    public static void applyMapping(Task proguardTask, File mappingFile) {
        //上一次混淆的mapping文件存在并且 也开启了混淆任务
        if (mappingFile.exists() && proguardTask != null) {
            //将上次混淆的mapping应用到本次
            TransformTask task = (TransformTask) proguardTask;
            ProGuardTransform transform = (ProGuardTransform) task.getTransform();
            transform.applyTestedMapping(mappingFile);
        }
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

    /**
     * 从文件中读取class：md5
     * @param hexFile
     * @return
     */
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

    /**
     * 将class：md5写入到文件中进行保存，用于下次编译比对代码是否有改动
     * @param md5Map
     * @param md5File
     */
    public static void writeHex(Map<String, String> md5Map, File md5File) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(md5File);
            for (String key : md5Map.keySet()) {
                String value = md5Map.get(key);
                String line = key + ":" + value + "\n";
                fos.write(line.getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean isAndroidClass(String filePath) {
        return filePath.startsWith("android") ||
                filePath.startsWith("androidx");
    }
}
