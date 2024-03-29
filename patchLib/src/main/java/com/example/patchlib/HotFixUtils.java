package com.example.patchlib;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class HotFixUtils {

    /**
     * 从assets中获取hack.dex，复制到缓存目录中
     *
     * @return
     */
    public static File initHack(Application application) {
        File hackFile = new File(application.getExternalFilesDir(""), "hack.dex");
        FileOutputStream fos = null;
        InputStream is = null;
        try {
            fos = new FileOutputStream(hackFile);

            is = application.getAssets().open("hack.dex");
            int len;
            byte[] buffer = new byte[2048];
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
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
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
        return hackFile;
    }


    public static void installPatch(Application application, File patchFile) throws Exception {
        List<File> patchList = new ArrayList<>();
        File hackFile = initHack(application);
        patchList.add(hackFile);
        if (patchFile.exists()) {
            patchList.add(patchFile);
        }
        // 1、获取程序的PathClassLoader对象
        ClassLoader classLoader = application.getClassLoader();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                //7.0及以上替换 PathClassLoader的同时，也会把补丁包里的类进行加载，所以无需后续操作
                ClassLoaderInjector.inject(application, classLoader, patchList);
            } catch (Throwable throwable) {
            }
            return;
        }

        // 2、反射获得PathClassLoader父类BaseDexClassLoader的pathList对象
//        Class classLoaderClazz = Class.forName("dalvik.system.BaseDexClassLoader");
//        Field pathListField = classLoaderClazz.getDeclaredField("pathList");
//        pathListField.setAccessible(true);
        //使用工具类，没有找到时，不断向上去父类寻找
        Field pathListField = ReflectUtil.findField(classLoader, "pathList");
        Object pathList = pathListField.get(classLoader);


        // 3、反射获取pathList的dexElements对象 （oldElement）
//        Class pathListClass = Class.forName("dalvik.system.DexPathList");
//        Field dexElementsField = pathListClass.getDeclaredField("dexElements");
//        dexElementsField.setAccessible(true);
        //使用工具类，没有找到时，不断向上去父类寻找
        Field dexElementsField = ReflectUtil.findField(pathList, "dexElements");
        Object[] oldDexElements = (Object[]) dexElementsField.get(pathList);

        // 4、把补丁包变成Element数组：patchElement
        //方式一: 创新新的类加载器, 传入路径 todo (dalvik虚拟机（5.0以下）如果 内部类 和 外部类不是同一个classloader， 会导致 dexopt 优化失败)
        //        PathClassLoader dexClassLoader = new PathClassLoader(apkPath, null, classLoader);
        //        Object pluginPathList = pathListField.get(dexClassLoader);
        //        Object[] patchDexElements = (Object[]) dexElementsField.get(pluginPathList);

        //方式二: 反射执行 DexPathList.makePathElements 方法
//        List<File> result = new ArrayList<>();
//        result.add(new File(apkPath));
//        Method method = pathListClass.getDeclaredMethod("makePathElements", List.class, File.class, List.class);
//        method.setAccessible(true);
        //使用工具类，没有找到时，不断向上去父类寻找
        Method method = ReflectUtil.findMethod(pathList, "makePathElements", List.class, File.class, List.class);
        Object[] patchDexElements = (Object[]) method.invoke(null, patchList, patchFile, null);


        // 5、合并patchElement+oldElement = newElement （Array.newInstance）
        Object newDexElements = Array.newInstance(oldDexElements.getClass().getComponentType(), oldDexElements.length + patchDexElements.length);
        System.arraycopy(patchDexElements, 0, newDexElements, 0, patchDexElements.length);
        System.arraycopy(oldDexElements, 0, newDexElements, patchDexElements.length, oldDexElements.length);

        // 6、反射把oldElement赋值成newElement
        dexElementsField.set(pathList, newDexElements);
    }
}
