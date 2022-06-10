package com.faytian.hotfixdemo.fix;


import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import dalvik.system.DexFile;

/**
 * https://mp.weixin.qq.com/s?__biz=MzAwNDY1ODY2OQ==&mid=2649286341&idx=1&sn=054d595af6e824cbe4edd79427fc2706&scene=0#wechat_redirect
 *
 * 为什么要替换ClassLoader？
 * ART 是在 Android KitKat(Android 4.0)引入并在 Lollipop(Android 5.0)中设为默认运行环境,可以看作Dalvik2.0。
 * ART模式在Android N(7.0)之前安装APK时会采用AOT(Ahead of time:提前编译、静态编译)预编译为机器码。
 *
 * 而在Android 7.0使用混合模式的运行时。应用在安装时不做编译,而是运行时解释字节码，
 * 同时在JIT编译了一些代码后将这些代码信息记录至Profile文件，
 * 等到设备空闲的时候使用AOT(All-Of-the-Time compilation:全时段编译)编译生成称为app_image的base.art(类对象映像)文件，
 * 这个art文件会在apk启动时自动加载(相当于缓存)。根据类加载原理,类被加载了无法被替换,即无法修复。
 *
 * 无论是使用插入 pathlist 还是parent classloader的方式，若补丁修改的class已经存在与app image，它们都是无法通过热补丁更新的。
 * 它们在启动app时已经加入到PathClassloader的ClassTable中，系统在查找类时会直接使用base.art中的 class。
 */
public class ClassLoaderInjector {

    public static void inject(Application app, ClassLoader oldClassLoader, List<File> patchs) throws Throwable {
        //创建我们自己的加载器
        ClassLoader newClassLoader = createNewClassLoader(app, oldClassLoader, patchs);
        doInject(app, newClassLoader);
    }

    private static ClassLoader createNewClassLoader(Context context, ClassLoader oldClassLoader, List<File> patchs) throws Throwable {
        /**
         * 1、先把补丁包的dex拼起来
         */
        // 获得原始的dexPath用于构造classloader
        StringBuilder dexPathBuilder = new StringBuilder();
        String packageName = context.getPackageName();
        boolean isFirstItem = true;
        for (File patch : patchs) {
            //添加:分隔符  /xx/a.dex:/xx/b.dex
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                dexPathBuilder.append(File.pathSeparator);
            }
            dexPathBuilder.append(patch.getAbsolutePath());
        }

        /**
         * 2、把apk中的dex拼起来
         */
        //得到原本的pathList
        Field pathListField = ReflectUtil.findField(oldClassLoader, "pathList");
        Object oldPathList = pathListField.get(oldClassLoader);

        //dexElements
        Field dexElementsField = ReflectUtil.findField(oldPathList, "dexElements");
        Object[] oldDexElements = (Object[]) dexElementsField.get(oldPathList);

        //从Element上得到 dexFile
        Field dexFileField = ReflectUtil.findField(oldDexElements[0], "dexFile");
        for (Object oldDexElement : oldDexElements) {
            String dexPath = null;
            DexFile dexFile = (DexFile) dexFileField.get(oldDexElement);
            if (dexFile != null) {
                dexPath = dexFile.getName();
            }
            if (dexPath == null || dexPath.isEmpty()) {
                continue;
            }
            if (!dexPath.contains("/" + packageName)) {
                continue;
            }
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                dexPathBuilder.append(File.pathSeparator);
            }
            dexPathBuilder.append(dexPath);
        }
        String combinedDexPath = dexPathBuilder.toString();

        /**
         * 3、获取apk中的so加载路径
         */
        //  app的native库（so） 文件目录 用于构造classloader
        Field nativeLibraryDirectoriesField = ReflectUtil.findField(oldPathList, "nativeLibraryDirectories");
        List<File> oldNativeLibraryDirectories = (List<File>) nativeLibraryDirectoriesField.get(oldPathList);
        StringBuilder libraryPathBuilder = new StringBuilder();
        isFirstItem = true;
        for (File libDir : oldNativeLibraryDirectories) {
            if (libDir == null) {
                continue;
            }
            if (isFirstItem) {
                isFirstItem = false;
            } else {
                libraryPathBuilder.append(File.pathSeparator);
            }
            libraryPathBuilder.append(libDir.getAbsolutePath());
        }

        String combinedLibraryPath = libraryPathBuilder.toString();

        //创建自己的类加载器
        ClassLoader result = new HotFixClassLoader(combinedDexPath, combinedLibraryPath, ClassLoader.getSystemClassLoader());
        return result;
    }


    private static void doInject(Application app, ClassLoader classLoader) throws Throwable {
        Thread.currentThread().setContextClassLoader(classLoader);

        Context baseContext = (Context) ReflectUtil.findField(app, "mBase").get(app);
        if (Build.VERSION.SDK_INT >= 26) {
            ReflectUtil.findField(baseContext, "mClassLoader").set(baseContext, classLoader);
        }

        Object basePackageInfo = ReflectUtil.findField(baseContext, "mPackageInfo").get(baseContext);
        ReflectUtil.findField(basePackageInfo, "mClassLoader").set(basePackageInfo, classLoader);

        if (Build.VERSION.SDK_INT < 27) {
            Resources res = app.getResources();
            try {
                ReflectUtil.findField(res, "mClassLoader").set(res, classLoader);

                final Object drawableInflater = ReflectUtil.findField(res, "mDrawableInflater").get(res);
                if (drawableInflater != null) {
                    ReflectUtil.findField(drawableInflater, "mClassLoader").set(drawableInflater, classLoader);
                }
            } catch (Throwable ignored) {
                // Ignored.
            }
        }
    }
}
