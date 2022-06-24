package com.faytian.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.api.ApplicationVariant;
import com.android.utils.FileUtils;
import com.faytian.bean.HotFixExtBean;
import com.faytian.utils.ClassUtil;
import com.faytian.utils.PatchUtil;

import org.apache.commons.compress.utils.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.PluginContainer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;


public class PatchPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        //获取所有引入的插件
        PluginContainer plugins = project.getPlugins();
        //引入我们这个插件的前提是，引入application插件，所以需要判断下
        if (!plugins.hasPlugin(AppPlugin.class)) {
            throw new GradleException("必须结合Android Application插件使用该插件");
        }

        // 创建扩展, gradle配置 hotfix插件属性，会保存到HotFixExtBean对象中
        project.getExtensions().create("hotfix", HotFixExtBean.class);
//        HotFixExtBean hotfix = project.getExtensions().create("hotfix", HotFixExtBean.class);
//        boolean debugOn = hotfix.isDebugOn();//此时获取不到值，因为apply方法是在引入插件时执行，此时还没有执行到插件拓展（hotfix）里
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                //build.gradle解析完成后，回调监听
                final HotFixExtBean hotfix = project.getExtensions().getByType(HotFixExtBean.class);
                //获取gradle android标签
                AppExtension android = project.getExtensions().getByType(AppExtension.class);
                android.getApplicationVariants().all(new Action<ApplicationVariant>() {//获取所有变体，getApplicationVariants就是包含了debug和release的集合，all表示对集合进行遍历
                    @Override
                    public void execute(ApplicationVariant applicationVariant) {
                        //当前用户是debug模式，并且没有配置debug运行执行热修复
                        if (applicationVariant.getName().contains("debug") && !hotfix.isDebugOn()) {
                            return;
                        }
                        //配置热修复插件生成补丁的一系列任务
                        configTasks(project, applicationVariant, hotfix);
                    }
                });
            }
        });
    }


    private void configTasks(final Project project, ApplicationVariant applicationVariant, HotFixExtBean hotfix) {
        //获得: debug/release
        String variantName = applicationVariant.getName();
        //首字母大写
        String capitalizeName = PatchUtil.capitalize(variantName);
        //热修复的输出目录
        File outputDir;
        String output = hotfix.getOutput();
        if (!PatchUtil.isEmpty(output)) {
            outputDir = new File(output, variantName);
        } else {
            //没有设置输出路径，默认路径设置为build路劲
            outputDir = new File(project.getBuildDir(), "patch/" + variantName);
        }
        outputDir.mkdirs();

        //获得混淆task
        Task proguardTask = project.getTasks().findByName("transformClassesAndResourcesWithProguardFor" + capitalizeName);
        File mappingFile = new File(outputDir, "mapping.txt");
        if (proguardTask != null) {
            proguardTask.doLast(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    FileCollection files = proguardTask.getOutputs().getFiles();
                    for (File file : files) {
                        if (file.getName().endsWith("mapping.txt")) {
                            try {
                                FileUtils.copyFile(file, mappingFile);
                                project.getLogger().error("mapping: " + mappingFile.getCanonicalPath());//输出log
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            break;
                        }
                    }
                }
            });
        }
        //将上次混淆的mapping应用到本次,如果没有上次的混淆文件就没操作
        PatchUtil.applyMapping(proguardTask, mappingFile);

        //缓存本次编译的class：md5值，用于下次编译时比较
        File md5File = new File(outputDir, "hex_md5.txt");
        //补丁包的jar，发生改变的类添加到这里
        File patchClassFile = new File(outputDir, "patchClass.jar");
        //用dx命令 把patchClass.jar 打包成patch.dex
        File patchDexFile = new File(outputDir, "patch.dex");
        //本次编译的map, key：className   value：md5
        Map<String, String> md5Map = new HashMap<>();

        //todo 获取打包dex任务
        final Task dexTask = project.getTasks().findByName("transformClassesWithDexBuilderFor" + capitalizeName);
        dexTask.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {
                PatchGenerator patchGenerator = new PatchGenerator(project, md5File, patchClassFile, patchDexFile);
                String applicationName = hotfix.getApplicationName();
                //windows下 目录输出是  xx\xx\  ,linux下是  /xx/xx ,把 . 替换成平台相关的斜杠
                applicationName = applicationName.replaceAll("\\.", Matcher.quoteReplacement(File.separator));
                //获取目录名
                String dirName = applicationVariant.getDirName();
                //获取task的输入文件，所有要打成dex的class和jar包
                Set<File> files = dexTask.getInputs().getFiles().getFiles();
                for (File file : files) {
                    String filePath = file.getAbsolutePath();
                    if (filePath.endsWith(".jar")) {
                        processJar(applicationName, file, md5Map, patchGenerator);
                    } else if (filePath.endsWith(".class")) {
                        processClass(applicationName, file, dirName, md5Map, patchGenerator);
                    }
                }
                //类的md5集合 写入到文件
                PatchUtil.writeHex(md5Map, md5File);
                try {
                    //生成补丁
                    patchGenerator.generate();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        //创建生成 补丁包的task任务
        Task task = project.getTasks().create("patch" + capitalizeName);
        task.setGroup("patch");
        task.dependsOn(dexTask);//该任务依赖dexTask
    }

    /**
     * 获取其他模块的类时，只有app模块能直接拿到class，其他模块是以jar传递过来的，需要从jar文件中获取class
     * @param applicationName
     * @param file              jar文件
     * @param md5Map
     * @param patchGenerator
     */
    private void processJar(String applicationName, File file, Map<String, String> md5Map, PatchGenerator patchGenerator) {
        try {
            applicationName = applicationName.replaceAll(Matcher.quoteReplacement(File.separator), "/");
            File bakJar = new File(file.getParent(), file.getName() + ".bak");
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(bakJar));
            //解压jar文件
            JarFile jarFile = new JarFile(file);
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();

                jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
                InputStream is = jarFile.getInputStream(jarEntry);//todo 从jar文件中获取
                String className = jarEntry.getName();
                if (className.endsWith(".class") && !className.contains(applicationName)//可以进行热修复的class
                        && !PatchUtil.isAndroidClass(className) && !className.startsWith("com/example/patchlib")) {
                    //进行插桩，插入单独dex的代码，解决 CLASS_ISPREVERIFIED标志导致的问题
                    byte[] byteCode = ClassUtil.referHackWhenInit(is);
                    //得到class的md5
                    String md5 = PatchUtil.hex(byteCode);
                    md5Map.put(className, md5);
                    patchGenerator.checkClass(className, md5, byteCode);
                    jarOutputStream.write(byteCode);
                } else {
                    //无法热修复的class
                    jarOutputStream.write(IOUtils.toByteArray(is));//输出到临时文件
                }
                is.close();
                jarOutputStream.closeEntry();
            }
            jarOutputStream.close();
            jarFile.close();
            file.delete();
            //todo 使用插桩后的class生成新的jar，替换其他模块传递过来的jar，因为CLASS_ISPREVERIFIED标志问题，如果不插桩，就不能热修复这个类。所以打包apk时，需要所有的类进行插桩，去除因为CLASS_ISPREVERIFIED标志
            bakJar.renameTo(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取app模块下的class，调用该方法
     * @param applicationName
     * @param file              class文件， HotFixDemo/app/build/intermediates/classes/debug/com/faytian/hotfixdemo/MainActivity.class
     * @param dirName
     * @param md5Map
     * @param patchGenerator
     */
    private void processClass(String applicationName, File file, String dirName, Map<String, String> md5Map, PatchGenerator patchGenerator) {
        //去除目录名
        String filePath = file.getAbsolutePath();
        String s = filePath.split(dirName)[1];
        String className = s.substring(1);
        //application 和 android包下的类无法进行热修复，所以这里屏蔽掉
        if (className.contains(applicationName) || PatchUtil.isAndroidClass(className)) {
            return;
        }
        try {
            //得到 class文件的输入流
            FileInputStream fis = new FileInputStream(file);
            //进行插桩，插入单独dex的代码，解决 CLASS_ISPREVERIFIED标志导致的问题
            byte[] byteCode = ClassUtil.referHackWhenInit(fis);
            //得到class的md5
            String md5 = PatchUtil.hex(byteCode);
            //todo 这里为什么要再把已插桩的class，在写入到file中，因为CLASS_ISPREVERIFIED标志问题，如果不插桩，就不能热修复这个类。所以打包apk时，需要所有的类进行插桩，去除因为CLASS_ISPREVERIFIED标志
            FileOutputStream fos = new FileOutputStream(filePath);
            fos.write(byteCode);
            fos.close();

            md5Map.put(className, md5);
            patchGenerator.checkClass(className, md5, byteCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
