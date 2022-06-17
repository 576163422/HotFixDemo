package com.faytian.plugin;

import com.android.build.gradle.AppExtension;
import com.android.build.gradle.AppPlugin;
import com.android.build.gradle.api.ApplicationVariant;
import com.faytian.bean.HotFixExtBean;
import com.faytian.utils.Util;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.PluginContainer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


public class PatchPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        //获取所有引入的插件
        PluginContainer plugins = project.getPlugins();
        //引入我们这个插件的前提是，引入application插件，所以需要判断下
        if (!plugins.hasPlugin(AppPlugin.class)) {
            throw new GradleException("必须结合Android Application插件使用该插件");
        }

        // 创建扩展
        project.getExtensions().create("hotfix", HotFixExtBean.class);
//        HotFixExtBean hotfix = project.getExtensions().create("hotfix", HotFixExtBean.class);
//        boolean debugOn = hotfix.isDebugOn();//此时获取不到值，因为apply方法是在引入插件时执行，此时还没有执行到插件拓展（hotfix）里
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(final Project project) {
                //build.gradle解析完成后，回调监听
                final HotFixExtBean hotfix = project.getExtensions().getByType(HotFixExtBean.class);
                System.out.println("> " + hotfix.getApplicationName());
                //
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
        String capitalizeName = Util.capitalize(variantName);
        //热修复的输出目录
        File outputDir;
        String output = hotfix.getOutput();
        if (!Util.isEmpty(output)) {
            outputDir = new File(output, variantName);
        } else {
            //没有设置输出路径，默认路径设置为build路劲
            outputDir = new File(project.getBuildDir(), "patch/" + variantName);
        }
        outputDir.mkdirs();

        File md5File = new File(outputDir, "hex_md5.txt");
        Map<String, String> oldMd5Map;
        Map<String, String> md5Map = new HashMap<>();

        if (md5File.exists()) {
            oldMd5Map = Util.readHex(md5File);
        }

        //todo 获取打包dex任务
        final Task dexTask = project.getTasks().findByName("transformClassesWithDexBuilderFor" + capitalizeName);
        dexTask.doFirst(new Action<Task>() {
            @Override
            public void execute(Task task) {
                //获取目录名
                String dirName = applicationVariant.getDirName();
                //获取task的输入文件，所有要打成dex的class和jar包
                Set<File> files = dexTask.getInputs().getFiles().getFiles();
                for (File file : files) {
                    String filePath = file.getAbsolutePath();
                    if (filePath.endsWith(".jar")) {
                        processJar();
                    } else if (filePath.endsWith(".class")) {
                        processClass(file, dirName, md5Map);
                    }
                }

                for (String s : md5Map.keySet()) {
                    System.out.println(s +" --》" + md5Map.get(s));
                }
            }
        });
    }


    private void processJar() {

    }

    private void processClass(File file, String dirName, Map<String, String> md5Map) {
        byte[] bytes = Util.readFile(file);
        //得到class的md5
        String md5 = Util.hex(bytes);

        //去除目录名
        String absolutePath = file.getAbsolutePath();
        String s = absolutePath.split(dirName)[1];
        String className = s.substring(1);
        md5Map.put(className, md5);

    }
}
