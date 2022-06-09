package com.faytian.hotfixdemo;

import android.util.Log;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.PathClassLoader;

class HotFixUtils {


    static void installPatch() throws Exception {
        // 1、获取程序的PathClassLoader对象
        PathClassLoader classLoader = (PathClassLoader) AppApplication.appApplication.getClassLoader();

        // 2、反射获得PathClassLoader父类BaseDexClassLoader的pathList对象
        Class classLoaderClazz = Class.forName("dalvik.system.BaseDexClassLoader");
        Field pathListField = classLoaderClazz.getDeclaredField("pathList");
        pathListField.setAccessible(true);
        Object pathList = pathListField.get(classLoader);


        // 3、反射获取pathList的dexElements对象 （oldElement）
        Class pathListClass = Class.forName("dalvik.system.DexPathList");
        Field dexElementsField = pathListClass.getDeclaredField("dexElements");
        dexElementsField.setAccessible(true);
        Object[] oldDexElements = (Object[]) dexElementsField.get(pathList);

        // 4、把补丁包变成Element数组：patchElement
        String dirPath = AppApplication.appApplication.getCacheDir().getAbsolutePath();
        String apkPath = dirPath + "/patch.jar";

        //方式一: 创新新的类加载器, 传入路径 todo (dalvik虚拟机（5.0以下）如果 内部类 和 外部类不是同一个classloader， 会导致 dexopt 优化失败)
        //        PathClassLoader dexClassLoader = new PathClassLoader(apkPath, null, classLoader);
        //        Object pluginPathList = pathListField.get(dexClassLoader);
        //        Object[] patchDexElements = (Object[]) dexElementsField.get(pluginPathList);

        //方式二:反射执行 DexPathList.makePathElements 方法
        List<File> result = new ArrayList<>();
        result.add(new File(apkPath));
        Method method = pathListClass.getDeclaredMethod("makePathElements", List.class, File.class, List.class);
        method.setAccessible(true);
        Object[] patchDexElements = (Object[]) method.invoke(null, result, new File(dirPath), null);


        // 5、合并patchElement+oldElement = newElement （Array.newInstance）
        Object newDexElements = Array.newInstance(oldDexElements.getClass().getComponentType(), oldDexElements.length + patchDexElements.length);
        System.arraycopy(patchDexElements, 0, newDexElements, 0, patchDexElements.length);
        System.arraycopy(oldDexElements, 0, newDexElements, patchDexElements.length, oldDexElements.length);

        // 6、反射把oldElement赋值成newElemen t
        dexElementsField.set(pathList, newDexElements);

        Log.v("tyh", classLoader + "---");
    }
}
